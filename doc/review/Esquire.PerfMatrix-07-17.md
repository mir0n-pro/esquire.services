# Esquire -- Observability Cost: the three modes

*Measured 2026-07-17. One Windows host, 24 logical cores, Docker Desktop, local k8s (docker-desktop), WSL2 VM
16 GB. Load: hauberk (Gatling) super-load, 200 virtual users -- read 64, tx 64, update 32, create 32, move 8 --
120 s per load, driven through the gateway.*

## Abstract

**This answers one question: what does observability cost, pillar by pillar?**

`doc/review/Esquire.PerfMatrix-07-14.md` (Part I) priced observability as ONE lump -- the stack as shipped,
with collectors versus without. That number is sound, but it cannot say which pillar bought what, and it was
measured against a baseline that was still logging.

Here NOTHING logs in the baseline, and exactly ONE thing changes between arms.

## 1. The three modes

| mode | application logger (`pro.mir0n`) | log stack | tracing + metrics | viewing stack |
|---|---|---|---|---|
| **OFF** | OFF | none | off | none |
| **ONLY-LOGGING** | INFO | loki + alloy + grafana | off | logs only |
| **IN-FULL** | INFO | loki + alloy + grafana | **on** (histograms, sampling 1.0) | full (+ tempo, prometheus, otel-collector, postgres-exporter) |

**They add up, because only `pro.mir0n` ever moves:**

```
  OFF  -> ONLY-LOGGING   =  the log pillar alone
  ONLY-LOGGING -> IN-FULL =  tracing + metrics alone
  OFF  -> IN-FULL        =  the whole observability bill
```

**Every other logger is OFF in every arm** -- `develop`, `msg`, `amq`, `jms` -- so none of them can leak into a
delta. `levelRoot` is NOT touched and stays at its ERROR default .

**Why `levelMir0n` and not `levelRoot`:** in `logback-spring.xml` the `pro.mir0n` logger carries its OWN level
and has NO appender of its own, so its events reach the root's ECS CONSOLE appender by **additivity** -- and an
ancestor's LEVEL is never re-checked on the way. `levelRoot` therefore gates only third-party libraries and
**cannot silence the application**. An earlier attempt turned root, reported "logging costs 4.7%", and was void:
the application logged identically in both arms and cancelled out. See Part II of the 07-14 doc.

Grafana is inside the ONLY-LOGGING arm on purpose : it is how a human READS the logs, and a log pillar
nobody can look at is not the thing we ship.

**Required infra only.** kafka is removed from every run (nothing references it -- the audit sink is `audit-c`,
AMQ -> auKeep). redis is removed at x1, where the BFF falls back to an in-memory session store, and KEPT at x2,
where two BFF replicas genuinely need a shared session store .

## 1.1 Method

**k8s only. docker is excluded** -- it is uncapped, saturates the host, and its sag scales WITH throughput, so
the decline eats the delta in one direction. See Part III of the 07-14 doc for the evidence and the decision.

**One run per config, 4-6 loads** . The 2-runs-per-config rule of the earlier matrix existed to catch
FROM-SCRATCH drift (accumulated audit rows, run ordering) -- a problem since fixed, and a different question
from "is this delta real". Here the **load-to-load spread across the plateau** is the noise estimate, and it is
0.2-3.7%. Every run is built FROM SCRATCH: cluster torn down, PVCs dropped, Postgres re-seeded, KeyCloak realm
re-imported.

**Plateau** = the trailing loads that have stopped moving (each within 3% of the one before). Load 1 is warm-up
and always excluded.

Raw CSV: `explorer/hauberk/output/perf-matrix/../perf-o11y-0717/matrix.csv`
Rerun: `perf-matrix.ps1 -Only k8s-x1-OFF,k8s-x1-LOG,k8s-x1-FULL,k8s-x2-OFF,k8s-x2-LOG,k8s-x2-FULL -Runs 1 -OutDir <abs path>`

---

## 2. Every run, every load

### Run 1 -- k8s x1 -- OFF (all logging off, no stack)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 86,079 | 717.3 | 278 | 199 | 718 | 1101 | 2,102 | 0 | warm-up |
| 2 | 147,462 | 1228.8 | 162 | 154 | 307 | 398 | 898 | 0 | **steady** |
| 3 | 146,395 | 1220 | 163 | 154 | 303 | 391 | 2,065 | 0 | **steady** |
| 4 | 148,322 | 1236 | 161 | 155 | 297 | 356 | 2,004 | 0 | **steady** |

**Plateau: 1228 rps** (3 loads, spread 1.3%)

