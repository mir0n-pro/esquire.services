# ===========================================================================================
#  Esquire Haubergeon -- PERFORMANCE MATRIX
#
#  Drives super-load across every deployment shape and writes one CSV you can compare.
#
#      k8s     x1  o11y OFF / LOG / FULL
#      k8s     x2  o11y OFF / LOG / FULL
#      docker      OFF / LOG / ON        (SMOKE TEST ONLY -- uncapped, its sag biases an o11y delta; see $CONFIGS)
#
#  o11y has THREE modes (I49): OFF (all logging off, no stack) / LOG (app logging only) / FULL (logging +
#  tracing + metrics). Only pro.mir0n moves between them, so the modes ADD UP. -Runs sets runs per config,
#  -Scale scales the VU count, -Only runs a subset. Each run is FROM SCRATCH, driving several back-to-back loads.
#
#  Entry point: perf-matrix.bat   (launches this detached so a long matrix survives the console)
#
# -------------------------------------------------------------------------------------------
#  WHY IT IS SHAPED THIS WAY -- every rule below was paid for with a wrong answer.
#
#  FROM SCRATCH, INCLUDING THE DATA.  A run tears the environment down and rebuilds it, and that
#  MUST drop the Postgres + KeyCloak + broker state. k8s-down.bat does NOT delete the PVCs and
#  `docker compose down` does not drop the volume, so a naive down/up silently keeps the old
#  database -- and the audit rows and deleted entities that accumulate there dragged throughput
#  down ~8% per run. With the data wiped, repeat runs agree to ~1-2%; without it, they drifted by
#  13-17% and NO effect smaller than that could be seen at all.
#
#  TWO RUNS PER CONFIG = THE NOISE FLOOR.  The gap between a config's own two from-scratch runs is
#  the smallest difference this rig can honestly resolve. Any config-to-config difference smaller
#  than that is not a finding. (An earlier A/B "showed" a 13% observability cost that turned out
#  to be nothing but run ordering.) Report the noise next to every conclusion.
#
#  DISCARD LOAD 1.  The first load after a rebuild is warm-up -- cold JIT, empty caches, cold
#  pools. It runs 35-45% below steady state, every single time. It is not a baseline and it is not
#  degradation; averaging it in poisons the number. Steady state = loads 2..N.
#
#  ADAPTIVE LOAD COUNT.  4 loads minimum, but keep going while the last load is still CLIMBING
#  more than $ClimbPct on the previous -- a rising last load means the fleet has not finished
#  warming (x2 has twice the JVMs to JIT), and averaging a climb UNDERSTATES the config. Capped at
#  $MaxLoads; hitting the cap while still climbing is logged loudly, because it means steady state
#  was never reached and the number is not trustworthy.
#
#  OFF MEANS OFF.  k8s-up.bat installs the observability viewing stack as part of a normal bring-up,
#  so "o11y OFF" is NOT off unless o11y-off.bat uninstalls it. Leave it running and the ON-vs-OFF
#  delta prices only the in-process half of the bill. (On k8s the collectors alone cost ~8%.)
#
#  STRICTLY SEQUENTIAL -- ONE STACK AT A TIME.  The only parallelism permitted in the whole matrix
#  is Gatling's virtual users. Running docker and k8s together on one machine is what invalidated
#  an entire first day of measurements: docker looked 5x slower purely because the cluster was
#  sharing the box.
#
#  RESUMABLE.  Results are appended after EVERY load, and a run already present in the CSV is
#  skipped. A matrix takes hours; an interruption at run 11 must not cost runs 1-10.
# ===========================================================================================

param(
    [string] $OutDir     = "$PSScriptRoot\output\perf-matrix",
    [int]    $Duration   = 120,      # seconds per load
    [int]    $MinLoads   = 4,
    [int]    $MaxLoads   = 6,
    [double] $ClimbPct   = 3.0,      # extend while the last load is >this% above the previous
    [int]    $Runs       = 2,        # runs per config (2 = the noise floor)
    [switch] $Fresh,                 # start a new CSV instead of resuming the existing one
    [string[]] $Only     = @(),      # run ONLY these config names (empty = all of $CONFIGS)
    [double] $Scale      = 1.0,      # scale every scenario's VU count (1.0 = the standard 200-VU super-load)
    [string[]] $Kinds    = @(),      # ONE KIND PER CELL: tree read readacct update tx (empty = the mixed super-load)
    [int]    $KindVus    = 64,       # VU count for the single scenario of a -Kinds cell
    [int]    $PrepDepth  = 5,
    [int]    $PrepClients= 5,
    [int]    $PrepAccounts = 2,
    [int]    $MetricsVus  = 0,       # >0 adds a LIGHT capture cycle per kind (--metrics): the per-leg timings
    [int]    $MetricsDuration = 60,
    [int]    $PoolMax    = 0,         # >0 sets BIZTREE_H2_POOL_MAX on the workload that holds the tree
                                      # cache -- gateWard in compact, bizTree in classic. The cache-read
                                      # scheduler sizes itself from this pool, so it is the one knob that
                                      # widens both the connections and the threads waiting on them.
    [switch] $ServiceMetrics          # set GW_SERVICE_METRICS_ENABLED on the gate after each build:
                                      # it only adds a header to the downstream request, and the per-leg
                                      # timings are stamped only for a request that carries the client's
                                      # X-Capture-Metrics -- so a plateau cycle pays nothing for it
)
# NOTE $Only, never $Configs: PowerShell variable names are CASE-INSENSITIVE, so a param named $Configs
# IS $CONFIGS -- it would silently overwrite the matrix definition with a list of strings and select nothing.

