#!/usr/bin/env python3
#  Esquire frameworks (tm)
#  observability stack -- the driver
#
#  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
#  mailto:mir0n.the.programmer@gmail.com
#
#  History:
# 07/16/2026 mir0n  created (v1.2.11 T11/I48): DRIVE every o11y asset so the sweep has something to assert.
#                   o11y-verify.py only OBSERVES -- it asks Prometheus/Loki/Tempo "is it there?" and can never
#                   MAKE an asset appear. So a third of the inventory read as unproven not because anything was
#                   broken, but because nothing had ever called it: esq.svc.node and esq.svc.save had NEVER fired
#                   in the life of the stack, and two API calls lit both. This script is the missing half.
#                   ONE launcher per environment (compose/o11y-test.bat, k8s/o11y-test.bat) sets only the
#                   addresses -- same shape as o11y-verify.
"""
DRIVE the Esquire o11y assets: log in, then exercise every REST path that has a meter or a mark on it.

    BASE_URL=http://localhost:3000 ESQ_USER=... ESQ_PASS=... python o11y-drive.py

Environment (the launcher sets these; only the addresses differ per environment):
    BASE_URL   the BFF's browser-facing base           (docker: http://localhost:3000)
    ESQ_USER   an interactive-login user (au_connect_flg='Y')
    ESQ_PASS   its password

WHAT THIS IS NOT.  It is not a functional test -- it asserts almost nothing and is happy with a 4xx from a
business rule. Its ONLY job is to make the fleet EMIT, so o11y-verify has something real to check. The e2e proves
behaviour; this proves the instrumentation exists on the paths behaviour uses.

WHY IT IS SEPARATE FROM o11y-verify.  The sweep is READ-ONLY and safe to point at any environment, including one
you must not disturb. This one WRITES (it re-saves an entity). Keeping them apart means the sweep never has to
ask permission, and the driver never runs by accident.

WHAT IT CANNOT REACH, and why that is not a failure:
  - esq.svc.cache -- lives in BizTreeDirectorLegacy, and BIZTREE_DIRECTOR=taijitu everywhere. The class is never
    instantiated, so no driver can light it. Config-unreachable, not undriven (I48).
  - messaging.error / retry.backoff / retry.dropped / esq.biz.move.failed -- FAILURE paths. They fire only when
    something actually breaks, which is the broker-down smoke's job, not a happy-path driver's.
"""
import http.cookiejar
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:3000").rstrip("/")
USER = os.environ.get("ESQ_USER", "")
PASS = os.environ.get("ESQ_PASS", "")

DROVE = []
SKIPPED = []


class _SandboxCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    """Return KeyCloak's cookies over plain http on a dotless host. Sandbox only -- and it IS needed.

    Two of http.cookiejar's defaults conspire against a localhost login, and the symptom is a lie: KC answers
    400 "Restart login cookie not found ... or cookies are disabled in your browser", which reads like bad
    credentials.
      1. a DOTLESS host is rewritten localhost -> localhost.local (the two-dot rule for domain cookies), so the
         cookie's domain no longer matches the host it came from;
      2. KC marks AUTH_SESSION_ID / KC_RESTART **secure**, and a secure cookie is never returned over http --
         correct on the internet, fatal on a sandbox that IS http.
    curl does neither, which is why the same flow works by hand and fails here. Overriding is safe for exactly
    this job: a throwaway login against the local sandbox. Never widen it to a real deployment -- a secure cookie
    withheld over http is a protection, not a bug.
    """

    def set_ok_secure(self, cookie, request):
        return True

    def return_ok_secure(self, cookie, request):
        return True

    def return_ok_domain(self, cookie, request):
        host = http.cookiejar.request_host(request)
        if cookie.domain in (host, host + ".local", "." + host):
            return True
        return http.cookiejar.DefaultCookiePolicy.return_ok_domain(self, cookie, request)


def _opener():
    jar = http.cookiejar.CookieJar(policy=_SandboxCookiePolicy())
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


OPENER = _opener()


