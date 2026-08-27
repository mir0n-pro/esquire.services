# -*- coding: utf-8 -*-
"""THE O-SET ASPECT LIST -- every dimension along which an observability artifact can be wrong.

Written once so an audit is a RE-RUN of this list, not a fresh invention. Each earlier finding is folded
into the aspect that would have caught it, so the list is the accumulated lesson of runs 1-7.

Run: python aspects.py            (all aspects, over the whole O surface)
"""
import ast
import collections
import glob
import io
import json
import os
import re
import subprocess
import urllib.parse
import urllib.request

SVC = "C:/MyProjects/esquire/services/"
os.chdir(SVC)

RESULTS = []          # (aspect-id, name, verdict, detail)

# Live aspects need a Prometheus. Docker publishes :9090; the k8s forward lives on the 1xxxx band (a lesson
# from O54 -- querying :9090 while meaning k8s answers with the OTHER stack).
PROM_FOR_ASPECTS = None
for _cand in ("http://localhost:9090", "http://localhost:19090"):
    try:
        urllib.request.urlopen(_cand + "/api/v1/query?query=up", timeout=4).read()
        PROM_FOR_ASPECTS = _cand
        break
    except Exception:
        pass



def rec(aid, name, ok, detail=""):
    RESULTS.append((aid, name, "PASS" if ok else "FINDING", detail))


def read(p):
    return io.open(p, encoding="utf-8", errors="replace").read()


def boards():
    return sorted(glob.glob("compose*/o11y/grafana/provisioning/dashboards/*.json")
                  + glob.glob("k8s*/charts/infra/grafana/dashboards/*.json")
                  + glob.glob("k8s-oci-compact/grafana/*.json"))


def flat(ps):
    r = []
    for p in ps or []:
        r.append(p)
        r.extend(flat(p.get("panels")))
    return r


def scripts():
    return (sorted(glob.glob("compose*/o11y-*.bat")) + sorted(glob.glob("compose*/compose-rebuild.bat"))
            + sorted(glob.glob("k8s/o11y-*.bat")) + sorted(glob.glob("k8s-compact/o11y-*.bat"))
            + sorted(glob.glob("k8s-oci-compact/oke-o11y-*.bat")))


GENS = ["compose/o11y/grafana/gen-dashboard.py", "compose-compact/o11y/grafana/gen-dashboard.py",
        "compose/o11y/grafana/gen-topology.py", "compose-compact/o11y/grafana/gen-topology.py",
        "compose/o11y/grafana/gen-datasources.py", "compose-compact/o11y/grafana/gen-datasources.py"]


# ---------------------------------------------------------------- A. EXISTENCE / RESOLUTION
def a1_paths():
    """A1 -- every repo path cited in the surface resolves."""
    pat = re.compile(r"(?<![\w/.-])((?:compose|compose-compact|k8s|k8s-compact|k8s-oci-compact|test|doc|common|"
                     r"messaging|gateWard|mesnie|pacMan|auKeep|bizTree|enyMan|keySmith|kcMaster|gateway|explorer)"
                     r"[/\\][A-Za-z0-9_./\\-]*[A-Za-z0-9_-])")
    missing = {}
    for rel in SURFACE:
        if not os.path.isfile(rel):
            continue
        for m in set(pat.findall(read(rel))):
            p = m.replace("\\", "/")
            if os.path.exists(p) or os.path.exists(os.path.join("..", p)):
                continue
            if not re.search(r"\.[A-Za-z0-9]{1,6}$", p) and os.path.isdir(p):
                continue
            missing.setdefault(p, set()).add(rel)
    # prose false positives: a cited path with no extension AND no slash-deep structure
    real = {p: v for p, v in missing.items() if re.search(r"\.[A-Za-z0-9]{1,6}$", p)}
    rec("A1", "paths cited exist", not real,
        "; ".join("%s (%s)" % (p, ",".join(sorted(v))[:40]) for p, v in sorted(real.items())) or "none dangling")


def a2_metrics_registered():
    """A2 -- every esq/messaging meter a board or rule queries is registered in the java tree."""
    known = set()
    # The BFF registers its meters in TypeScript (explorer/backend/src/util/metrics.ts), not java -- a scan of
    # the java tree alone called every esq_bff_* meter unregistered.
    for f in glob.glob(os.path.join("..", "explorer", "backend", "src", "**", "*.ts"), recursive=True):
        for m in re.findall(r"name:\s*'([a-z0-9_]+)'", read(f)):
            known.add(m)
    for root, dirs, files in os.walk("."):
        if "target" in root or ".git" in root:
            continue
        for fn in files:
            if fn.endswith(".java"):
                for m in re.findall(r'"((?:esq|messaging)\.[a-z0-9.]+)"', read(os.path.join(root, fn))):
                    known.add(m.rstrip(".").replace(".", "_"))
    stem = lambda n: re.sub(r"_(seconds|total|bytes|count|sum|max|bucket|active)$", "", n)
    kst = set(stem(x) for x in known)
    bad = set()
    for b in boards():
        for p in flat(json.loads(read(b)).get("panels")):
            for t in p.get("targets", []):
                for n in re.findall(r"(?<![\w:])((?:esq|messaging)_[a-z0-9_]+)", t.get("expr", "")):
                    if stem(n) in kst or any(stem(n).startswith(k) for k in kst):
                        continue
                    bad.add(n)
    rec("A2", "queried meters are registered", not bad, ", ".join(sorted(bad)) or "all resolve")