$ErrorActionPreference = "Continue"
$SVCS    = "C:\MyProjects\esquire\services"
$HAUBERK = $PSScriptRoot
$APPS    = @("gateway","biztree","enyman","pacman","keysmith","kcmaster","aukeep","backend")
# The COMPACT fleet: gateWard carries the gate and the tree cache, Mesnie carries enyMan, keySmith and the
# identity work. Four workloads instead of eight, and they are named differently: the compact charts name the
# workload {{ .Release.Name }} ("esquire-mesnie"), the classic ones {{ .Release.Name }}-<svc>
# ("esquire-enyman-enyman"). Wl() below is the one place that knows which.
$APPS_COMPACT = @("gateward","mesnie","pacman","aukeep","backend")

# The super-puper load: 200 virtual users across the five scenarios.
# The standard super-load: 200 VUs as read 64 / update 32 / create 32 / move 8 / tx 64.
# -Scale shrinks every scenario in proportion, for finding a load the HOST can actually hold flat.
# WHY IT EXISTS: k8s plateaus dead flat at 200 VUs (573,1045,1034,1046) because every pod is capped at 1 CPU.
# docker is UNCAPPED -- it grabs all 24 cores, saturates the box, and Windows throttles, so it SAGS. Worse,
# the sag scales WITH throughput: the faster arm saturates harder and sags harder, so the decline eats part of
# the very delta being measured. That is a BIAS, not noise, and no amount of averaging removes it.
$vus = @{ read = 64; update = 32; create = 32; move = 8; tx = 64 }
$scaled = @{}
foreach ($k in @($vus.Keys)) { $scaled[$k] = [Math]::Max(1, [int][Math]::Round($vus[$k] * $Scale)) }
$LOAD = @("--duration","$Duration",
          "--read","$($scaled.read)","--update","$($scaled.update)","--create","$($scaled.create)",
          "--move","$($scaled.move)","--tx","$($scaled.tx)")