def _call(method, url, body=None, ctype="application/json"):
    """Returns (status, text). A 4xx is DATA, not an exception -- a business rule saying no still emitted."""
    data = None
    headers = {}
    if body is not None:
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        headers["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with OPENER.open(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, str(e)


def login():
    """The real OIDC login -- the BFF's /auth/login, KeyCloak's form, the callback. Same flow a browser does."""
    status, page = _call("GET", BASE + "/auth/login")
    if status != 200:
        raise SystemExit("login: /auth/login -> %s (is the stack up at %s?)" % (status, BASE))
    action = re.search(r'action="([^"]+)"', page, re.I)
    if not action:
        raise SystemExit("login: no form action in the KeyCloak page -- already logged in, or KC not reachable")
    url = action.group(1).replace("&amp;", "&")
    form = urllib.parse.urlencode({"username": USER, "password": PASS, "credentialId": ""})
    # KC answers the form with a 302 to /auth/callback; the opener follows it, and the BFF sets the session.
    status, _ = _call("POST", url, form, "application/x-www-form-urlencoded")
    if status not in (200, 302):
        raise SystemExit("login: KC form POST -> %s (bad credentials for %r?)" % (status, USER))
    status, _ = _call("GET", BASE + "/auth/me")
    if status != 200:
        raise SystemExit("login: session not established (/auth/me -> %s)" % status)
    DROVE.append(("login (OIDC: discovery + token exchange)", 200))


def api(method, path, body=None, label=None):
    status, text = _call(method, BASE + "/api" + path, body)
    DROVE.append((label or (method + " " + path), status))
    return status, text


def pick_entity(tree_json):
    """A real (id, kind) from the tree -- never a guessed one. Prefers a plain entity over a folder."""
    try:
        nodes = json.loads(tree_json)
    except Exception:
        return None, None
    for n in nodes:
        if n.get("entityId") and n.get("kind") and "~" not in str(n.get("id", "")):
            return str(n["id"]), int(n["kind"])
    return None, None


def main():
    if not USER or not PASS:
        raise SystemExit("ESQ_USER / ESQ_PASS not set -- the launcher supplies them (never hardcode a credential)")

    print("=== driving %s ===" % BASE)
    login()

    # ---- reads: tree / subtree / path / node / dict / kinds -------------------------------------------------
    status, tree = api("GET", "/esq-tree?id=1", label="GET /esq-tree      -> esq.svc.tree|subtree")
    api("GET", "/esq-kinds", label="GET /esq-kinds     -> esq.biz.dict/kinds + the BFF cache")
    api("GET", "/esq-dict?kind=1000", label="GET /esq-dict       -> esq.biz.dict.lookup")

    entity_id, kind = pick_entity(tree)
    if entity_id is None:
        SKIPPED.append("no entity in the tree -- is the stack seeded? (writes skipped)")
    else:
        api("GET", "/esq-enode?id=%s&kind=%d" % (entity_id, kind),
            label="GET /esq-enode      -> esq.svc.node")
        api("GET", "/esq-path?id=%s&kind=%d" % (entity_id, kind),
            label="GET /esq-path       -> esq.svc.path")

        # ---- write: a NO-OP re-save. The e2e creates/moves/deletes but never UPDATES, which is why
        # esq.svc.save had never fired once in the life of the stack.
        status, node = _call("GET", BASE + "/api/esq-enode?id=%s&kind=%d" % (entity_id, kind))
        fields = {}
        try:
            d = json.loads(node)
            fields = {k: v for k, v in d.items() if k in ("name", "desc", "fullName")}
        except Exception:
            pass
        api("POST", "/esq-cmd-save?kind=%d&id=%s&cmd=save" % (kind, entity_id), json.dumps(fields),
            label="POST /esq-cmd-save  -> esq.svc.save (the UPDATE path)")

    # ---- keys: the permission surface (esq.svc.key.read). `id` is a USER id and is OPTIONAL -- omitted, so the
    # caller reads its OWN access profile. Passing an ENTITY id here 400s at the edge, which means keySmith is
    # never reached and the mark never fires: a driver that "called" it and emitted nothing.
    api("GET", "/esq-key", label="GET /esq-key        -> esq.svc.key.read")

    print()
    print("--- drove ---")
    for what, status in DROVE:
        mark = "ok " if status in (200, 201, 204) else ("%-3d" % status)
        print("  [%s] %s" % (mark, what))
    for note in SKIPPED:
        print("  [skip] %s" % note)
    print()
    print("%d call(s). NOTE: a 4xx is fine here -- a business rule saying no still EMITTED." % len(DROVE))
    print("Now run o11y-verify to assert what this made appear.")


if __name__ == "__main__":
    main()