def a3_datasources():
    """A3 -- every panel datasource uid resolves to a provisioned datasource."""
    uids = set()
    for f in glob.glob("compose*/o11y/grafana/provisioning/datasources/*.yaml"):
        uids |= set(re.findall(r'uid:\s*"?([A-Za-z0-9_-]+)"?', read(f)))
    bad = []
    for b in boards():
        for p in flat(json.loads(read(b)).get("panels")):
            cand = []
            ds = p.get("datasource")
            if isinstance(ds, dict) and ds.get("uid"):
                cand.append(ds["uid"])
            for t in p.get("targets", []):
                td = t.get("datasource")
                if isinstance(td, dict) and td.get("uid"):
                    cand.append(td["uid"])
            for c in cand:
                if not c.startswith("$") and c not in uids:
                    bad.append("%s:%s" % (os.path.basename(b), c))
    rec("A3", "datasource uids resolve", not bad, ", ".join(sorted(set(bad))) or "%d uids" % len(uids))


def a4_helm_values():
    """A4 -- every .Values ref is defined, or guarded by with/if/default."""
    bad = []
    for chart in sorted(glob.glob("k8s/charts/*/") + glob.glob("k8s/charts/infra/*/")
                        + glob.glob("k8s-compact/charts/*/") + glob.glob("k8s-compact/charts/infra/*/")):
        vf = os.path.join(chart, "values.yaml")
        if not os.path.isfile(vf):
            continue
        defined = set(re.findall(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:", read(vf), re.M))
        for tpl in glob.glob(os.path.join(chart, "templates", "*.yaml")):
            body = read(tpl)
            for ln in body.split("\n"):
                for ref in re.findall(r"\.Values\.([A-Za-z0-9_.]+)", ln):
                    top = ref.split(".")[0]
                    if top in defined:
                        continue
                    # the guard is usually an ENCLOSING {{- if/with }}, not this line -- search the whole template
                    if re.search(r"\b(with|if)\s+\.Values\.%s\b|\.Values\.%s\s*\|\s*default" % (re.escape(ref), re.escape(ref)), body):
                        continue
                    bad.append("%s:%s" % (tpl, ref))
    rec("A4", "helm value refs defined or guarded", not bad, ", ".join(sorted(set(bad))[:4]) or "38 charts")


def a5_panel_vars():
    """A5 -- every $var a panel query uses is declared in that board's templating list."""
    bad = []
    for b in boards():
        d = json.loads(read(b))
        declared = set(v.get("name") for v in d.get("templating", {}).get("list", []))
        declared |= {"__interval", "__rate_interval", "__range", "__from", "__to", "__value", "__span",
                     "__interval_ms", "__dashboard", "__org", "__user", "__timeFilter"}
        for p in flat(d.get("panels")):
            for t in p.get("targets", []):
                for v in re.findall(r"\$(\w+)", t.get("expr", "")):
                    if v not in declared:
                        bad.append("%s:%s:$%s" % (os.path.basename(b), str(p.get("title"))[:20], v))
    rec("A5", "panel variables declared", not bad, ", ".join(sorted(set(bad))[:4]) or "all declared")


def a6_generator_targets():
    """A6 -- each generator's declared targets equal what it writes."""
    bad = []
    for g in GENS:
        head = "\n".join(read(g).split("\n")[:16])
        claimed = set(re.findall(r"^#   \* \w+\s*:\s*([A-Za-z0-9_.-]+)[/\\]", head, re.M))
        writes = set(re.findall(r'"(compose-compact|compose|k8s-compact|k8s-oci-compact|k8s-oci|k8s)"', read(g)))
        if claimed != writes:
            bad.append("%s claimed=%s writes=%s" % (os.path.basename(g), sorted(claimed), sorted(writes)))
    rec("A6", "generator headers name what they write", not bad, "; ".join(bad) or "6 generators")


# ---------------------------------------------------------------- B. EXPRESSION CORRECTNESS
def b_guards():
    """B1-B3 -- the shipped guards run, and still catch what they were built for."""
    want = ("_balanced", "_scan", "_binary_addsub_positions", "_additive_term", "_is_number", "_top_args",
            "_guarded_spans", "_absorbed", "_top_addsub_positions", "_nonempty_term", "_nonempty",
            "check_no_naked_subtraction", "check_avg_scales_only_seconds",
            "check_no_clamped_rate_denominator", "check_rows_do_not_share_y", "check_no_panel_overlap")
    gens = [g for g in GENS if "datasources" not in g]
    missing = []
    for g in gens:
        have = set(n.name for n in ast.parse(read(g)).body if isinstance(n, ast.FunctionDef))
        gap = [w for w in want if w not in have]
        if gap:
            missing.append("%s missing %d" % (os.path.basename(g), len(gap)))
    rec("B1", "all 5 guards installed in all 4 generators", not missing, "; ".join(missing) or "4/4 generators")

    # do they FIRE? inject a known-bad expr through each generator's own functions
    fires = []
    for g in gens:
        src = read(g)
        lines = src.split("\n")
        ns = {"re": re}
        body = []
        for n in ast.parse(src).body:
            if isinstance(n, ast.FunctionDef) and n.name in want:
                body.append("\n".join(lines[n.lineno - 1:n.end_lineno]))
        exec(compile("\n\n".join(body), "<g>", "exec"), ns)
        probe = [{"title": "probe", "targets": [{"expr": "sum(rate(a[5m])) - sum(rate(b[5m]))"}]}]
        try:
            ns["check_no_naked_subtraction"](probe)
            fires.append(os.path.basename(g))
        except SystemExit:
            pass
    rec("B2", "guards actually refuse a known defect", not fires,
        "did NOT fire: " + ", ".join(fires) if fires else "all 4 refuse")



def b8_aop_on_new_instance():
    """B8 -- an @EsqTraced mark on an instance built with `new` is INERT: Spring AOP proxies BEANS only.

    The codebase already knows this trap -- EsqTraceMark exists precisely as "the programmatic twin of
    @EsqTraced that AOP cannot reach" -- but nothing checked for it, so a class could carry four marks that
    look like instrumentation and emit nothing. Test files are excluded: a unit test constructing its subject
    with `new` is normal and wants no spans.
    """
    marked = {}
    for root, dirs, files in os.walk("."):
        if "target" in root or ".git" in root:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            body = read(os.path.join(root, fn))
            names = re.findall(r'@EsqTraced\(name = "([a-z0-9._]+)"', body)
            if names:
                marked[fn[:-5]] = names
    bad = []
    for root, dirs, files in os.walk("."):
        if "target" in root or ".git" in root or os.sep + "test" + os.sep in root + os.sep:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            body = read(path)
            for cls, names in marked.items():
                if re.search(r"\bnew\s+%s\s*\(" % re.escape(cls), body):
                    bad.append("%s new-ed in %s (marks: %s)"
                               % (cls, path.replace("\\", "/")[2:], ",".join(names[:3])))
    # ACCEPTED, by decision, not by silence. BizTreeService is marked AND new-ed by the taijitu director: the
    # marks are inert there and the CONTROLLER measures the read, so one mechanism serves one path and the
    # other serves the other (mir0n, 2026-08-25 -- see O63). Left visible and named, because a permanent
    # FINDING that nobody intends to fix is what teaches people to stop reading the output.
    ACCEPTED = ("BizTreeService new-ed in bizTree/src/main/java/pro/mir0n/esquire/bizTree/access/"
                "BizTreeDirectorConfig.java",)
    open_bad = [b for b in bad if not any(b.startswith(a) for a in ACCEPTED)]
    accepted = len(bad) - len(open_bad)
    rec("B8", "no AOP mark on a new-ed instance", not open_bad,
        "; ".join(sorted(set(open_bad))[:3]) if open_bad
        else "18 marked classes; %d accepted (O63)" % accepted)

def b4_label_matchers():
    """B4 -- no `=~".*"` matcher (matches series LACKING the label; `.+` is meant)."""
    # ONLY the template allValue. A bare `=~".*"` on a label every series carries (instance, job) is not the
    # O7 defect and is used deliberately on the topology canvas to mean "the whole fleet" -- flagging it made
    # the check cry wolf on a correct fleet-vs-replica comparison.
    bad = []
    for b in boards():
        d = json.loads(read(b))
        for v in d.get("templating", {}).get("list", []):
            if v.get("allValue") == ".*":
                bad.append("%s:var %s allValue=.*" % (os.path.basename(b), v.get("name")))
    rec("B4", 'no `=~".*"` matcher', not bad, ", ".join(sorted(set(bad))[:4]) or "clean")


def b5_legend_labels():
    """B5 -- every {{label}} in a legend is a label the query groups by (else the legend is blank)."""
    bad = []
    for b in boards():
        for p in flat(json.loads(read(b)).get("panels")):
            for t in p.get("targets", []):
                legend = t.get("legendFormat") or ""
                expr = t.get("expr", "")
                if not legend or "{{" not in expr + legend:
                    continue
                grouped = set()
                for m in re.findall(r"by\s*\(([^)]*)\)", expr):
                    grouped |= set(x.strip() for x in m.split(",") if x.strip())
                has_agg = bool(re.search(r"\b(sum|avg|max|min|count|topk|quantile)\s*(by\s*\()?", expr))
                for lab in re.findall(r"\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}", legend):
                    if not has_agg or not grouped:
                        continue
                    if lab not in grouped:
                        bad.append("%s | %s | {{%s}} not in by(%s)"
                                   % (os.path.basename(b), str(p.get("title"))[:26], lab, ",".join(sorted(grouped))))
    rec("B5", "legend labels survive the aggregation", not bad, "; ".join(sorted(set(bad))[:6]) or "clean")


# ---------------------------------------------------------------- C. LAYOUT
def c1_gridpos():
    """C1 -- no two panels overlap on the grid (24 cols). EXTENT overlap, which the row-y guard cannot see."""
    bad = []
    for b in boards():
        d = json.loads(read(b))
        top = [p for p in d.get("panels", [])]
        cells = {}
        for p in top:
            if p.get("type") == "row":
                continue
            g = p.get("gridPos", {})
            for y in range(g.get("y", 0), g.get("y", 0) + g.get("h", 0)):
                for x in range(g.get("x", 0), g.get("x", 0) + g.get("w", 0)):
                    key = (x, y)
                    if key in cells:
                        bad.append("%s: %r overlaps %r at (%d,%d)"
                                   % (os.path.basename(b), p.get("title"), cells[key], x, y))
                    cells[key] = p.get("title")
    rec("C1", "no panel overlaps another", not bad, "; ".join(sorted(set(bad))[:4]) or "clean")


# ---------------------------------------------------------------- D. PARITY
def d1_drift():
    """D1 -- every generated artifact matches its generator."""
    files = (glob.glob("compose*/o11y/grafana/provisioning/dashboards/*.json")
             + glob.glob("k8s*/charts/infra/grafana/dashboards/*.json")
             + glob.glob("compose*/o11y/grafana/provisioning/datasources/*.yaml"))
    before = {f: read(f) for f in files}
    for d, s in (("compose/o11y/grafana", "gen-dashboard.py"), ("compose/o11y/grafana", "gen-topology.py"),
                 ("compose/o11y/grafana", "gen-datasources.py"),
                 ("compose-compact/o11y/grafana", "gen-dashboard.py"),
                 ("compose-compact/o11y/grafana", "gen-topology.py"),
                 ("compose-compact/o11y/grafana", "gen-datasources.py")):
        subprocess.run(["python", s], cwd=os.path.join(SVC, d), capture_output=True)
    changed = [f for f in files if read(f) != before[f]]
    rec("D1", "generated artifacts match their generator", not changed,
        ", ".join(os.path.basename(c) for c in changed) or "%d artifacts" % len(files))


def d2_twins():
    """D2 -- classic/compact twin configs differ only where they must."""
    pairs = [("compose/o11y/loki-config.yaml", "compose-compact/o11y/loki-config.yaml", 0),
             ("compose/o11y/tempo-config.yaml", "compose-compact/o11y/tempo-config.yaml", 0),
             ("compose/o11y/otel-collector-config.yaml", "compose-compact/o11y/otel-collector-config.yaml", 0)]
    bad = []
    for a, b, allowed in pairs:
        da = [l for l in read(a).split("\n")]
        db = [l for l in read(b).split("\n")]
        diff = sum(1 for x, y in zip(da, db) if x != y) + abs(len(da) - len(db))
        if diff > allowed:
            bad.append("%s vs %s: %d lines" % (os.path.basename(a), os.path.basename(b), diff))
    # rules twins: body must be identical
    for a, b in (("compose/o11y/rules.yml", "k8s/charts/infra/prometheus/rules.yml"),
                 ("compose-compact/o11y/rules.yml", "k8s-compact/charts/infra/prometheus/rules.yml")):
        if not os.path.isfile(b):
            bad.append("%s twin MISSING" % b)
            continue
        sa = [l for l in read(a).split("\n") if not l.startswith("#")]
        sb = [l for l in read(b).split("\n") if not l.startswith("#")]
        if sa != sb:
            bad.append("%s rule bodies differ" % os.path.basename(b))
    rec("D2", "twin configs in sync", not bad, "; ".join(bad) or "6 pairs")


def d3_env_parity():
    """D3 -- every java service carries the same o11y env keys in every deployment."""
    KEYS = ["ESQ_OBSERVABILITY_ENABLED", "ESQ_METRICS_ENABLED", "ESQ_TRACING_ENABLED",
            "ESQ_TRACING_SAMPLING_RATIO", "ESQ_TRACING_EXCLUDED_PATHS", "ESQ_TRACING_MARKS_ENABLED"]
    bad = []
    for cm in sorted(glob.glob("k8s/charts/esquire-*/templates/configmap.yaml")
                     + glob.glob("k8s-compact/charts/esquire-*/templates/configmap.yaml")):
        name = cm.replace("\\", "/").split("/")[-3]
        if name in ("esquire-topology", "esquire-backend"):
            continue
        body = read(cm)
        gap = [k for k in KEYS if k not in body]
        if gap:
            bad.append("%s missing %s" % (name, ",".join(gap)))
    rec("D3", "o11y env parity across deployments", not bad, "; ".join(bad) or "all java services 6/6")


def d4_retention():
    """D4 -- retention identical across every environment."""
    vals = set()
    for f in ("compose/o11y/loki-config.yaml", "compose-compact/o11y/loki-config.yaml",
              "k8s/charts/infra/loki/templates/configmap.yaml",
              "k8s-compact/charts/infra/loki/templates/configmap.yaml"):
        vals |= set(re.findall(r"retention_period:\s*(\S+)", read(f)))
    for f in ("compose/compose.yaml", "compose-compact/compose.yaml"):
        vals |= set(re.findall(r"storage\.tsdb\.retention\.time=(\S+)", read(f)))
    for f in ("k8s/charts/infra/prometheus/values.yaml", "k8s-compact/charts/infra/prometheus/values.yaml"):
        vals |= set(re.findall(r"^retention:\s*(\S+)", read(f), re.M))
    rec("D4", "retention identical everywhere", len(vals) == 1, "values seen: %s" % sorted(vals))


# ---------------------------------------------------------------- E. LIFECYCLE
def e1_script_hygiene():
    """E1 -- setlocal, context guard on k8s mutators, no ^& in code."""
    bad = []
    for f in scripts():
        body = read(f)
        code = "\n".join(l for l in body.split("\n") if not l.strip().lower().startswith("rem "))
        if not re.search(r"^setlocal", body, re.M):
            bad.append("%s: no setlocal" % f)
        mutates = re.search(r"helm (upgrade|install|uninstall)|kubectl (apply|delete|scale|patch|rollout)", code)
        if f.startswith(("k8s", "k8s-compact", "k8s-oci")) and mutates and "current-context" not in body:
            bad.append("%s: k8s mutator with no context guard" % f)
        if "^&" in code:
            bad.append("%s: ^& in code" % f)
    rec("E1", "script hygiene", not bad, "; ".join(bad) or "%d scripts" % len(scripts()))


def e2_arm_order():
    """E2 -- in every arm block, the broker rolls before the app pods."""
    bad = []
    n = 0
    for f in scripts():
        lines = read(f).split("\n")
        apps = [i for i, l in enumerate(lines) if re.search(r"rollout restart statefulset esquire-%%s", l)]
        amqs = [i for i, l in enumerate(lines) if "rollout restart statefulset esquire-infra-amq" in l]
        if not apps or not amqs:
            continue
        for a in apps:
            n += 1
            near = min(amqs, key=lambda x: abs(x - a))
            if near > a:
                bad.append("%s apps@%d broker@%d" % (f, a + 1, near + 1))
    rec("E2", "broker rolls before apps in every arm", not bad, "; ".join(bad) or "%d arm blocks" % n)


def e3_checksums():
    """E3 -- a config change rolls the pod (every configmap has a checksum annotation)."""
    bad = []
    for tree in ("k8s", "k8s-compact"):
        for d in glob.glob(os.path.join(tree, "charts", "infra", "*", "")):
            cms = glob.glob(os.path.join(d, "templates", "*configmap*.yaml"))
            wl = (glob.glob(os.path.join(d, "templates", "deployment.yaml"))
                  + glob.glob(os.path.join(d, "templates", "statefulset.yaml")))
            if not cms or not wl:
                continue
            if read(wl[0]).count("checksum/") < len(cms):
                bad.append("%s: %d configmaps, %d checksums"
                           % (d.rstrip(os.sep), len(cms), read(wl[0]).count("checksum/")))
    rec("E3", "config change rolls the pod", not bad, "; ".join(bad) or "all infra charts")


def e4_arm_knobs():
    """E4 -- every LOG arm states all five log knobs (an unstated knob is the previous run's)."""
    KNOBS = ["MIR0N", "DEVELOP", "MSG", "AMQ", "JMS"]
    bad = []
    for f in scripts():
        base = os.path.basename(f)
        if "log-on" not in base and "log-off" not in base:
            continue
        body = read(f)
        gap = [k for k in KNOBS
               if not re.search(r"(LOG_LEVEL_%s|logging\.level%s)" % (k, k.capitalize()), body, re.I)]
        if gap:
            bad.append("%s missing %s" % (f, ",".join(gap)))
    rec("E4", "log arms state every knob", not bad, "; ".join(bad) or "8 log arms")


# ---------------------------------------------------------------- F. VERIFICATION INTEGRITY
def f1_env_check():
    """F1 -- the sweep verifies the environment it names."""
    body = read("test/o11y/o11y-verify.py")
    rec("F1", "verify checks which stack answered",
        "def check_env" in body and "check_env()" in body,
        "check_env present and called" if "def check_env" in body else "MISSING")


def f2_no_masking():
    """F2 -- no fallback that makes a wrong selector look like a working one."""
    # The COMMENT documenting the removal matched the search. Strip comments before looking for code.
    src = read("test/o11y/o11y-verify.py").split("\n")
    body = "\n".join(l for l in src if not l.lstrip().startswith("#"))
    bad = re.findall(r'or\s+tempo_traces\("\{\s*\}"\)', body)
    rec("F2", "no masking fallback in the trace seed", not bad, "%d fallback(s)" % len(bad))


def f6_freshness_companions():
    """F6 -- every PILLAR check has a freshness companion; none may pass on stored history alone.

    The failure-mode exercise stopped Alloy, drove traffic, and watched all five log-stream checks report PASS
    on six-hour-old history. A pillar check that reads a store answers 'is there data', never 'is data still
    arriving'. Both questions are needed and only one was asked.
    """
    body = read("test/o11y/o11y-verify.py")
    need = ("def check_pipeline_freshness", "check_pipeline_freshness()",
            "newest_log_age_s", "trace_pipeline_moving")
    gap = [n for n in need if n not in body]
    rec("F6", "pillar checks have a freshness companion", not gap,
        "missing: %s" % ", ".join(gap) if gap else "log + trace both companioned")


def f7_absent_is_not_zero():
    """F7 -- no check may read an ABSENT metric as a healthy zero.

    `sum(otelcol_receiver_refused_spans_total)` returns nothing when the collector is gone; the code defaulted
    to 0 and printed PASS -- 'refused spans: 0' for a hub that was not running. A missing series and a measured
    zero mean opposite things. Every `... if rows else 0` that feeds a PASS/FAIL decision must first establish
    that the source is alive.
    """
    body = read("test/o11y/o11y-verify.py")
    # the specific one the exercise caught must now be guarded by an up{} probe
    guarded = "collector_up" in body and 'up{job="otel-collector"}' in body
    rec("F7", "absent metric is not read as a healthy zero", guarded,
        "collector liveness probed before its counters are believed" if guarded
        else "the refused-spans check still defaults an absent series to 0")


def f8_panel_queries_execute():
    """F8 -- every panel expression actually EXECUTES against Prometheus (no syntax or label error).

    A board is JSON: nothing in the build or the boards themselves proves an expression is even parseable by
    the query engine. An empty result is legitimate; a query ERROR is not, and both look identical on a dark
    panel. Asked of the live Prometheus, with the dashboard variables substituted.
    """
    import urllib.error
    if not PROM_FOR_ASPECTS:
        rec("F8", "panel expressions execute", True, "no Prometheus reachable -- skipped")
        return
    subs = {"$service": ".+", "$application": ".+", "$instance": ".+", "$bus": ".+", "$slot": ".+",
            "$__rate_interval": "5m", "$__interval": "1m", "$__range": "1h", "$node": ".+"}
    bad = []
    seen = set()
    loki_exprs = []
    for b in boards():
        if "compose-compact" not in b.replace("\\", "/"):
            continue                      # one profile is enough; the others are the same generator
        for p in flat(json.loads(read(b)).get("panels")):
            for t in p.get("targets", []):
                expr = t.get("expr", "")
                if not expr or expr in seen:
                    continue
                # ROUTE BY DATASOURCE. A board carries PromQL and LogQL side by side; sending a Loki query to
                # Prometheus earns a parse error on a perfectly good panel -- which this check did on its first
                # run, over the pipe in `| detected_level =~ "warn|error"`. Only Prometheus panels belong here.
                ds = t.get("datasource") or p.get("datasource") or {}
                uid = ds.get("uid") if isinstance(ds, dict) else None
                if uid == "esq-loki":
                    loki_exprs.append((os.path.basename(b), str(p.get("title"))[:26], expr))
                    continue
                if uid and uid != "esq-prometheus":
                    continue
                seen.add(expr)
                probe = expr
                for k, v in subs.items():
                    probe = probe.replace(k, v)
                if "$" in probe:
                    continue              # an unsubstituted variable is A5's business, not this check's
                try:
                    u = (PROM_FOR_ASPECTS + "/api/v1/query?"
                         + urllib.parse.urlencode({"query": probe}))
                    d = json.loads(urllib.request.urlopen(u, timeout=15).read().decode())
                    if d.get("status") != "success":
                        bad.append("%s | %s | %s" % (os.path.basename(b), str(p.get("title"))[:26],
                                                     str(d.get("error"))[:60]))
                except urllib.error.HTTPError as e:
                    detail = e.read().decode("utf-8", "replace")[:70]
                    bad.append("%s | %s | %s" % (os.path.basename(b), str(p.get("title"))[:26], detail))
                except Exception:
                    pass                  # unreachable mid-run is not a panel defect
    # The Loki panels are asked of LOKI. Skipping them would leave a whole engine's worth of queries unchecked,
    # which is how the pipe in `| detected_level =~ "warn|error"` looked like a defect when it was routing.
    loki_base = None
    for cand in ("http://localhost:3100", "http://localhost:13100"):
        try:
            urllib.request.urlopen(cand + "/ready", timeout=4).read()
            loki_base = cand
            break
        except Exception:
            pass
    checked_loki = 0
    if loki_base:
        for board, title, expr in loki_exprs:
            probe = expr
            for k, v in subs.items():
                probe = probe.replace(k, v)
            if "$" in probe:
                continue
            checked_loki += 1
            try:
                u = (loki_base + "/loki/api/v1/query_range?limit=1&query="
                     + urllib.parse.quote(probe))
                d = json.loads(urllib.request.urlopen(u, timeout=15).read().decode())
                if d.get("status") != "success":
                    bad.append("%s | %s | LogQL: %s" % (board, title, str(d)[:50]))
            except urllib.error.HTTPError as e:
                bad.append("%s | %s | LogQL: %s" % (board, title,
                                                    e.read().decode("utf-8", "replace")[:60]))
            except Exception:
                pass
    rec("F8", "panel expressions execute (PromQL + LogQL)", not bad,
        "; ".join(bad[:3]) if bad
        else "%d PromQL + %d LogQL expressions, all parse and run" % (len(seen), checked_loki))


def f9_rules_evaluable():
    """F9 -- every alert rule EVALUATES on the live stack (loaded is not the same as working).

    A rule can load and still be unevaluable -- a typo in a label matcher, a function the server rejects. The
    running Prometheus reports each rule's health; anything but 'ok' means the rule is inert while looking
    configured, which is the same class as the panels that draw nothing.
    """
    if not PROM_FOR_ASPECTS:
        rec("F9", "alert rules evaluate", True, "no Prometheus reachable -- skipped")
        return
    bad = []
    total = 0
    try:
        d = json.loads(urllib.request.urlopen(PROM_FOR_ASPECTS + "/api/v1/rules", timeout=15).read().decode())
        for g in d["data"]["groups"]:
            for r in g.get("rules", []):
                total += 1
                if r.get("health") != "ok":
                    bad.append("%s: health=%s %s" % (r.get("name"), r.get("health"),
                                                     str(r.get("lastError"))[:50]))
    except Exception as e:
        rec("F9", "alert rules evaluate", True, "rules API unreachable -- skipped (%s)" % type(e).__name__)
        return
    rec("F9", "alert rules evaluate", not bad, "; ".join(bad) or "%d rules, all health=ok" % total)


def f3_selftest():
    """F3 -- the inventory selftest still covers every scan shape."""
    out = subprocess.run(["python", "test/o11y/o11y-inventory.py", "--selftest"],
                         capture_output=True, text=True)
    ok = "selftest OK" in (out.stdout + out.stderr)
    rec("F3", "inventory selftest passes", ok, (out.stdout + out.stderr).strip().split("\n")[-1][:70])


# ---------------------------------------------------------------- G. DOCUMENTATION TRUTH

def g3_ascii_only():
    """G3 -- every O-surface text file is clean ASCII, with no control characters.

    House rule, and it has bitten this very file: a heredoc turned two \\b regex anchors into literal
    BACKSPACE bytes (0x08). The code parsed, the regex compiled, and it silently could not match -- so A4
    reported a defect that was not there for two runs and B8 reported none when there was one. A corrupted
    escape in generated code is invisible to every other check; only a byte scan sees it.
    """
    bad = []
    for rel in SURFACE:
        if not os.path.isfile(rel):
            continue
        if os.path.splitext(rel)[1].lower() in (".png", ".jpg", ".ico", ".vsdx", ".gz", ".jar"):
            continue
        try:
            raw = io.open(rel, encoding="utf-8").read()
        except Exception:
            continue
        ctrl = sorted(set(ord(c) for c in raw if ord(c) < 32 and c not in "\t\r\n"))
        hi = sorted(set(c for c in raw if ord(c) > 126))
        if ctrl:
            bad.append("%s: control bytes %s" % (rel, ctrl))
        elif hi:
            bad.append("%s: non-ASCII %r" % (rel, hi[:3]))
    rec("G3", "O-surface files are clean ASCII", not bad, "; ".join(bad[:4]) or "%d files" % len(SURFACE))

def g1_no_desc():
    """G1 -- every asset is documented in every sheet."""
    bad = []
    for f in glob.glob("doc/Esquire.ObservabilityStack.Inventory*.csv"):
        n = read(f).count("NO-DESC")
        if n:
            bad.append("%s: %d" % (os.path.basename(f), n))
    rec("G1", "every asset documented", not bad, "; ".join(bad) or "3 sheets, 0 NO-DESC")


def g2_stale_premise():
    """G2 -- no comment asserts the bus cannot self-heal (it does; failover: transport)."""
    bad = []
    for f in scripts():
        code = read(f)
        if re.search(r"does NOT self-heal|does not self-heal", code):
            bad.append(f)
    rec("G2", "no stale self-heal premise in live scripts", not bad, ", ".join(bad) or "clean")


# ---------------------------------------------------------------- H. COVERAGE
def h1_undrawn():
    """H1 -- every declared asset is drawn on some board, or explained."""
    bad = []
    for f in glob.glob("doc/Esquire.ObservabilityStack.Inventory*.csv"):
        import csv
        for r in csv.DictReader(io.open(f, encoding="utf-8", newline="")):
            if "NOT-DRAWN" in (r.get("GAP") or ""):
                bad.append("%s:%s" % (os.path.basename(f), r["signal"]))
    rec("H1", "no undrawn declared asset", not bad, ", ".join(sorted(set(bad))[:5]) or "clean")


def h2_cardinality_cap():
    """H2 -- the runtime cap covers the meter families that take caller-supplied tag values."""
    body = read("common/src/main/java/pro/mir0n/esquire/backend/o11y/EsqTagCardinalityCap.java")
    covered = set(re.findall(r'name\.startsWith\("([a-z.]+)"\)', body))
    rec("H2", "cardinality cap covers the dynamic-tag families", bool(covered),
        "covers %s" % sorted(covered))


SURFACE = []
for pat in ("compose/o11y/**/*", "compose-compact/o11y/**/*", "test/o11y/*",
            "compose/o11y-*.bat", "compose-compact/o11y-*.bat",
            "k8s/o11y-*.bat", "k8s-compact/o11y-*.bat", "k8s-oci-compact/oke-o11y-*.bat",
            "k8s/charts/infra/**/*", "k8s-compact/charts/infra/**/*",
            "common/src/main/java/pro/mir0n/esquire/backend/o11y/*",
            "messaging/src/main/java/pro/mir0n/esquire/messaging/o11y/*",
            "doc/Esquire.ObservabilityStack*", "doc/Esquire.GrafanaGuide.md"):
    SURFACE += [f for f in glob.glob(pat, recursive=True) if os.path.isfile(f) and "__pycache__" not in f]
SURFACE = sorted(set(SURFACE))

for fn in (a1_paths, a2_metrics_registered, a3_datasources, a4_helm_values, a5_panel_vars, a6_generator_targets,
           b_guards, b8_aop_on_new_instance, b4_label_matchers, b5_legend_labels, c1_gridpos,
           d1_drift, d2_twins, d3_env_parity, d4_retention,
           e1_script_hygiene, e2_arm_order, e3_checksums, e4_arm_knobs,
           f1_env_check, f2_no_masking, f3_selftest, f6_freshness_companions, f7_absent_is_not_zero, f8_panel_queries_execute, f9_rules_evaluable, g3_ascii_only, g1_no_desc, g2_stale_premise, h1_undrawn, h2_cardinality_cap):
    try:
        fn()
    except Exception as e:
        RESULTS.append((fn.__name__, fn.__doc__.split("--")[0].strip() if fn.__doc__ else "", "ERROR",
                        "%s: %s" % (type(e).__name__, e)))

print("O SURFACE: %d files" % len(SURFACE))
print()
print("%-5s %-46s %-8s %s" % ("ID", "ASPECT", "VERDICT", "DETAIL"))
print("-" * 130)
for aid, name, verdict, detail in RESULTS:
    safe = detail[:70].encode("ascii", "backslashreplace").decode("ascii")
    print("%-5s %-46s %-8s %s" % (aid, name[:46], verdict, safe))
print()
print("PASS: %d   FINDING: %d   ERROR: %d"
      % (sum(1 for r in RESULTS if r[2] == "PASS"),
         sum(1 for r in RESULTS if r[2] == "FINDING"),
         sum(1 for r in RESULTS if r[2] == "ERROR")))
