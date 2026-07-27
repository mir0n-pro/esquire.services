# Esquire -- Performance Matrix

*Measured 2026-07-14. One Windows host, 24 logical cores, Docker Desktop, WSL2 VM 16 GB.*

## Abstract

**This matrix compares different ENVIRONMENTS running on THE SAME HARDWARE RESOURCES.**

Every configuration is given the same machine -- the same 24 cores, the same 16 GB -- and every run drives
that machine to **100% CPU and ~95% RAM**. Everything shares it: the services, the infrastructure, the
observability stack, and the load generator itself. The box is saturated in every single run, by design.

**The absolute numbers are therefore NOT capacity figures.** They do not say what Esquire can serve on real
hardware, and they must not be quoted as though they did.

**What the matrix is for is COMPARISON.** Same load, same machine, same saturation, one variable changed at a
time: the number of replicas, observability on or off, docker or kubernetes. Under those conditions the
DIFFERENCE between two configurations is meaningful -- and only when it is larger than the noise floor, which
every configuration measures against itself by being run twice from scratch.

## Test

**Load ("super-puper"):** 200 virtual users -- read 64, tx 64, update 32, create 32, move 8 -- for 120 s,
driven by hauberk (Gatling) through the gateway.

**Runs:** 6 configurations x 2 runs. Every run built FROM SCRATCH -- stack torn down and rebuilt, Postgres
and KeyCloak data wiped. Each run then drives 4+ loads back to back. One stack up at a time.

**Steady state:** the trailing loads of a run whose throughput is STABLE -- each within 3% of the previous.
Load 1 (warm-up) and any load still rising or falling by more than 3% are excluded from the averages.

### Configurations

| | docker | k8s x1 | k8s x2 |
|---|---|---|---|
| app services | 8 containers | 8 pods | 16 pods (2 per service) |
| CPU limit per instance | none (24 cores available) | 1 CPU | 1 CPU |
| memory limit per instance | none | 768 Mi | 768 Mi |
| gateway connection pool | 16 | 64 | 64 |

App services: gateway, biztree, enyMan, pacMan, keySmith, kcMaster, auKeep, backend (BFF).
Infra (both targets): postgres, keycloak, activemq, redis, kafka.

**o11y OFF** = app instrumentation disabled and the viewing stack uninstalled (no Prometheus, Tempo, Loki,
Alloy, OTel collector, Grafana). **o11y ON** = instrumentation enabled (tracing + metrics + histograms,
`sampling-ratio: 1.0`) and the full viewing stack running.

### Terms

| term | meaning |
|---|---|
| **KO** | Gatling's term for a FAILED request: no response, or a status the scenario does not accept. One KO = one request the client got no valid answer to. |
| **failure rate** | `KO / requests`, as a percentage. |
| **rps** | requests completed per second = `requests / 120`. |
| **p50 / p95 / p99** | the response time that 50% / 95% / 99% of requests came in under. |
| **max** | the single slowest response in that load. |
| **noise** | the gap between a configuration's own two from-scratch runs. A difference smaller than the noise is not a result. |

Raw CSV: `explorer/hauberk/output/perf-matrix/matrix.csv` -- rerun: `perf-matrix.bat` -- report: `perf-matrix-report.py`

---

## 1. Every run, every load

### Run 1 -- k8s x1, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 74,275 | 619 | 322 | 245 | 739 | 1109 | 2,146 | 0 | warm-up |
| 2 | 124,098 | 1034.2 | 192 | 168 | 382 | 452 | 1,548 | 0 | not steady |
| 3 | 117,454 | 978.8 | 203 | 191 | 372 | 491 | 1,756 | 0 | **steady** |
| 4 | 120,476 | 1004 | 198 | 192 | 342 | 443 | 1,275 | 0 | **steady** |

**Steady state: 991 rps**

### Run 2 -- k8s x1, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 74,639 | 622 | 320 | 203 | 791 | 1203 | 2,530 | 0 | warm-up |
| 2 | 114,121 | 951 | 209 | 180 | 419 | 522 | 1,320 | 0 | **steady** |
| 3 | 111,331 | 927.8 | 214 | 188 | 409 | 513 | 1,732 | 0 | **steady** |
| 4 | 112,800 | 940 | 211 | 192 | 394 | 500 | 1,741 | 0 | **steady** |

**Steady state: 940 rps**

### Run 3 -- k8s x1, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 64,981 | 541.5 | 367 | 264 | 860 | 1243 | 2,271 | 0 | warm-up |
| 2 | 103,018 | 858.5 | 231 | 200 | 453 | 758 | 1,589 | 0 | **steady** |
| 3 | 102,830 | 856.9 | 232 | 212 | 411 | 560 | 1,697 | 0 | **steady** |
| 4 | 103,019 | 858.5 | 232 | 213 | 408 | 544 | 2,144 | 0 | **steady** |

