#!/usr/bin/env python3
"""Esquire Haubergeon -- read a perf-matrix CSV and say what it means.

    python perf-matrix-report.py [path\\to\\matrix.csv]

The CSV is raw; this turns it into the three answers the matrix exists to give:

  1. Does super-load PASS in every environment?     (KO rate per config)
  2. What is the NOISE FLOOR?                       (the gap between a config's two
                                                     from-scratch runs -- nothing smaller
                                                     than this may be called a finding)
  3. What do the comparisons cost?                  (o11y ON vs OFF, x2 vs x1, k8s vs docker)
                                                     -- each printed WITH the noise, and flagged
                                                     when the arms overlap.

TWO RULES ARE BAKED IN, and both were learned the hard way:

  * STEADY STATE IS THE PLATEAU, NOT "EVERYTHING AFTER LOAD 1". A run is steady only once its
    throughput STOPS MOVING -- each load within STEADY_TOL% of the one before. Two things break that,
    and BOTH understate the config if averaged in:
      - the tail of the WARM-UP. A run can still be climbing at load 4 (x2 has twice the JVMs to JIT):
        run 6 went 1450 -> 1439 -> 1486 -> 1508, run 8 went 1088 -> ... -> 1177.
      - a DECLINE. The app, the infra, the o11y stack AND the load generator all share one VM; under
        sustained load the host throttles and swaps, and throughput sags (docker: 1159 -> 1080).
    So take only the TRAILING STABLE loads. Load 1 (warm-up, 35-45% below steady) is always excluded.
    Averaging "everything after load 1" is what this rule replaces -- it silently mixed climbs and sags
    into the mean.

  * A DIFFERENCE SMALLER THAN THE NOISE IS NOT A FINDING. Two identical from-scratch runs of the
    same config still differ by a few percent. Before the from-scratch reseed existed they
    differed by 13-17%, and an "observability costs 13%" conclusion drawn from that turned out to
    be pure run-ordering. So every comparison below is printed next to the noise it must beat, and
    the SEPARATION check (does every run of arm A beat every run of arm B?) is the honest test --
    it survives noisy means.
"""
import csv
import os
import sys
from collections import defaultdict

STEADY_TOL = 3.0   # a load joins the plateau if it is within this % of the load before it

DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "output", "perf-matrix", "matrix.csv")


