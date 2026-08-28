#!/usr/bin/env python3
#  Esquire frameworks (tm)
#  deploy config -- local-k8s <-> OKE parity check
#
#  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
#  mailto:mir0n.the.programmer@gmail.com
#
#  History:
# 07/22/2026 mir0n  created: catch config that was applied to the LOCAL k8s deploy but NOT carried to OKE (or
#                   left unset there). Renders each service's ConfigMap for BOTH overlays with `helm template`
#                   and diffs the delivered env-var set. Offline (source tree only, no live cluster). We have
#                   missed a setting on OKE more than once; this makes the miss a failing check, not a surprise.
"""
Local-k8s <-> OKE deploy-config parity.

For every service deployed on BOTH targets it renders the Helm ConfigMap twice --
once with the LOCAL overlay (k8s/values/<svc>.yaml) and once with the OKE overlay
(k8s-oci/values/<svc>.yaml) -- and compares the env-var config the app actually
receives. It FAILS on real drift:

  * a config KEY present on one target but missing on the other, and
  * a key that renders EMPTY or "<no value>" on either target (a chart knob that
    one overlay sets and the other forgot).

Value differences that are legitimate (hosts, log levels, replica-driven knobs) are
listed as INFO only -- they are for the eye, they never fail the run.

Both targets use the SAME chart, so an identical key set is the expected healthy
state; the check earns its keep on the EMPTY / "<no value>" case -- exactly the
"added it for local, forgot it on OKE" miss.

Usage:  python test/config-parity/config-parity.py   (or the launcher: k8s-oci/oke-config-parity.bat)
Exit:   0 = in sync,  1 = drift found,  2 = a render failed.

Prereq: helm on PATH. No cluster, no kubectl context -- it reads the charts only.
"""

import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))              # .../services/test/config-parity
SVC = os.path.abspath(os.path.join(HERE, "..", ".."))          # .../services
# --- the two deployment profiles -------------------------------------------------------------
# CLASSIC: seven application processes, local k8s (k8s/) vs OKE (k8s-oci/).
# COMPACT: four, local k8s-compact/ vs OKE k8s-oci-compact/ -- Mesnie carries enyMan, keySmith and
# the identity work; gateWard carries the gate and the tree cache.
#
# Only the paths and the service list differ; every check below is the same for both.
PROFILES = {
    "classic": {
        "charts":       os.path.join(SVC, "k8s", "charts"),
        "local_values": os.path.join(SVC, "k8s", "values"),
        "oke_values":   os.path.join(SVC, "k8s-oci", "values"),
        # service short name -> chart directory. Only the services deployed on BOTH targets.
        "both": {
            "gateway":  "esquire-gateway",
            "biztree":  "esquire-biztree",
            "enyman":   "esquire-enyman",
            "pacman":   "esquire-pacman",
            "keysmith": "esquire-keysmith",
            "kcmaster": "esquire-kcmaster",
            "backend":  "esquire-backend",
        },
        # Deployed on LOCAL k8s only -- no OKE overlay by design (OKE audit = DB triggers, no auKeep
        # pod). Reported so the asymmetry is explicit; never a failure.
        "local_only": {
            "aukeep": "esquire-aukeep",
        },
        # Intended local-vs-OKE differences that must NOT count as drift (the config-var-consistency
        # "expected differences" list). Key by (service, CONFIG_KEY) -> the reason. Anything NOT listed
        # here that comes up empty-on-one-side or key-missing is a genuine miss and fails the run.
        "expected": {
            ("gateway", "ESQ_GW_VANILLA_CLIENTS"): "Token Relay disabled on OKE by design (empty allowlist, audit A1)",
            ("gateway", "ESQ_GW_PHANTOM_CLIENTS"): "Token Relay disabled on OKE by design (empty allowlist, audit A1)",
            ("backend", "REDIS_URL"): "OKE BFF is a SINGLE replica (no shared-session store) -> no Redis on OKE (overlay note)",
            ("backend", "ALLOWED_ORIGINS"): "OKE BFF is same-origin (SPA served by the BFF); cross-origin allowlist empty by design",
        },
    },
    "compact": {
        "charts":       os.path.join(SVC, "k8s-compact", "charts"),
        "local_values": os.path.join(SVC, "k8s-compact", "values"),
        "oke_values":   os.path.join(SVC, "k8s-oci-compact", "values"),
        "both": {
            "gateward": "esquire-gateward",
            "mesnie":   "esquire-mesnie",
            "pacman":   "esquire-pacman",
            "backend":  "esquire-backend",
        },
        # Local compact runs the 5-process shape with the audit BUS drained by auKeep; OKE runs
        # SUPER-COMPACT, audit option (a) DB triggers, so auKeep has no OKE overlay. Same asymmetry
        # the classic profile has, for the same reason.
        "local_only": {
            "aukeep": "esquire-aukeep",
        },
        "expected": {
            ("gateward", "ESQ_GW_VANILLA_CLIENTS"): "Token Relay disabled on OKE by design (empty allowlist, audit A1)",
            ("gateward", "ESQ_GW_PHANTOM_CLIENTS"): "Token Relay disabled on OKE by design (empty allowlist, audit A1)",
            ("backend", "REDIS_URL"): "OKE BFF is a SINGLE replica (no shared-session store) -> no Redis on OKE (overlay note)",
            ("backend", "ALLOWED_ORIGINS"): "OKE BFF is same-origin (SPA served by the BFF); cross-origin allowlist empty by design",
        },
    },
}