if ($Scale -ne 1.0) {
    Log ("### LOAD SCALED x{0} -> read {1} / update {2} / create {3} / move {4} / tx {5} = {6} VUs" -f `
         $Scale, $scaled.read, $scaled.update, $scaled.create, $scaled.move, $scaled.tx,
         ($scaled.Values | Measure-Object -Sum).Sum)
}

# The matrix. Order groups k8s first so docker only needs the cluster torn down once.
# THREE ARMS since I49 (mir0n):
#
#   OFF  app logger OFF (levelMir0n=OFF), no viewing stack, no tracing/metrics   <- the baseline
#   LOG  app logger INFO + loki/alloy/grafana ONLY, tracing/metrics off          <- the log pillar alone
#   ON   everything (tracing + metrics + shipping)
#
# THE KNOB IS levelMir0n, NEVER levelRoot (mir0n). `pro.mir0n` carries its own level in logback-spring.xml and
# no appender of its own, so its events reach the root's ECS CONSOLE appender by ADDITIVITY -- an ancestor's
# LEVEL is never re-checked on the way. levelRoot therefore gates only third-party libraries and CANNOT silence
# the application. THE 07-16 RUN TURNED ROOT AND IS VOID: the app logged identically in both arms, cancelled out
# of the delta, and the resulting "-4.7%" was the price of third-party libs plus loki/alloy/grafana -- not of
# logging. Those load runs must be REDONE with this knob.
#
# T10 (doc/review/Esquire.PerfMatrix-07-14.md) is SOUND and is NOT superseded: the app logged at DEBUG in both
# its arms, and its ON arm really did ship those lines to Loki while its OFF arm uninstalled Alloy/Loki. So its
# -11.5% / -24% IS the full o11y cost, all three pillars. What T10 lacks is only the per-pillar SPLIT -- which
# is the whole of I49's job.
# devLog/msgLog (DEBUG/INFO, to FILES) are IDENTICAL in every arm and cancel out by design -- the only thing that
# moves between OFF and LOG is the logging o11y stack.
# DOCKER IS NOT AN INSTRUMENT FOR o11y COST -- it is a smoke test (mir0n, 2026-07-17). Do not re-add it to an
# o11y measurement, and do not "fix" it with a lower load. See doc/review/Esquire.PerfMatrix-07-14.md Part III.
#   k8s plateaus DEAD FLAT (573,1045,1034,1046 = 0.0% drift) because every pod is capped at 1 CPU: it draws
#   ~1045 rps and leaves the box headroom. docker is UNCAPPED -- it takes all 24 cores, saturates the host
#   (services + infra + o11y stack + Gatling in one 16 GB WSL2 VM) and Windows throttles, so it SAGS.
#   THE SAG SCALES WITH THROUGHPUT: the all-logs-off arm was the fastest ever measured here (2243 rps) and
#   sagged the HARDEST (-44%); slower arms sag ~11%. Throughput IS what an o11y comparison measures, so the
#   decline eats the delta, always in the same direction. That is a BIAS. Two runs do not cancel it; averaging
#   does not cancel it.
#   AND the 200-VU super-load runs docker PAST ITS KNEE: 100 VUs gives the SAME 2225 rps at HALF the p99
#   (235 vs 533 ms). The extra users buy queueing, not work. Nothing measured past the knee is a measurement.
#   OKE caps app services at ~750m on ~1-OCPU nodes = the same effective 1 CPU local k8s reproduces. docker,
#   the only place a JVM sees 24 cores, resembles nothing we deploy. k8s "slower" than docker is that budget.
# THE THREE MODES (mir0n, 2026-07-17) = OFF / LOG / FULL. They ADD UP because only ONE thing ever moves --
# pro.mir0n -- while develop/msg/amq/jms are OFF and root sits at its ERROR default in EVERY arm:
#   OFF -> LOG   the log pillar alone        LOG -> FULL  tracing + metrics alone
#   OFF -> FULL  the whole observability bill
# "ON" is a FOURTH, different thing: the stack AS SHIPPED, pro.mir0n at its DEBUG chart default. It is what
# T10 measured (12%/24%, sound). Keep it to reproduce T10 -- never mix it into the three-mode arithmetic.
$CONFIGS = @(
    @{ name = "k8s-x1-OFF";  target = "k8s";    reps = 1; o11y = "OFF"  },
    @{ name = "k8s-x1-LOG";  target = "k8s";    reps = 1; o11y = "LOG"  },
    @{ name = "k8s-x1-FULL"; target = "k8s";    reps = 1; o11y = "FULL" },
    @{ name = "k8s-x1-ON";   target = "k8s";    reps = 1; o11y = "ON"   },
    @{ name = "k8s-x2-OFF";  target = "k8s";    reps = 2; o11y = "OFF"  },
    @{ name = "k8s-x2-LOG";  target = "k8s";    reps = 2; o11y = "LOG"  },
    @{ name = "k8s-x2-FULL"; target = "k8s";    reps = 2; o11y = "FULL" },
    @{ name = "k8s-x2-ON";   target = "k8s";    reps = 2; o11y = "ON"   },
    # docker: smoke test only -- NEVER part of an o11y measurement (see the note above).
    # COMPACT on local k8s -- the same six arms against the composed fleet. `dir` is the stack folder, and it is
    # the only thing that forks: same charts layout, same ingress hosts, same o11y arm scripts.
    @{ name = "k8sc-x1-OFF";  target = "k8sc"; dir = "k8s-compact"; reps = 1; o11y = "OFF"  },
    @{ name = "k8sc-x1-LOG";  target = "k8sc"; dir = "k8s-compact"; reps = 1; o11y = "LOG"  },
    @{ name = "k8sc-x1-FULL"; target = "k8sc"; dir = "k8s-compact"; reps = 1; o11y = "FULL" },
    @{ name = "k8sc-x2-OFF";  target = "k8sc"; dir = "k8s-compact"; reps = 2; o11y = "OFF"  },
    @{ name = "k8sc-x2-LOG";  target = "k8sc"; dir = "k8s-compact"; reps = 2; o11y = "LOG"  },
    @{ name = "k8sc-x2-FULL"; target = "k8sc"; dir = "k8s-compact"; reps = 2; o11y = "FULL" },

    @{ name = "docker-OFF";  target = "docker"; reps = 1; o11y = "OFF" },
    @{ name = "docker-LOG";  target = "docker"; reps = 1; o11y = "LOG" },
    @{ name = "docker-ON";   target = "docker"; reps = 1; o11y = "ON"  }
)

# ONE KIND PER CELL (-Kinds). Each base config becomes one cell per kind, named "<config>-<kind>", and the
# cell drives that scenario ALONE at -KindVus. The mixed super-load cannot answer a per-operation question:
# its scenarios share the box, so an operation that speeds up takes capacity from the others and every row
# moves at once. A cell per kind is the only way a single operation's number means that operation.
$KIND_NAMES = @("tree","treesmall","cache","cachesmall","read","readacct","update","create","move","tx")
if ($Kinds.Count -gt 0) {
    $typo = $Kinds | Where-Object { $KIND_NAMES -notcontains $_ }
    if ($typo) { throw "unknown kind(s): $($typo -join ', ') -- known: $($KIND_NAMES -join ', ')" }
}

# The full matrix order, captured BEFORE any -Configs filter: run numbers are assigned from a config's slot
# here, so a subset keeps the same numbers it would have had in a full matrix.
$ALL_CONFIG_NAMES = [System.Collections.ArrayList]@($CONFIGS | ForEach-Object { $_.name })

# -Only runs a SUBSET by name (the k8s arms were stopped part-way on 07-16 and only the docker arms were
# wanted; without this the resume would have re-run them). An unknown name is a typo, not a request to run
# nothing -- fail loudly rather than silently skip the whole matrix.
if ($Only.Count -gt 0) {
    $known = $ALL_CONFIG_NAMES
    $typo  = $Only | Where-Object { $known -notcontains $_ }
    if ($typo) { throw "unknown config name(s): $($typo -join ', ') -- known: $($known -join ', ')" }
    # @() is REQUIRED: a Where-Object that matches ONE config returns the hashtable itself, not an array,
    # and $CONFIGS.Count then reports the hashtable's KEY count (4) instead of 1. The loop still runs
    # correctly -- only the log lies -- which is exactly the kind of false alarm that gets a good run killed.
    $CONFIGS = @($CONFIGS | Where-Object { $Only -contains $_.name })
}

# $OutDir MUST be absolute. The script Set-Locations into services\k8s and services\compose as it works, so a
# RELATIVE -OutDir resolves against whatever the CWD happens to be at that moment: Add-Content then targets a
# directory that does not exist, the write FAILS, and the log line is LOST WITHOUT A TRACE. That is how the
# 07-17 run lost every RUN header, every build step and the infra-removal line while its CSV stayed perfect --
# a log that lies by omission is worse than no log, because a missing step reads exactly like a step not taken.
if (-not [System.IO.Path]::IsPathRooted($OutDir)) { $OutDir = Join-Path $PSScriptRoot $OutDir }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$CSV = "$OutDir\matrix.csv"
$LOG = "$OutDir\matrix.log"
if ($Fresh) { Remove-Item $CSV,$LOG -ErrorAction SilentlyContinue }
if (-not (Test-Path $CSV)) {
    "run,config,target,replicas,o11y,load,requests,ok,ko,ko_pct,rps,mean_ms,p50_ms,p75_ms,p95_ms,p99_ms,max_ms" |
        Out-File -FilePath $CSV -Encoding utf8
}

function Log($m) {
    $line = "[$((Get-Date).ToString('HH:mm:ss'))] $m"
    $line | Out-File -FilePath $LOG -Encoding utf8 -Append
    # Write-HOST, not Write-Output. Write-Output puts the line on the PIPELINE, so any function that
    # calls Log AND returns a value returns BOTH -- the caller then gets @("[12:33] load 4 ...", 1080)
    # and the arithmetic on it blows up ("Cannot convert ... to System.Double"). That silently killed
    # the adaptive load extension: the climb check threw, so no run ever extended past MinLoads.
    Write-Host $line
}

# ---- parse one Gatling global-stats block ------------------------------------------------
function Save-Result($out, $run, $cfg, $loadNo, $secs = 0) {
    if ($secs -le 0) { $secs = $Duration }
    $hit = @($out | Select-String "request count")
    if ($hit.Count -eq 0) { Log "    !! NO STATS (run $run load $loadNo) -- did the token fetch fail?"; return $null }
    $all   = [regex]::Matches($hit[0].ToString(), '\|\s*([\d,\-]+)')
    $total = [double](($all[0].Groups[1].Value) -replace ',','')
    $ok    = if ($all.Count -gt 1) { ($all[1].Groups[1].Value) -replace ',','' } else { "" }
    $ko    = if ($all.Count -gt 2) { ($all[2].Groups[1].Value) -replace ',','' } else { "0" }
    function Col($pat) {
        $m = @($out | Select-String $pat)
        if ($m.Count -eq 0) { return "" }
        $c = [regex]::Matches($m[0].ToString(), '\|\s*([\d,\-]+)')
        if ($c.Count -lt 1) { return "" }
        return ($c[0].Groups[1].Value) -replace ',',''
    }
    $rps   = [math]::Round($total / $secs, 1)
    $koPct = if ($total -gt 0) { [math]::Round(100.0 * [double]$ko / $total, 3) } else { 0 }
    "$run,$($cfg.name),$($cfg.target),$($cfg.reps),$($cfg.o11y),$loadNo,$total,$ok,$ko,$koPct,$rps,$(Col 'mean response time'),$(Col '50th percentile'),$(Col '75th percentile'),$(Col '95th percentile'),$(Col '99th percentile'),$(Col 'max response time')" |
        Out-File -FilePath $CSV -Encoding utf8 -Append
    Log "    load $loadNo : $total req, $rps rps, p99=$(Col '99th percentile')ms, KO $ko ($koPct%)"
    return $rps
}

function Wait-K8sPods {
    for ($i = 0; $i -lt 200; $i++) {
        $all = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-")
        $bad = @($all | Select-String -NotMatch "1/1\s+Running")
        if ($all.Count -gt 0 -and $bad.Count -eq 0) { return }
        Start-Sleep -Seconds 10
    }
    Log "    !! pods never all became Ready"
}

# UP = the host ANSWERED, not "answered 200". Invoke-WebRequest throws on every non-2xx, so a bare try/catch
# treats a guarded endpoint's 401 as down and waits out the whole budget. What separates a ready gate from an
# unready one is WHICH status comes back:
#   2xx/3xx/4xx -- the gate answered. A 401 on a guarded route is the gate UP and enforcing auth.
#   5xx         -- NOT up: ingress-nginx returns 502/503 when no endpoint is ready behind it.
#   no response -- NOT up: connection refused, DNS, or timeout.
function Wait-Url($url, $tries = 90, [switch] $GuardedOk) {
    for ($i = 0; $i -lt $tries; $i++) {
        try { Invoke-WebRequest -Uri $url -TimeoutSec 4 -UseBasicParsing | Out-Null; return $true }
        catch {
            $code = 0
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            # -GuardedOk is ONLY for a route that answers 401 when it is healthy. It must NOT be used for the
            # realm: KeyCloak answers 404 on that path from the moment it is listening, long before the import
            # finishes, so accepting a 4xx there passes the gate on a realm that does not exist yet and the
            # prepare then finds no token. That is exactly how a run reached its prepare one second after the
            # gate and produced no requests at all.
            if ($GuardedOk -and $code -ge 400 -and $code -lt 500) { return $true }
            Start-Sleep -Seconds 10
        }
    }
    return $false
}

# ---- build a k8s environment FROM SCRATCH -------------------------------------------------
function Wl($cfg, $svc) {
    # The workload name for a service on THIS profile. Compact charts name it after the release alone.
    if ($cfg.dir -eq "k8s-compact") { return "esquire-$svc" }
    return "esquire-$svc-$svc"
}

function Build-K8s($cfg) {
    $dir  = if ($cfg.dir) { $cfg.dir } else { "k8s" }
    $apps = if ($cfg.dir -eq "k8s-compact") { $APPS_COMPACT } else { $APPS }
    Set-Location "$SVCS\$dir"
    Log "  tearing k8s down"
    & ".\k8s-down.bat" *> $null
    for ($i = 0; $i -lt 60; $i++) {
        if (@(kubectl get pods --no-headers 2>$null | Select-String "^esquire-").Count -eq 0) { break }
        Start-Sleep -Seconds 5
    }
    # k8s-down.bat leaves the PVCs. Without this the "from scratch" run keeps the old database.
    Log "  dropping PVCs (Postgres + KeyCloak + broker store)"
    kubectl delete pvc postgres-data-esquire-infra-postgres-0     --ignore-not-found *> $null
    kubectl delete pvc keycloak-data-esquire-infra-kc-keycloak-0  --ignore-not-found *> $null
    kubectl delete pvc activemq-data-esquire-infra-amq-activemq-0 --ignore-not-found *> $null

    Log "  bringing k8s up (fresh PG seed + KC realm import)"
    & ".\k8s-up.bat" *> $null

    # REQUIRED INFRA ONLY (mir0n, 2026-07-17). k8s-up.bat installs the full dev cluster; a measurement wants
    # nothing on the box that the measurement does not use -- idle components still hold RAM and take CPU on a
    # host where EVERYTHING (services + infra + o11y + Gatling) shares one 16 GB WSL2 VM.
    #   kafka -- ALWAYS out. Nothing references it: the audit sink is audit-c (AMQ -> auKeep); audit-ck/dk
    #            are not in play.
    #   redis -- out at x1 ONLY (mir0n). It is the BFF's SHARED SESSION STORE across replicas, so at x2 it is
    #            REQUIRED -- two BFF pods must see each other's logins, and removing it would measure a broken
    #            deployment. At x1 there is nothing to share: the chart omits REDIS_URL and the BFF falls back
    #            to express-session's in-memory MemoryStore (config.ts) -- a SUPPORTED state, and exactly the
    #            OKE 1-replica shape. (hauberk drives the GATEWAY directly either way.)
    & helm uninstall esquire-infra-kafka *> $null
    if ($cfg.reps -eq 1) {
        Log "  required infra only -- removing kafka + redis (x1: BFF uses an in-memory session store)"
        & helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set redis.url="" *> $null
        & kubectl rollout restart statefulset (Wl $cfg "backend") *> $null
        & helm uninstall esquire-infra-redis *> $null
    } else {
        Log "  required infra only -- removing kafka; redis KEPT (x2: the BFF replicas need a shared session store)"
    }

    Wait-K8sPods

    # k8s-up installs the viewing stack, so every arm must explicitly put it into the state it wants.
    # THREE arms (I49) -- and NO fall-through else: an unknown value must FAIL, never quietly run OFF and file
    # the result under another name (that would report the cost of logging as zero, convincingly and wrongly).
    if ($cfg.o11y -eq "FULL") {
        Log "  o11y FULL (IN-FULL: pro.mir0n INFO + tracing + metrics + full viewing stack)"
        & ".\o11y-full-on.bat" *> $null
    } elseif ($cfg.o11y -eq "ON") {
        Log "  o11y ON  (the stack AS SHIPPED: pro.mir0n at its DEBUG default + tracing + metrics)"
        & ".\o11y-on.bat" *> $null
    } elseif ($cfg.o11y -eq "LOG") {
        Log "  o11y LOG (ONLY-LOGGING: pro.mir0n INFO + loki/alloy/grafana; tracing/metrics off)"
        & ".\o11y-log-on.bat" *> $null
    } elseif ($cfg.o11y -eq "OFF") {
        Log "  o11y OFF (no viewing stack; tracing/metrics off; EVERY logger off)"
        & ".\o11y-log-off.bat" *> $null
    } else {
        Log "  !! unknown o11y arm '$($cfg.o11y)' -- refusing to guess"
        return $false
    }
    Wait-K8sPods

    if ($ServiceMetrics) {
        $gw = if ($cfg.dir -eq "k8s-compact") { "gateward" } else { "gateway" }
        Log "  enabling the per-leg duration trace on $(Wl $cfg $gw)"
        & kubectl set env statefulset (Wl $cfg $gw) GW_SERVICE_METRICS_ENABLED=true *> $null
        & kubectl rollout status statefulset (Wl $cfg $gw) --timeout=300s *> $null
        Wait-K8sPods
    }

    if ($PoolMax -gt 0) {
        $holder = if ($cfg.dir -eq "k8s-compact") { "gateward" } else { "biztree" }
        Log "  setting BIZTREE_H2_POOL_MAX=$PoolMax on $(Wl $cfg $holder)"
        & kubectl set env statefulset (Wl $cfg $holder) BIZTREE_H2_POOL_MAX=$PoolMax *> $null
        & kubectl rollout status statefulset (Wl $cfg $holder) --timeout=300s *> $null
        Wait-K8sPods
    }

    if ($cfg.reps -eq 1) {
        Log "  scaling to x1"
        foreach ($s in $apps) { kubectl scale sts (Wl $cfg $s) --replicas=1 *> $null }
        Start-Sleep -Seconds 20
        Wait-K8sPods
    }
    # Both endpoints are ingress-fronted -- no port-forward, which would be a single-threaded proxy
    # AND would die on every KeyCloak rollout, leaving the run with no token and no stats.
    if (-not (Wait-Url "http://esquire.localhost/kc-auth/realms/esquire")) { Log "  !! realm never imported"; return $false }
    # NOT /actuator/health on compact: the actuator sits on the management port (F0 A6) and is deliberately not
    # published through the Service or the ingress, so it 404s there. A guarded route is the honest probe --
    # 401 means the gate is up, routing, and enforcing auth.
    $gateUrl = if ($cfg.dir -eq "k8s-compact") { "http://api.esquire.localhost/esq-enode" }
               else { "http://api.esquire.localhost/actuator/health" }
    if (-not (Wait-Url $gateUrl -GuardedOk)) { Log "  !! gateway never came UP"; return $false }
    Log "  k8s ready (x$($cfg.reps), o11y=$($cfg.o11y))"
    return $true
}

# ---- build a docker environment FROM SCRATCH ----------------------------------------------
function Build-Docker($cfg) {
    Set-Location "$SVCS\compose"
    Log "  tearing docker down (-v drops the Postgres volume)"
    docker compose --profile o11y down -v *> $null
    if (Test-Path "$SVCS\compose\data\keycloak") {
        Remove-Item "$SVCS\compose\data\keycloak" -Recurse -Force -ErrorAction SilentlyContinue
    }
    # o11y has THREE values now (I49): OFF | ON | LOG.
    #   OFF -- nothing: no tracing, no metrics, no viewing stack, APP LOGGER OFF (levelMir0n=OFF).
    #   ON  -- everything (the T10 lump: tracing + metrics + shipping).
    #   LOG -- the LOG PILLAR ALONE: tracing/metrics OFF, levelMir0n=INFO, loki+alloy+grafana, nothing else.
    # LOG vs OFF is the isolated cost of logging -- the per-pillar SPLIT that T10 never reported (T10 itself
    # is sound; it priced all three pillars as one lump). The knob is levelMir0n, never levelRoot.
    # devLog/msgLog are IDENTICAL in every arm (DEBUG/INFO, to files) -- they cancel out of the delta by
    # design (mir0n): the only thing that moves is the logging o11y stack.
    # LOG_LEVEL_ROOT is NOT a knob here and never was -- root cannot silence the application (pro.mir0n has its
    # own level and reaches the ECS console by additivity). pro.mir0n is THE knob; every other logger is OFF in
    # every arm, so the only thing that moves is the pillar under test. Root stays at its ERROR default (mir0n).
    $env:LOG_LEVEL_DEVELOP="OFF"; $env:LOG_LEVEL_MSG="OFF"; $env:LOG_LEVEL_AMQ="OFF"; $env:LOG_LEVEL_JMS="OFF"
    if ($cfg.o11y -eq "FULL") {
        $env:ESQ_OBSERVABILITY_ENABLED="true";  $env:ESQ_METRICS_HISTOGRAMS="true";  $env:LOG_LEVEL_MIR0N="INFO"
    } elseif ($cfg.o11y -eq "ON") {
        $env:ESQ_OBSERVABILITY_ENABLED="true";  $env:ESQ_METRICS_HISTOGRAMS="true";  $env:LOG_LEVEL_MIR0N=$null
    } elseif ($cfg.o11y -eq "LOG") {
        $env:ESQ_OBSERVABILITY_ENABLED="false"; $env:ESQ_METRICS_HISTOGRAMS="false"; $env:LOG_LEVEL_MIR0N="INFO"
    } else {
        $env:ESQ_OBSERVABILITY_ENABLED="false"; $env:ESQ_METRICS_HISTOGRAMS="false"; $env:LOG_LEVEL_MIR0N="OFF"
    }
    Log "  bringing docker up (fresh PG seed + KC realm import), o11y=$($cfg.o11y)"
    docker compose up -d *> $null
    if ($cfg.o11y -eq "FULL") { & ".\o11y-full-on.bat" *> $null }
    if ($cfg.o11y -eq "ON")   { & ".\o11y-on.bat"  *> $null }
    if ($cfg.o11y -eq "LOG")  { & ".\o11y-log-on.bat" *> $null }
    if ($cfg.o11y -eq "OFF")  { & ".\o11y-log-off.bat" *> $null }

    # The gateway's health is NOT a readiness gate for the STACK. It comes up in ~10s without
    # touching Postgres, so a database that failed to seed leaves the gateway happily reporting UP
    # while every load then returns no stats at all. (That is exactly what happened: the postgres
    # image had a CRLF init.sh, initdb died, and this gate said "ready" four runs in a row.)
    # Gate on the three things a run actually needs: the DB SEEDED, the realm IMPORTED, the gateway UP.
    $ok = $false
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $orgs = (docker exec esq-postgres psql -U esq2025 -d esq2025 -t -A -c "select count(*) from esq_org;" 2>$null)
            if ([int]$orgs -gt 0) {
                if ((Invoke-RestMethod -Uri "http://localhost:7070/actuator/health" -TimeoutSec 4).status -eq "UP") {
                    Invoke-WebRequest -Uri "http://localhost:8081/kc-auth/realms/esquire" -TimeoutSec 4 -UseBasicParsing | Out-Null
                    $ok = $true; break
                }
            }
        } catch { }
        Start-Sleep -Seconds 10
    }
    if (-not $ok) { Log "  !! docker never became usable (DB seeded? realm imported? gateway UP?)"; return $false }
    Log "  docker ready (o11y=$($cfg.o11y)) -- DB seeded, realm imported, gateway UP"
    return $true
}

# ---- one run: build from scratch -> prepare -> loads until steady --------------------------
function Invoke-Run($run, $cfg) {
    # WHICH ROWS COUNT AS THIS CELL. With one kind per invocation, every kind of a config shares the run
    # number, and the config column carries "<config>-<kind>". Matching the config PREFIX therefore counts a
    # previous kind's rows as this one's and skips the cell -- silently, with a COMPLETE line and no work.
    # Match the exact cell names this invocation is about to produce.
    $cells = @()
    $all   = @()
    if ($Kinds.Count -gt 0) {
        foreach ($k in $Kinds) { $cells += "$($cfg.name)-$k"; $all += "$($cfg.name)-$k"; $all += "$($cfg.name)-$k-legs" }
    } else {
        $cells = @($cfg.name); $all = @($cfg.name)
    }
    if (Test-Path $CSV) {
        # completeness counts the PLATEAU cells only; the discard sweeps the capture rows too, or a repeat
        # would leave the previous attempt's legs row behind next to the new one
        $mine = @(Import-Csv $CSV | Where-Object { $_.run -eq "$run" -and $cells -contains $_.config })
        if ($mine.Count -ge ($MinLoads * $cells.Count)) { Log "======== RUN $run : $($cfg.name) [$($Kinds -join ',')] -- already complete, skipping ========"; return }
        $stale = @(Import-Csv $CSV | Where-Object { $_.run -eq "$run" -and $all -contains $_.config })
        if ($stale.Count -gt 0) {
            @(Import-Csv $CSV | Where-Object { -not ($_.run -eq "$run" -and $all -contains $_.config) }) | Export-Csv -Path $CSV -NoTypeInformation -Encoding utf8
            Log "  (discarded $($stale.Count) rows from an interrupted run $run)"
        }
    }
    Log "======== RUN $run : $($cfg.name)  (from scratch) ========"
    if ($cfg.target -eq "k8s" -or $cfg.target -eq "k8sc") {
        if (-not (Build-K8s $cfg)) { Log "  ABORTING run $run"; return }
        # THE PROFILE FOLLOWS THE SHAPE. k8sc deploys k8s-compact, where the bizTree cache lives inside
        # gateWard -- its director/restart commands drive statefulset/esquire-gateward, not esquire-biztree.
        # Handing the classic profile to a compact run leaves those commands pointing at a deployment that
        # does not exist, and only the resilience arms notice.
        if ($cfg.target -eq "k8sc") { $hcfg = @("--config","hauberk-k8s-compact.properties") }
        else                        { $hcfg = @("--config","hauberk-k8s.properties") }
    } else {
        if (-not (Build-Docker $cfg)) { Log "  ABORTING run $run"; return }
        $hcfg = @()
    }

    Set-Location $HAUBERK
    # CHECK the prepare. It used to be fire-and-forget, and "playground prepared" was logged whether or
    # not it worked -- so a broken environment produced four NO-STATS loads instead of one clear abort.
    # If the playground is not there, no load can mean anything; stop the run here and say why.
    $prep = & .\hauberk.cmd run prepare-for-anything @hcfg --prep-depth $PrepDepth --prep-clients $PrepClients --prep-accounts $PrepAccounts 2>&1
    $prepReq = @($prep | Select-String "request count")
    if ($prepReq.Count -eq 0) {
        Log "  !! PLAYGROUND PREPARE PRODUCED NO REQUESTS -- the environment is not usable (token? gateway? seeded DB?)"
        Log "  ABORTING run $run"
        return
    }
    Log "  playground prepared; loads (min $MinLoads, extend while climbing >$ClimbPct%, max $MaxLoads):"

    # -Kinds: ONE build, one load series per kind, in the order given. Every other scenario is passed an
    # explicit 0, so a cycle measures that operation with the whole fleet to itself. The kinds share a build
    # on purpose -- a rebuild per kind costs four times the wall clock and the reads do not disturb the data;
    # put the writing kinds LAST so what they add cannot reach the read cycles.
    $cycles = @()
    if ($Kinds.Count -gt 0) {
        foreach ($k in $Kinds) {
            $vals = [ordered]@{ read = 0; update = 0; create = 0; move = 0; tx = 0; tree = 0; readacct = 0; treesmall = 0; cache = 0; cachesmall = 0 }
            $vals[[string]$k] = $KindVus
            $one = @("--duration","$Duration")
            foreach ($v in $vals.Keys) { $one += @("--$v", "$($vals[$v])") }
            $cell = $cfg.Clone()
            $cell.name = "$($cfg.name)-$k"
            $cycles += ,@($cell, $one)
        }
    } else {
        $cycles += ,@($cfg, $LOAD)
    }

    foreach ($cycle in $cycles) {
    $cell = $cycle[0]
    $load = $cycle[1]
    if ($Kinds.Count -gt 0) { Log "  ---- cycle $($cell.name) at $KindVus VUs ----" }
    $rps = @()
    for ($i = 1; $i -le $MaxLoads; $i++) {
        $out = & .\hauberk.cmd run super-load @hcfg @load 2>&1
        # Belt and braces: take only the LAST value the function emits, and force it to a double, so a
        # stray pipeline write can never turn the climb check into string arithmetic again.
        $r = @(Save-Result $out $run $cell $i)[-1]
        if ($null -ne $r -and $r -match '^\d+(\.\d+)?$') { $rps += [double]$r }
        if ($i -ge $MinLoads -and $rps.Count -ge 2) {
            $climb = 100.0 * ($rps[-1] - $rps[-2]) / $rps[-2]
            if ($climb -le $ClimbPct) { Log "    settled ($([math]::Round($climb,1))% vs previous) -- $i loads"; break }
            Log "    still climbing (+$([math]::Round($climb,1))%) -- extending"
            if ($i -eq $MaxLoads) { Log "    !! hit the $MaxLoads-load cap WHILE STILL CLIMBING -- steady state not reached, treat this config's number as a floor" }
        }
    }
    }

    # THE LEG PASS. --metrics makes the gateway buffer the response to stamp its timing headers, which
    # inflates its own band and lowers throughput -- so it can never share a cycle with a plateau
    # measurement. It runs here instead: same build, same data, a LIGHT load well under the knee, one load
    # per kind. Rows land as "<config>-<kind>-legs"; the per-leg CSVs land in the Gatling run folder.
    if ($MetricsVus -gt 0 -and $Kinds.Count -gt 0) {
        foreach ($k in $Kinds) {
            $vals = [ordered]@{ read = 0; update = 0; create = 0; move = 0; tx = 0; tree = 0; readacct = 0; treesmall = 0; cache = 0; cachesmall = 0 }
            $vals[[string]$k] = $MetricsVus
            $one = @("--duration","$MetricsDuration","--metrics")
            foreach ($v in $vals.Keys) { $one += @("--$v", "$($vals[$v])") }
            $legCell = $cfg.Clone()
            $legCell.name = "$($cfg.name)-$k-legs"
            Log "  ---- legs $($legCell.name) at $MetricsVus VUs for $MetricsDuration s ----"
            $out = & .\hauberk.cmd run super-load @hcfg @one 2>&1
            $null = Save-Result $out $run $legCell 1 $MetricsDuration
        }
    }
}

# ---- the matrix ---------------------------------------------------------------------------
Log "############ PERF MATRIX START ($($CONFIGS.Count) configs x $Runs runs) ############"
Log "### docker down -- the k8s half must have the machine to itself"
Set-Location "$SVCS\compose"; docker compose --profile o11y down -v *> $null

$run = 0
foreach ($cfg in $CONFIGS) {
    if ($cfg.target -eq "docker" -and -not $script:k8sDrained) {
        # k8s-down.bat is NOT a full teardown. It uninstalls the app releases and esquire-infra, but NOT the
        # observability releases that o11y-on.bat installs separately (loki / alloy / tempo / otel-collector /
        # prometheus / grafana / postgres-exporter), nor kafka / redis / topology. NINE pods survive it --
        # including the entire k8s collector stack -- and they then compete with docker for the same cores.
        # That is the co-tenancy that has already invalidated two sets of docker numbers.
        # So: uninstall EVERY esquire release, and REFUSE TO CONTINUE if anything is still standing. A drain
        # that cannot prove itself must abort, not log "drained" and carry on measuring a contaminated box.
        Log "### tearing k8s FULLY down -- docker must have the machine to itself"
        Set-Location "$SVCS\k8s"
        foreach ($rel in (helm list --short 2>$null)) { helm uninstall $rel *> $null }
        for ($i = 0; $i -lt 60; $i++) {
            if (@(kubectl get pods --no-headers 2>$null | Select-String "^esquire-").Count -eq 0) { break }
            Start-Sleep -Seconds 5
        }
        $left = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-").Count
        if ($left -gt 0) {
            Log "### !! $left k8s pods SURVIVED the teardown -- docker would be measured on a shared box."
            Log "### !! ABORTING the docker half. Drain the cluster by hand and rerun."
            throw "k8s not drained ($left pods still up)"
        }
        $script:k8sDrained = $true
        Log "### k8s drained -- 0 pods, 0 helm releases; docker has the machine"
    }
    # The run NUMBER is derived from the config's slot in the FULL matrix, never from a running counter:
    # with -Configs the counter would restart at 1 and collide with runs already in the CSV, which the
    # resume check would then read as "already complete" and skip the whole subset.
    $slot = $ALL_CONFIG_NAMES.IndexOf($cfg.name)
    for ($r = 1; $r -le $Runs; $r++) { Invoke-Run (($slot * $Runs) + $r) $cfg }
}
Log "############ MATRIX COMPLETE -> $CSV ############"
Log "Analyse with:  python perf-matrix-report.py `"$CSV`""