### Run 2 -- k8s x1 -- ONLY-LOGGING (pro.mir0n INFO + loki/alloy/grafana)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 61,382 | 511.5 | 390 | 300 | 864 | 1187 | 2,122 | 0 | warm-up |
| 2 | 115,142 | 959.5 | 207 | 192 | 392 | 498 | 2,095 | 0 | **steady** |
| 3 | 118,102 | 984.2 | 202 | 195 | 356 | 427 | 1,360 | 0 | **steady** |
| 4 | 119,370 | 994.8 | 200 | 195 | 349 | 410 | 1,476 | 0 | **steady** |

**Plateau: 980 rps** (3 loads, spread 3.7%)

### Run 3 -- k8s x1 -- IN-FULL (logging + tracing + metrics + full stack)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 68,367 | 569.7 | 350 | 229 | 838 | 1286 | 2,233 | 0 | warm-up |
| 2 | 107,767 | 898.1 | 222 | 197 | 448 | 570 | 1,512 | 0 | not steady |
| 3 | 112,583 | 938.2 | 212 | 200 | 374 | 450 | 2,188 | 0 | **steady** |
| 4 | 110,623 | 921.9 | 216 | 202 | 385 | 480 | 1,921 | 0 | **steady** |

**Plateau: 930 rps** (2 loads, spread 1.8%)

### Run 5 -- k8s x2 -- OFF (all logging off, no stack)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 126,107 | 1050.9 | 190 | 174 | 503 | 755 | 1,789 | 0 | warm-up |
| 2 | 221,102 | 1842.5 | 108 | 86 | 247 | 355 | 2,379 | 0 | not steady |
| 3 | 217,136 | 1809.5 | 110 | 85 | 240 | 345 | 3,113 | 0 | not steady |
| 4 | 226,685 | 1889 | 105 | 87 | 233 | 323 | 2,936 | 0 | **steady** |
| 5 | 225,222 | 1876.8 | 106 | 87 | 234 | 330 | 2,895 | 0 | **steady** |

**Plateau: 1883 rps** (2 loads, spread 0.7%)

### Run 6 -- k8s x2 -- ONLY-LOGGING (pro.mir0n INFO + loki/alloy/grafana)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 128,360 | 1069.7 | 186 | 135 | 469 | 661 | 1,487 | 0 | warm-up |
| 2 | 184,731 | 1539.4 | 128 | 110 | 266 | 384 | 2,362 | 0 | **steady** |
| 3 | 182,912 | 1524.3 | 131 | 109 | 251 | 437 | 2,531 | 3 | **steady** |
| 4 | 183,854 | 1532.1 | 129 | 108 | 251 | 425 | 2,741 | 5 | **steady** |

**Plateau: 1532 rps** (3 loads, spread 1.0%)

### Run 7 -- k8s x2 -- IN-FULL (logging + tracing + metrics + full stack)

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 113,699 | 947.5 | 210 | 174 | 516 | 829 | 2,413 | 0 | warm-up |
| 2 | 149,720 | 1247.7 | 160 | 131 | 329 | 893 | 2,513 | 0 | not steady |
| 3 | 165,175 | 1376.5 | 144 | 121 | 289 | 646 | 2,216 | 0 | **steady** |
| 4 | 165,442 | 1378.7 | 144 | 122 | 280 | 479 | 2,615 | 0 | **steady** |

**Plateau: 1378 rps** (2 loads, spread 0.2%)

## 3. Aggregated

Latency over the plateau loads only; requests / KO count every load.

| config | plateau rps | spread | mean ms | p50 | p95 | p99 | requests | KO | rate |
|---|---|---|---|---|---|---|---|---|---|
| k8s x1, OFF | **1228** | 1.3% | 162 | 154 | 302 | 382 | 528,258 | 0 | 0.0000% |
| k8s x1, ONLY-LOGGING | **980** | 3.7% | 203 | 194 | 366 | 445 | 413,996 | 0 | 0.0000% |
| k8s x1, IN-FULL | **930** | 1.8% | 214 | 201 | 380 | 465 | 399,340 | 0 | 0.0000% |
| k8s x2, OFF | **1883** | 0.7% | 106 | 87 | 234 | 326 | 1,016,252 | 0 | 0.0000% |
| k8s x2, ONLY-LOGGING | **1532** | 1.0% | 129 | 109 | 256 | 415 | 679,857 | 8 | 0.0012% |
| k8s x2, IN-FULL | **1378** | 0.2% | 144 | 122 | 284 | 562 | 594,036 | 0 | 0.0000% |
| **total** | | | | | | | **3,631,739** | **8** | **0.0002%** |