PROFILE = "classic"
if "--profile" in sys.argv:
    PROFILE = sys.argv[sys.argv.index("--profile") + 1]
if PROFILE not in PROFILES:
    sys.stderr.write("unknown profile %r -- use one of: %s" % (PROFILE, ", ".join(sorted(PROFILES))) + chr(10))
    sys.exit(2)

CHARTS = PROFILES[PROFILE]["charts"]
LOCAL_VALUES = PROFILES[PROFILE]["local_values"]
OKE_VALUES = PROFILES[PROFILE]["oke_values"]
BOTH = PROFILES[PROFILE]["both"]
LOCAL_ONLY = PROFILES[PROFILE]["local_only"]
EXPECTED = PROFILES[PROFILE]["expected"]

# Harmless placeholder values so `helm template` does not fail on a required secret / tag. A path a chart
# never reads is simply ignored by the template -- passing a superset is safe.
STUBS = [
    "image.tag=parity",
    "db.password=x",
    "keycloak.adminPassword=x",
    "keycloak.clientSecret=x",
    "keycloak.adminClientSecret=x",
    "tokenRelay.phantom.exchangeClientSecret=x",
    "session.secret=x",
]


def render_configmap(chart_dir, values_file):
    """Return {KEY: raw_value} of the rendered ConfigMap's data block, or raise on a render failure."""
    cmd = ["helm", "template", "parity", chart_dir, "-f", values_file]
    for s in STUBS:
        cmd += ["--set", s]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError("helm template failed (%s -f %s):\n%s"
                           % (os.path.basename(chart_dir), os.path.basename(values_file),
                              proc.stderr.strip()[-1000:]))
    return configmap_data(proc.stdout)


def configmap_data(manifest):
    """Parse the ConfigMap document out of a multi-doc helm render; return its data map."""
    ret = {}
    for doc in manifest.split("\n---"):
        if "kind: ConfigMap" not in doc:
            continue
        in_data = False
        for line in doc.split("\n"):
            if not in_data:
                if re.match(r"^data:\s*$", line):
                    in_data = True
                continue
            if line.strip() == "" or re.match(r"^\s+#", line):   # blank or comment line inside data
                continue
            if re.match(r"^\S", line):                           # dedent to column 0 -> data block ended
                break
            m = re.match(r"^  ([A-Za-z0-9_.-]+):\s?(.*)$", line)
            if m:
                ret[m.group(1)] = m.group(2)
        break
    return ret