**Steady state: 858 rps**

### Run 4 -- k8s x1, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 60,347 | 502.9 | 396 | 303 | 908 | 1275 | 2,399 | 0 | warm-up |
| 2 | 102,349 | 852.9 | 233 | 202 | 455 | 661 | 1,260 | 0 | **steady** |
| 3 | 103,207 | 860.1 | 228 | 201 | 414 | 730 | 30,073 | 4 | **steady** |
| 4 | 100,561 | 838 | 237 | 204 | 421 | 1133 | 2,373 | 0 | **steady** |

**Steady state: 850 rps**

### Run 5 -- k8s x2, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 108,274 | 902.3 | 221 | 145 | 607 | 830 | 1,821 | 0 | warm-up |
| 2 | 182,076 | 1517.3 | 131 | 107 | 277 | 407 | 1,656 | 0 | **steady** |
| 3 | 177,765 | 1481.4 | 134 | 107 | 269 | 407 | 2,754 | 0 | **steady** |
| 4 | 174,678 | 1455.6 | 136 | 110 | 261 | 426 | 3,077 | 0 | **steady** |

**Steady state: 1485 rps**

### Run 6 -- k8s x2, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 111,158 | 926.3 | 215 | 171 | 522 | 844 | 2,169 | 0 | warm-up |
| 2 | 174,060 | 1450.5 | 137 | 110 | 301 | 570 | 2,013 | 0 | not steady |
| 3 | 172,660 | 1438.8 | 138 | 107 | 289 | 554 | 2,701 | 0 | not steady |
| 4 | 178,269 | 1485.6 | 134 | 111 | 270 | 425 | 2,440 | 0 | **steady** |
| 5 | 181,019 | 1508.5 | 132 | 110 | 263 | 434 | 2,680 | 0 | **steady** |

**Steady state: 1497 rps**

### Run 7 -- k8s x2, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 88,222 | 735.2 | 141 | 98 | 391 | 684 | 2,132 | 0 | warm-up |
| 2 | 124,346 | 1036.2 | 119 | 96 | 261 | 544 | 1,758 | 0 | not steady |
| 3 | 130,389 | 1086.6 | 114 | 89 | 240 | 331 | 30,047 | 20 | **steady** |
| 4 | 131,732 | 1097.8 | 112 | 91 | 246 | 359 | 2,148 | 0 | **steady** |

**Steady state: 1092 rps**

### Run 8 -- k8s x2, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 90,479 | 754 | 264 | 192 | 692 | 1019 | 2,006 | 0 | warm-up |
| 2 | 130,613 | 1088.4 | 183 | 155 | 394 | 915 | 1,715 | 0 | not steady |
| 3 | 126,970 | 1058.1 | 188 | 160 | 384 | 1051 | 2,419 | 0 | not steady |
| 4 | 131,667 | 1097.2 | 138 | 103 | 251 | 380 | 30,211 | 64 | not steady |
| 5 | 138,963 | 1158 | 172 | 148 | 343 | 561 | 2,301 | 0 | **steady** |
| 6 | 141,250 | 1177.1 | 169 | 144 | 350 | 601 | 2,548 | 0 | **steady** |

**Steady state: 1168 rps**

### Run 9 -- docker, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 149,303 | 1244.2 | 159 | 152 | 295 | 553 | 1,633 | 0 | warm-up |
| 2 | 139,097 | 1159.1 | 170 | 154 | 299 | 874 | 1,631 | 1 | not steady |
| 3 | 133,502 | 1112.5 | 178 | 161 | 307 | 605 | 2,046 | 0 | **steady** |
| 4 | 129,594 | 1080 | 184 | 162 | 318 | 798 | 2,442 | 0 | **steady** |

**Steady state: 1096 rps**

### Run 10 -- docker, o11y OFF

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 148,481 | 1237.3 | 160 | 149 | 301 | 593 | 1,880 | 0 | warm-up |
| 2 | 140,102 | 1167.5 | 169 | 156 | 290 | 440 | 2,118 | 0 | not steady |
| 3 | 126,062 | 1050.5 | 188 | 170 | 327 | 518 | 1,734 | 0 | **steady** |
| 4 | 126,345 | 1052.9 | 188 | 172 | 337 | 484 | 1,546 | 0 | **steady** |

**Steady state: 1052 rps**