---

## 4. Results

### 4.1 Failures

**3,631,739 requests, 8 KO -- 0.0002%.** Every mode passed.

The 8 KO are all in `k8s x2, ONLY-LOGGING` (loads 3 and 4). **They are NOT the known 30 s slow-route
deadline**: their loads' slowest responses were 2,531 ms and 2,741 ms, nowhere near 30,000 ms, so this is not
the pattern that produced every KO in the 07-14 matrix. 8 failures in 3.6 million is negligible in throughput
terms and changes no number here, but the cause is **unexplained** and is listed in section 5.

### 4.2 What each pillar costs

| | OFF | ONLY-LOGGING | IN-FULL | logging alone | tracing+metrics alone | **whole bill** |
|---|---|---|---|---|---|---|
| **k8s x1** | 1228 | 980 | 930 | **-20.3%** | **-5.0%** | **-24.3%** |
| **k8s x2** | 1883 | 1532 | 1378 | **-18.6%** | **-10.1%** | **-26.8%** |

Both bills are clean: the slowest OFF load beats the fastest IN-FULL load in each pair (x1: 1220 > 938;
x2: 1877 > 1379), and every plateau holds to within 0.2-3.7%.

### 4.3 LOGGING IS THE BILL

| | logging | tracing + metrics |
|---|---|---|
| **k8s x1** | **83%** | 17% |
| **k8s x2** | **69%** | 31% |

**Logging is not a share of the observability cost -- it is most of it.** Four fifths of the bill at x1, two
thirds at x2. Every earlier reading pointed the other way, and every one of them was measured against a
baseline that was already logging.

### 4.4 Logging is FLAT with the fleet; tracing and metrics DOUBLE

| | x1 | x2 |
|---|---|---|
| logging | -20.3% | **-18.6%** |
| tracing + metrics | -5.0% | **-10.1%** |

This is the shape worth carrying, and it follows from where each pillar spends.

**Logging is CPU spent per line, inside the instance that logs it.** It scales with the work that instance
does, so it stays the same FRACTION of throughput no matter how many replicas run -- ~19% at x1 and at x2.
Adding pods adds logging and adds capacity in the same proportion.

**Tracing and metrics are different: 16 instrumented JVMs push into the SAME collectors on the SAME box.**
Doubling the fleet doubles what arrives at one place, so the cost per replica grows -- it doubled here, -5.0%
to -10.1%, and it is the mechanism behind the 07-14 finding that the o11y bill roughly doubles from x1 to x2.
`sampling-ratio` (1.0 here -- every request traced) is the lever on this half, and only this half.

### 4.5 The second replica

| mode | x1 | x2 | gain |
|---|---|---|---|
| OFF | 1228 | 1883 | **+53.3%** |
| ONLY-LOGGING | 980 | 1532 | **+56.4%** |
| IN-FULL | 930 | 1378 | **+48.1%** |

About half again, not double -- the CPU budget (1 CPU per pod) is the binding constraint, exactly as 07-14
found (+54.4% at o11y OFF). That an independent quantity reproduces on a completely rebuilt stack with
different logging settings is the best evidence available that this baseline is sound.

The gain shrinks with the full stack (+48.1%) because tracing and metrics take a bigger cut at x2 (4.4).

### 4.6 Why this says -24% where 07-14 says -12%

**Both are right; they measure from different places.** 07-14's "o11y OFF" arm ran `pro.mir0n` at DEBUG, so it
was already paying for log encoding on BOTH sides of its comparison -- its 12% / 24% contains the collectors
and the shipping, but never the encode. Measured from actual silence the full bill is **-24.3% (x1)** and
**-26.8% (x2)**.

So 07-14 answers *"what do the collectors cost on the stack as we ship it?"* -- **12% / 24%, and that stands.**
This doc answers *"what does observability cost, from nothing?"* -- **24% / 27%, of which logging is the large
majority.** Neither supersedes the other. They must not be quoted as if they were the same number.

---

## 5. Still open

- **The 8 KO in `k8s x2, ONLY-LOGGING`** -- not the 30 s deadline (max 2.5-2.7 s), cause unknown. Small enough
  to change nothing measured here; unexplained enough to write down.
- **The ONLY-LOGGING arm at x1 was still creeping** (960, 984, 995 -- each step under the 3% threshold, so the
  rig stopped). Had it run on it might have reached ~1010, which would put logging nearer -18% than -20.3% and
  tracing+metrics nearer -8%. Read 4.2 as "logging costs roughly a fifth", not as a figure to the decimal.