def load(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    if not os.path.exists(path):
        print("no CSV at %s -- run perf-matrix.bat first" % path)
        return
    rows = load(path)
    if not rows:
        print("CSV is empty")
        return

    # steady state = the PLATEAU: the trailing loads that have stopped moving (see module docstring)
    steady = defaultdict(list)      # (config, run) -> [rps ...] of the plateau only
    loads_of = defaultdict(list)    # (config, run) -> [(load, rps) ...] all loads
    plateau_loads = {}              # (config, run) -> set of load numbers in the plateau
    ko = defaultdict(lambda: [0, 0])  # config -> [ko, requests]
    p99 = defaultdict(list)
    by_run = defaultdict(list)

    for r in rows:
        cfg, run, ld = r["config"], int(r["run"]), int(r["load"])
        loads_of[(cfg, run)].append((ld, float(r["rps"])))
        ko[cfg][0] += int(r["ko"] or 0)
        ko[cfg][1] += int(r["requests"] or 0)
        if ld >= 2:
            by_run[(cfg, run)].append(r)

    for key, rs in by_run.items():
        rs = sorted(rs, key=lambda r: int(r["load"]))
        v = [float(r["rps"]) for r in rs]
        i = len(v) - 1
        while i > 0 and 100.0 * abs(v[i] - v[i - 1]) / v[i - 1] <= STEADY_TOL:
            i -= 1
        plateau_loads[key] = set(int(r["load"]) for r in rs[i:])
        steady[key] = v[i:]
        for r in rs[i:]:
            if r["p99_ms"]:
                p99[key[0]].append(float(r["p99_ms"]))

    print("\n" + "=" * 78)
    print("PER-RUN STEADY STATE   (the PLATEAU: trailing loads within %.0f%% of each other;" % STEADY_TOL)
    print("                        load 1 = warm-up; a climbing or sagging load is NOT steady)")
    print("=" * 78)
    means = defaultdict(list)
    for (cfg, run) in sorted(loads_of, key=lambda k: (k[0], k[1])):
        v = steady[(cfg, run)]
        if not v:
            continue
        m = sum(v) / len(v)
        means[cfg].append(m)
        seq = ", ".join(("**%.0f**" if ld in plateau_loads[(cfg, run)] else "%.0f") % x
                        for ld, x in sorted(loads_of[(cfg, run)]))
        print("  %-12s run %-2d  %6.0f rps    loads: %s   (** = steady)" % (cfg, run, m, seq))

    print("\n" + "=" * 78)
    print("1. DOES SUPER-LOAD PASS?   (KO rate per config -- 0 is the goal)")
    print("=" * 78)
    # A handful of failures in a million concurrent requests is the tail of a heavy test, not a
    # defect. A PERCENT of them is: that is the signature of a limiter shedding traffic (the T10
    # bulkhead shed 4.28% in one load while the backend sat perfectly healthy).
    for cfg in sorted(ko):
        k, n = ko[cfg]
        pct = 100.0 * k / n if n else 0.0
        if pct == 0:
            verdict = "PASS  (clean)"
        elif pct < 0.05:
            verdict = "PASS  (%d in %d -- tail of a concurrent test, not a defect)" % (k, n)
        elif pct < 0.5:
            verdict = "SHEDDING -- something is rejecting traffic; find out what"
        else:
            verdict = "FAIL"
        print("  %-12s %9d req, %6d KO  (%.3f%%)   %s" % (cfg, n, k, pct, verdict))

    print("\n" + "=" * 78)
    print("2. NOISE FLOOR   (the two from-scratch runs of the same config)")
    print("   Nothing smaller than this may be called a finding.")
    print("=" * 78)
    noise = {}
    for cfg, v in sorted(means.items()):
        if len(v) >= 2:
            n = 100.0 * (max(v) - min(v)) / min(v)
            noise[cfg] = n
            print("  %-12s %6.0f / %-6.0f -> mean %6.0f rps   noise %5.1f%%" %
                  (cfg, v[0], v[1], sum(v) / len(v), n))
        else:
            print("  %-12s %6.0f rps   (only 1 run -- NO noise estimate, treat with caution)" % (cfg, v[0]))
    worst = max(noise.values()) if noise else 0.0
    if noise:
        print("\n  worst noise across configs: %.1f%%" % worst)

    def cmp(a, b, label):
        """b relative to a, printed against the noise and the separation test."""
        if a not in means or b not in means:
            return
        am = sum(means[a]) / len(means[a])
        bm = sum(means[b]) / len(means[b])
        delta = 100.0 * (bm - am) / am
        # separation: do the two arms' runs overlap at all?
        sep = min(means[a]) > max(means[b]) or min(means[b]) > max(means[a])
        floor = max(noise.get(a, 0), noise.get(b, 0))
        verdict = "CLEAN (arms do not overlap)" if sep else "OVERLAPS -- not resolvable here"
        print("  %-28s %6.0f -> %6.0f rps  = %+6.1f%%   [noise %.1f%%]  %s" %
              (label, am, bm, delta, floor, verdict))

    print("\n" + "=" * 78)
    print("3. THE COMPARISONS   (each against the noise it has to beat)")
    print("=" * 78)
    print("  -- what observability costs --")
    cmp("k8s-x1-OFF", "k8s-x1-ON", "o11y ON (k8s x1)")
    cmp("k8s-x2-OFF", "k8s-x2-ON", "o11y ON (k8s x2)")
    cmp("docker-OFF", "docker-ON", "o11y ON (docker)")
    print("\n  -- what a second replica buys --")
    cmp("k8s-x1-OFF", "k8s-x2-OFF", "x1 -> x2 (o11y OFF)")
    cmp("k8s-x1-ON", "k8s-x2-ON", "x1 -> x2 (o11y ON)")
    print("\n  -- docker vs k8s (NOT like-for-like: see below) --")
    cmp("docker-OFF", "k8s-x1-OFF", "docker -> k8s x1 (OFF)")
    cmp("docker-ON", "k8s-x1-ON", "docker -> k8s x1 (ON)")
    print("""
  CAUTION on docker-vs-k8s: these are different RESOURCE ENVELOPES, not just different
  orchestrators. Every k8s pod is capped at 1 CPU by the R4 budget, so the JVM sees ONE
  processor and sizes its GC / ForkJoin / event-loop threads for a single core. A docker
  container has NO cpu limit and roams over all host cores. k8s being slower here is that
  budget, not a platform defect -- and it is the HONEST shape, because OKE nodes really are
  ~1 OCPU. Docker is the unrealistic one.""")

    print("\n" + "=" * 78)
    print("LATENCY (p99, steady loads only)")
    print("=" * 78)
    for cfg in sorted(p99):
        v = p99[cfg]
        print("  %-12s p99  min %4.0f  avg %4.0f  max %4.0f ms" %
              (cfg, min(v), sum(v) / len(v), max(v)))
    print()


if __name__ == "__main__":
    main()