def norm(value):
    """Strip surrounding quotes/space so an empty check and a value compare are honest."""
    v = value.strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in ("\"", "'"):
        v = v[1:-1]
    return v


def is_empty(value):
    v = norm(value)
    return v == "" or v == "<no value>"


def check_service(name, chart):
    """Compare one service's local vs OKE ConfigMap. Return (fails, allowed, infos) as lists of strings.
    A drift candidate keyed in EXPECTED moves to `allowed` (noted, non-failing) instead of `fails`."""
    infos = []
    candidates = []      # (config_key, message) -- filtered against EXPECTED below
    chart_dir = os.path.join(CHARTS, chart)
    local_f = os.path.join(LOCAL_VALUES, name + ".yaml")
    oke_f = os.path.join(OKE_VALUES, name + ".yaml")
    for label, f in (("local", local_f), ("OKE", oke_f)):
        if not os.path.isfile(f):
            return ["missing %s overlay: %s" % (label, f)], [], infos

    local = render_configmap(chart_dir, local_f)
    oke = render_configmap(chart_dir, oke_f)
    lk, ok = set(local), set(oke)

    for k in sorted(lk - ok):
        candidates.append((k, "KEY set on LOCAL but MISSING on OKE: %s = %s" % (k, norm(local[k]))))
    for k in sorted(ok - lk):
        candidates.append((k, "KEY set on OKE but MISSING on LOCAL: %s = %s" % (k, norm(oke[k]))))

    for k in sorted(lk & ok):
        le, oe = is_empty(local[k]), is_empty(oke[k])
        if oe and not le:
            candidates.append((k, "EMPTY on OKE (set on LOCAL): %s (local=%s)" % (k, norm(local[k]))))
        elif le and not oe:
            candidates.append((k, "EMPTY on LOCAL (set on OKE): %s (oke=%s)" % (k, norm(oke[k]))))
        elif oe and le:
            candidates.append((k, "EMPTY on BOTH: %s (chart knob wired to no value)" % k))
        elif norm(local[k]) != norm(oke[k]):
            infos.append("value differs: %s  local=%s  oke=%s" % (k, norm(local[k]), norm(oke[k])))

    fails = []
    allowed = []
    for key, msg in candidates:
        reason = EXPECTED.get((name, key))
        if reason:
            allowed.append("%s  [expected: %s]" % (msg, reason))
        else:
            fails.append(msg)
    return fails, allowed, infos


def main():
    print("=" * 78)
    print("Local-k8s <-> OKE config parity  (ConfigMap env-var set, rendered from charts)")
    print("profile: %s  --  %s" % (PROFILE, ", ".join(sorted(BOTH))))
    print("=" * 78)
    total_fail = 0
    for name, chart in BOTH.items():
        try:
            fails, allowed, infos = check_service(name, chart)
        except RuntimeError as e:
            print("\n[%s] RENDER ERROR\n  %s" % (name, e))
            return 2
        if fails:
            total_fail += len(fails)
            print("\n[%s] DRIFT (%d)" % (name, len(fails)))
            for f in fails:
                print("  FAIL  " + f)
        else:
            print("\n[%s] in sync" % name)
        for a in allowed:
            print("  allow " + a)
        for i in infos:
            print("  info  " + i)

    if LOCAL_ONLY:
        print("\nLocal-only (no OKE overlay, expected): " + ", ".join(sorted(LOCAL_ONLY)))

    print("\n" + "=" * 78)
    if total_fail:
        print("RESULT: DRIFT -- %d finding(s). Local k8s config is NOT fully applied to OKE." % total_fail)
        return 1
    print("RESULT: in sync -- every local-k8s config key is present and set on OKE.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