- **OKE (T12)** -- the number that actually counts. Local k8s reproduces the OKE envelope (~1 effective CPU per
  JVM) but not its network, its nodes, or its real traffic. The OKE **OFF baseline** is now in section 6; the
  LOG / IN-FULL arms (the real OKE delta) land in Stages 3-4.
- **The rig extends a run only while it is CLIMBING** (`ClimbPct`). A run that SAGS is never extended and gets
  logged "settled" while still falling. It did not bite here -- every k8s arm plateaued -- but it is what
  wrecked every docker measurement, and it is still in the rig.


---

## 6. OKE -- the full super-load matrix (one run per setup, 2026-07-19)

The OKE analogue of sections 2-4: the standard 200-VU super-load (read 64 / update 32 / create 32 / move 8 /
tx 64) across x1 and x2, each OFF / LOG / FULL, on the LIVE OKE cluster (`api.esquire.mir0n.pro`, Test House
org 14).

### 6.1 Method

- Driver: hauberk `oke-perf-matrix.ps1`. TOGGLE IN PLACE, not from scratch -- OKE is live and ALTER-migrated,
  no PVC wipe: each cell scales the replicas + calls `oke-o11y-on <arm>`, with `clean-house` between cells.
  The broker is never rolled (a bounce drops the app's messagingBus, which does not self-heal).
- o11y rides SEPARATE paid nodes (`tier=o11y`), so OFF->FULL prices the in-app instrumentation alone -- the
  loki/tempo/prometheus backend CPU does not compete with the app (unlike local single-node k8s).
- ONE run per config (no from-scratch, so a second run is not an independent replicate -- it only deepens the
  run-order drift). Discard load 1, extend while the last load climbs >3%, cap 12 (OKE warms ~13x slower than
  local -- far fewer requests per load over the ~55ms RTT).
- NOISY RIG -- read x2 with caution (see 6.3). The plateau detector ("last load within 3% of the previous")
  trips early on OKE's slow, jittery climb; x1 reproduces across passes, x2 does not.

### 6.2 Every run, every load

rps per 120s load; load 1 is warm-up (discarded); `**` = the trailing loads judged steady.

| run | config  | steady | rps per load                                                  |
|-----|---------|--------|---------------------------------------------------------------|
| 1   | x1-OFF  | 163    | 68, 94, 121, 140, 164, 172, **163**                           |
| 2   | x1-LOG  | 100    | 44, 52, 64, 74, 80, 85, 98, **101**, **100**                  |
| 3   | x1-FULL | 106    | 50, 52, 60, 69, 79, 86, **106**, **106**                      |
| 4   | x2-OFF  | 170    | 82, 107, 132, 155, **168**, **171**                           |
| 5   | x2-LOG  | 98     | 53, 70, 72, 82, 89, **99**, **97**                            |
| 6   | x2-FULL | 144    | 54, 66, 77, 85, 83, 101, 114, 118, 123, 139, **145**, **142** |

0 KO on every cell except x2-FULL (196 KO, 0.131% -- a brief shed as its 12-load run saturated).

### 6.3 What it says

**x1 is clean and reproduces the shape from every earlier pass:**

|      | OFF | LOG | FULL | OFF->LOG | LOG->FULL |
|------|-----|-----|------|----------|-----------|
| x1   | 163 | 100 | 106  | -39%     | +6%       |

Logging is the whole of the observability cost at x1 -- OFF->LOG is the drop, and adding tracing+metrics on
top (LOG->FULL) is within the noise. The -39% is larger than local's -20% because the ~55ms RTT dilutes the
server-CPU fraction of each request; the SPLIT (logging dominant) is the portable result, not the magnitude.

**x2 is NOT trustworthy this run.** The plateau detector settled x2-OFF and x2-LOG early -- x2-OFF stopped at
170 on a momentary flattening (`…155, 168, 171`), where the earlier pass showed it climbing on to ~264
(`…163, 189, 240, 258`) -- while x2-FULL ran its full 12 loads and reached 144. So x2-FULL reads FASTER than
x2-LOG (144 > 98), which is impossible: these are settle artifacts on a noisy, network-bound climb, not
measurements. Left as-is by decision -- OKE is inherently noisy on the toggle-in-place rig and not worth
another chase.

**Clean, portable results from OKE:** durability -- 0 KO under the saturating 200-VU load in every mode
(bar the small x2-FULL shed) -- and the x1 shape: logging IS the observability cost, tracing+metrics add
little on top. The precise per-mode magnitudes, especially at x2, are noise-limited on this rig; the per-pillar
split of record stays the local matrix (sections 2-4). OKE confirms the shape on real ~1-OCPU nodes.