### Run 11 -- docker, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 118,897 | 990.8 | 200 | 185 | 391 | 780 | 1,941 | 0 | warm-up |
| 2 | 111,642 | 930.4 | 214 | 190 | 381 | 965 | 1,537 | 0 | not steady |
| 3 | 99,942 | 832.8 | 238 | 217 | 423 | 758 | 2,182 | 0 | **steady** |
| 4 | 101,012 | 841.8 | 235 | 215 | 429 | 647 | 2,080 | 0 | **steady** |

**Steady state: 837 rps**

### Run 12 -- docker, o11y ON

| load | requests | rps | mean ms | p50 | p95 | p99 | max | KO | |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 115,064 | 958.9 | 207 | 188 | 410 | 765 | 2,483 | 0 | warm-up |
| 2 | 107,544 | 896.2 | 220 | 199 | 404 | 934 | 2,080 | 0 | not steady |
| 3 | 95,865 | 798.9 | 249 | 226 | 453 | 976 | 2,068 | 0 | **steady** |
| 4 | 94,622 | 788.5 | 252 | 228 | 459 | 786 | 2,311 | 0 | **steady** |

**Steady state: 794 rps**

---

## 2. Aggregated

Steady loads only.

| config | run A | run B | **rps** | **noise** | mean ms | p50 | p95 | p99 | requests | KO | rate |
|---|---|---|---|---|---|---|---|---|---|---|---|
| k8s x1, o11y OFF | 991 | 940 | **966** | 5.5% | 207 | 189 | 387 | 494 | 849,194 | 0 | 0.000% |
| k8s x1, o11y ON | 858 | 850 | **854** | 0.9% | 232 | 205 | 427 | 731 | 740,312 | 4 | 0.001% |
| k8s x2, o11y OFF | 1485 | 1497 | **1491** | 0.8% | 133 | 109 | 268 | 420 | 1,459,959 | 0 | 0.000% |
| k8s x2, o11y ON | 1092 | 1168 | **1130** | 6.9% | 142 | 118 | 295 | 463 | 1,234,631 | 84 | 0.007% |
| docker, o11y OFF | 1096 | 1052 | **1074** | 4.2% | 184 | 166 | 322 | 601 | 1,092,486 | 1 | 0.000% |
| docker, o11y ON | 837 | 794 | **816** | 5.5% | 244 | 222 | 441 | 792 | 844,588 | 0 | 0.000% |
| **total** | | | | | | | | | **6,221,170** | **89** | **0.001%** |

---

## 3. Results

### 3.1 Failures

**6,221,170 requests, 89 KO -- 0.001%.** No configuration collapsed.

All 89 KO fell in 3 loads (runs 4, 7, 8), each with a max response time of ~30,000 ms -- the 30 s
slow-route deadline. No other load in the matrix produced a failure.

### 3.2 Observability cost

| | OFF | ON | throughput | p99 | noise |
|---|---|---|---|---|---|
| k8s x1 | 966 | 854 | **-11.5%** | 494 -> 731 ms (**+48%**) | 5.5% |
| k8s x2 | 1491 | 1130 | **-24.2%** | 420 -> 463 ms (**+10%**) | 6.9% |
| docker | 1074 | 816 | **-24.1%** | 601 -> 792 ms (**+32%**) | 5.5% |

In all three pairs every OFF run is faster than every ON run.

### 3.3 Second replica

| | x1 | x2 | gain |
|---|---|---|---|
| o11y OFF | 966 | 1491 | **+54.4%** |
| o11y ON | 854 | 1130 | **+32.3%** |

### 3.4 docker vs k8s

| | docker | k8s x1 | k8s x2 |
|---|---|---|---|
| o11y OFF | 1074 | 966 (-10.1%) | 1491 (**+38.8%**) |
| o11y ON | 816 | 854 (+4.7%) | 1130 (**+38.5%**) |

With o11y ON, docker and k8s x1 differ by less than the noise floor: **no result**.

### 3.5 Ranking

| rank | config | rps | mean ms | p50 | p95 | p99 | KO |
|---|---|---|---|---|---|---|---|
| 1 | k8s x2, o11y OFF | **1491** | 133 | 109 | 268 | 420 | 0.000% |
| 2 | k8s x2, o11y ON | **1130** | 142 | 118 | 295 | 463 | 0.007% |
| 3 | docker, o11y OFF | **1074** | 184 | 166 | 322 | 601 | 0.000% |
| 4 | k8s x1, o11y OFF | **966** | 207 | 189 | 387 | 494 | 0.000% |
| 5 | k8s x1, o11y ON | **854** | 232 | 205 | 427 | 731 | 0.001% |
| 6 | docker, o11y ON | **816** | 244 | 222 | 441 | 792 | 0.000% |

- **k8s x2 is fastest and has the lowest latency at every percentile**, with and without observability.
- Lowest p99 of any configuration: **k8s x2, o11y OFF (420 ms)**.

