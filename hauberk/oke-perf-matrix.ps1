# ===========================================================================================
#  Esquire Haubergeon -- OKE PERFORMANCE MATRIX (super-compact)
#
#  The OKE twin of perf-matrix.ps1. Drives the standard 200-VU super-load across every
#  deployment shape on the LIVE OKE cluster and writes one CSV you can compare, in the SAME
#  schema as the local matrix (perf-matrix-report.py reads both).
#
#      oke  x1  o11y OFF / LOG / FULL
#      oke  x2  o11y OFF / LOG / FULL
#
#  OKE runs SUPER-COMPACT and nothing else (mir0n, 2026-08-19): Mesnie, gateWard, pacMan and the
#  BFF. The x1/x2 arms scale the three esquire services; the BFF is held at x2 throughout.
#
#  o11y has THREE modes (I49): OFF (pro.mir0n OFF, no stack) / LOG (pro.mir0n INFO + loki/
#  alloy/grafana) / FULL (pro.mir0n INFO + tracing + metrics + full stack). Only pro.mir0n
#  moves, so the modes ADD UP. -Runs sets runs per config, -Only runs a subset, -Scale scales
#  the VU count. Entry point: oke-perf-matrix.bat (launches this detached).
#
# -------------------------------------------------------------------------------------------
#  WHY THE OKE TWIN IS SHAPED DIFFERENTLY -- three forced departures from the local rig.
#
#  TOGGLE IN PLACE, NOT FROM SCRATCH.  The local matrix tears the environment down and drops
#  the Postgres + KeyCloak + broker PVCs before every run, because accumulated audit rows and
#  deleted entities drag throughput ~8%/run. OKE CANNOT do this: it is a live cluster, Postgres
#  is ALTER-migrated and never reseeded, and the free-tier block-volume budget forbids casual
#  PVC churn. So each cell is reached by TOGGLING the running cluster -- scale the app replicas,
#  then oke-o11y-on.bat <arm> -- never by a rebuild.
#
#  THE COST: run-to-run drift EXCEEDS the local 1-2% noise floor.  Audit history accumulates
#  across the whole matrix (OKE audits via DB triggers -- there is no stack to turn off and no
#  volume to wipe). clean-house between runs clears the created entities and the KC orphans the
#  create scenario leaves, but NOT the audit rows. Read a small OKE OFF->LOG delta with that in
#  mind: the two-run gap here is the honest floor, and it is wider than local's.
#
#  o11y RIDES SEPARATE PAID NODES (T12).  The 7 o11y deployments are pinned tier=o11y onto the
#  two paid nodes; the app is hard-pinned tier=app on the free nodes. So on OKE the OFF->FULL
#  delta is the IN-APP instrumentation cost ALONE -- the loki/tempo/prometheus backend CPU does
#  not compete with the app (unlike local single-node k8s, where they share the box). That is a
#  CLEANER number, but it is NOT the same quantity local measures; do not diff the two directly.
#
#  ONE RUN PER SETUP (LOCKED, mir0n) -- NOT two. Local's two runs are a noise floor because each is
#  FROM SCRATCH (independent). OKE cannot wipe, so a second run is just more accumulation on a bigger
#  DB, not an independent replicate -- it deepens the run-order drift instead of measuring it. The
#  within-run adaptive settle is the confidence. See the $Runs note.
#
#  EVERYTHING ELSE IS THE LOCAL RIG VERBATIM: the 200-VU super-load, DISCARD LOAD 1 (warm-up),
#  the adaptive load count (extend while the last load still climbs >$ClimbPct), RESUMABLE (a run
#  already in the CSV is skipped). See perf-matrix.ps1 for the full rationale on each.
#
#  STARTS AND ENDS AT THE DEFAULT (mir0n).  Before the first cell and after the last, the
#  cluster is put back to the OKE default: pro.mir0n/root at ERROR, no viewing stack, x2. The
#  matrix owns that restoration itself (there is no from-scratch bring-up to imply it).
# ===========================================================================================

param(
    [string] $OutDir     = "$PSScriptRoot\output\oke-perf-matrix",
    [int]    $Duration   = 120,      # seconds per load
    # OKE WARMS SLOWER THAN LOCAL, so the load ceiling is HIGHER here (pass-1 with 6 taught this).
    # Over the ~55ms Toronto RTT the fleet turns ~150 rps, so each 120s load delivers ~13x FEWER
    # requests than local's ~2000 rps -> the JIT (and the o11y instrumentation code paths, which is
    # why LOG/FULL climb longer than OFF) needs many more loads to reach steady state. At MaxLoads=6
    # five of six x1 runs hit the cap STILL CLIMBING (LOG floored at 92 vs OFF's settled 152 -- a
    # warm-up artifact, not a real -39% logging cost). 12 lets them settle.
    [int]    $MinLoads   = 6,
    [int]    $MaxLoads   = 12,
    [double] $ClimbPct   = 3.0,      # extend while the last load is >this% above the previous
    # ONE run per setup on OKE (LOCKED, mir0n). Local uses TWO because each is FROM SCRATCH -- an
    # independent replicate = a real noise floor. OKE cannot wipe (live ALTER-migrated data), so a
    # second run is NOT independent: it runs on an even bigger DB, adding accumulation, not confidence.
    # The within-run adaptive settle (loads 2..N converging) is the confidence here; a second run just
    # deepens the run-order drift. So: ONE run each.
    [int]    $Runs       = 1,
    [switch] $Fresh,                 # start a new CSV instead of resuming the existing one
    [string[]] $Only     = @(),      # run ONLY these config names (empty = all of $CONFIGS)
    [double] $Scale      = 1.0       # scale every scenario's VU count (1.0 = the standard 200-VU super-load)
)

$ErrorActionPreference = "Continue"
# NEVER roll the broker (mir0n): a toggle-in-place matrix must not bounce ActiveMQ under running app
# pods -- that drops their messagingBus connection, which does NOT self-heal (the health indicator
# stays DOWN until the pod restarts, hanging the run). oke-o11y-on/off honor this flag by SKIPPING the
# kc/amq metric rolls (broker/kc metrics are their own, not app o11y cost -> skipping is neutral).
$env:SKIP_INFRA_ROLL = "1"
$SVCS    = "C:\MyProjects\esquire\services"
$OKEDIR  = "$SVCS\k8s-oci-compact"
$HAUBERK = $PSScriptRoot
$HCFG    = @("--config","hauberk-oke.properties")
# OKE app services that carry a pro.mir0n knob and scale x1/x2. THREE, not six: Mesnie carries
# enyMan, keySmith and the identity work, gateWard carries the gate and the tree cache. No aukeep
# (super-compact audits via DB triggers, option (a)).
#
# The BFF is NOT scaled here and is not in this list. It now runs x2 on OKE (a Redis shared-session
# store arrived with super-compact), but it is held CONSTANT across every arm precisely so it
# cancels out of the delta -- the x1/x2 arms are about the three esquire services, as before.
$OKE_APPS = @("gateward","mesnie","pacman")
$EXPECTED = 3 + 2   # three services x their replicas + the BFF x2 -- used only for the readiness log

# The standard super-load: 200 VUs as read 64 / update 32 / create 32 / move 8 / tx 64.
$vus = @{ read = 64; update = 32; create = 32; move = 8; tx = 64 }
$scaled = @{}
foreach ($k in @($vus.Keys)) { $scaled[$k] = [Math]::Max(1, [int][Math]::Round($vus[$k] * $Scale)) }
$LOAD = @("--duration","$Duration",
          "--read","$($scaled.read)","--update","$($scaled.update)","--create","$($scaled.create)",
          "--move","$($scaled.move)","--tx","$($scaled.tx)")

# The matrix: six cells. OFF is the baseline, LOG isolates the log pillar, FULL is all three.
# levelMir0n is the only logger that moves (oke-o11y-on.bat sets it per arm); root sits at its
# ERROR default in EVERY arm and cancels out of the delta. No ON arm and no docker here -- ON is
# a local-only T10 reproduction, and docker is a smoke test, never an o11y measurement.
$CONFIGS = @(
    @{ name = "oke-x1-OFF";  reps = 1; o11y = "OFF"  },
    @{ name = "oke-x1-LOG";  reps = 1; o11y = "LOG"  },
    @{ name = "oke-x1-FULL"; reps = 1; o11y = "FULL" },
    @{ name = "oke-x2-OFF";  reps = 2; o11y = "OFF"  },
    @{ name = "oke-x2-LOG";  reps = 2; o11y = "LOG"  },
    @{ name = "oke-x2-FULL"; reps = 2; o11y = "FULL" }
)
$ALL_CONFIG_NAMES = [System.Collections.ArrayList]@($CONFIGS | ForEach-Object { $_.name })

if ($Only.Count -gt 0) {
    $known = $ALL_CONFIG_NAMES
    $typo  = $Only | Where-Object { $known -notcontains $_ }
    if ($typo) { throw "unknown config name(s): $($typo -join ', ') -- known: $($known -join ', ')" }
    $CONFIGS = @($CONFIGS | Where-Object { $Only -contains $_.name })
}

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
    Write-Host $line
}

# ---- CONTEXT GUARD: never run the OKE matrix against docker-desktop -----------------------
$ctx = (kubectl config current-context 2>$null)
if ($ctx -eq "docker-desktop") {
    Log "!! kubectl context is docker-desktop -- this is the OKE matrix. Refusing."
    throw "wrong kube context: $ctx"
}
Log "kube context: $ctx"

# ---- parse one Gatling global-stats block (identical to the local rig) --------------------
function Save-Result($out, $run, $cfg, $loadNo) {
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
    $rps   = [math]::Round($total / $Duration, 1)
    $koPct = if ($total -gt 0) { [math]::Round(100.0 * [double]$ko / $total, 3) } else { 0 }
    "$run,$($cfg.name),oke,$($cfg.reps),$($cfg.o11y),$loadNo,$total,$ok,$ko,$koPct,$rps,$(Col 'mean response time'),$(Col '50th percentile'),$(Col '75th percentile'),$(Col '95th percentile'),$(Col '99th percentile'),$(Col 'max response time')" |
        Out-File -FilePath $CSV -Encoding utf8 -Append
    Log "    load $loadNo : $total req, $rps rps, p99=$(Col '99th percentile')ms, KO $ko ($koPct%)"
    return $rps
}

# ---- wait until every esquire app pod is 1/1 Running --------------------------------------
function Wait-OkePods {
    # Phase 1: give the rollout ~5 min to settle cleanly.
    for ($i = 0; $i -lt 30; $i++) {
        $all = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-(gateward|mesnie|pacman|backend)-")
        $bad = @($all | Select-String -NotMatch "1/1\s+Running")
        if ($all.Count -gt 0 -and $bad.Count -eq 0) { return }
        Start-Sleep -Seconds 10
    }
    # Phase 2: a pod still not Ready is almost always a messagingBus straggler -- a JMS reconnect that
    # did not recover (the app pod's health indicator stays DOWN). The proven remedy is a restart: KICK
    # it, then wait. Bounded (3 rounds) so a genuinely broken pod cannot loop forever. With SKIP_INFRA_ROLL
    # the broker is never bounced, so this should rarely fire -- it is the belt-and-braces for a stray
    # app-rollout straggler.
    for ($round = 0; $round -lt 3; $round++) {
        $bad = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-(gateward|mesnie|pacman|backend)-" | Select-String -NotMatch "1/1\s+Running")
        if ($bad.Count -eq 0) { return }
        foreach ($line in $bad) {
            $name = (($line.ToString()) -split '\s+')[0]
            Log "    kicking not-Ready pod $name (messagingBus straggler)"
            kubectl delete pod $name --grace-period=5 *> $null
        }
        for ($i = 0; $i -lt 24; $i++) {
            $b2 = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-(gateward|mesnie|pacman|backend)-" | Select-String -NotMatch "1/1\s+Running")
            if ($b2.Count -eq 0) { return }
            Start-Sleep -Seconds 10
        }
    }
    Log "    !! app pods never all became Ready even after kicks"
}

# UP = the host ANSWERED, not "answered 200". Invoke-WebRequest throws on every non-2xx, so a bare
# try/catch treats an authenticated endpoint's 401 as down and waits forever. What distinguishes a
# ready gate from an unready one is WHICH status comes back:
#   2xx/3xx/4xx -- the gate answered. A 401 on a guarded route is the gate UP and enforcing auth.
#   5xx         -- NOT up: ingress-nginx returns 502/503 when no endpoint is ready behind it.
#   no response -- NOT up: connection refused, DNS, or timeout.
function Wait-Url($url, $tries = 90) {
    for ($i = 0; $i -lt $tries; $i++) {
        try {
            Invoke-WebRequest -Uri $url -TimeoutSec 6 -UseBasicParsing | Out-Null
            return $true
        }
        catch {
            $code = 0
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            if ($code -ge 200 -and $code -lt 500) { return $true }
            Start-Sleep -Seconds 10
        }
    }
    return $false
}

# ---- reach a cell BY TOGGLING the live cluster (no rebuild) --------------------------------
function Set-OkeConfig($cfg) {
    Set-Location $OKEDIR
    # 1) o11y arm. oke-o11y-on.bat <arm> sets pro.mir0n per arm (OFF/INFO/INFO), installs or
    #    removes the viewing stack, pins it tier=o11y, and rolls the app. This also RESETS
    #    replicas to the values default (x2) via --reset-then-reuse-values, so scaling MUST
    #    come after it, never before.
    Log "  o11y arm -> $($cfg.o11y)"
    # Tell the arm the replica count it is about to be scaled to. helm uses SERVER-SIDE APPLY: kubectl-scale from
    # the previous cell owns .spec.replicas, so an upgrade that asks for the chart default while the live value
    # differs is REFUSED -- and oke-o11y-on.bat calls helm with `*> $null` and no exit-code check, so the arm is
    # silently not applied and the cell measures the PREVIOUS arm. That is what voided the first pass.
    $env:ESQ_REPLICAS = "$($cfg.reps)"
    & ".\oke-o11y-on.bat" $cfg.o11y *> $null
    Wait-OkePods

    # 2) replicas -- set EXPLICITLY for both x1 and x2. The o11y arm's `helm upgrade
    #    --reset-then-reuse-values` does NOT restore replicaCount (kubectl-scale from a prior x1 cell
    #    persists on the live STS, and helm reuse-values does not override it), so a reps=2 cell would
    #    silently run at x1 if we trusted the arm. Always scale to $cfg.reps. backend (BFF) stays x1.
    Log "  scaling the three services to x$($cfg.reps)"
    foreach ($s in $OKE_APPS) { kubectl scale sts "esquire-$s" --replicas=$($cfg.reps) *> $null }
    Start-Sleep -Seconds 15
    Wait-OkePods

    # 3) gate on the two things a load actually needs: the realm reachable and the gateway UP,
    #    both via the public ingress (the same path hauberk drives -- no port-forward).
    if (-not (Wait-Url "https://esquire.mir0n.pro/kc-auth/realms/esquire")) { Log "  !! realm not reachable"; return $false }
    # NOT /actuator/health: the actuator sits on the management port (F0 A6) and is deliberately not
    # published through the Service or the public ingress, so it 404s here. A guarded route is the
    # honest probe -- 401 means the gate is up, routing, and enforcing auth; 503 means it is not.
    if (-not (Wait-Url "https://api.esquire.mir0n.pro/esq-enode"))           { Log "  !! the gate never came UP"; return $false }
    Log "  OKE ready (x$($cfg.reps), o11y=$($cfg.o11y))"
    return $true
}

# ---- restore the OKE default: ERROR/ERROR, no stack, x2 -----------------------------------
function Restore-Default {
    Set-Location $OKEDIR
    Log "### restoring OKE default (pro.mir0n/root ERROR, no viewing stack, x2)"
    # Re-apply the deploy values per service: this is the toggle-in-place equivalent of the
    # local rig's from-scratch bring-up -- it puts levelRoot/levelMir0n back to their ERROR
    # default and replicaCount back to 2, all from k8s-oci-compact\values\<svc>.yaml.
    foreach ($s in $OKE_APPS) {
        # --force-conflicts: helm 4 applies server-side, and kubectl-scale owns .spec.replicas by now, so an
        # upgrade that touches it is refused. Without this the restore silently leaves the last cell's arm on.
        & helm upgrade "esquire-$s" "$SVCS\k8s-compact\charts\esquire-$s" -f "values\$s.yaml" --reset-then-reuse-values --force-conflicts *> $null
        kubectl scale sts "esquire-$s" --replicas=2 *> $null   # explicit -- see Set-OkeConfig note
    }
    & ".\oke-o11y-off.bat" *> $null
    # Belt-and-braces: oke-o11y-off's uninstalls can be swallowed under a busy restore (seen once --
    # left all 7 o11y releases up). Force any remnant so the default is truly stack-free.
    foreach ($r in @("grafana","postgres-exporter","prometheus","otel-collector","tempo","alloy","loki")) {
        & helm uninstall "esquire-infra-$r" *> $null
    }
    Wait-OkePods
    Log "### OKE default restored"
}

# ---- MEMORY, actually used (T4) -----------------------------------------------------------
# The throughput CSV answers "how fast"; T4 asks how much memory the shape actually USES, under
# load, as against what it RESERVES. Reservation is bookkeeping -- measured idle, this fleet
# reserved ~1150m CPU while using ~20m -- so the honest figure is sampled mid-load, once the JVMs
# have allocated, and it is what the compact claim rests on.
#
# Written to a COMPANION csv: perf-matrix-report.py reads the throughput schema for both the local
# and the OKE matrix, and it must stay byte-identical.
$MEMCSV = Join-Path $OutDir "memory.csv"
if ($Fresh -or -not (Test-Path $MEMCSV)) {
    "run,config,replicas,o11y,load,pod,cpu_m,mem_mi" | Out-File -FilePath $MEMCSV -Encoding utf8
}

function Sample-Memory($run, $cfg, $load) {
    # kubectl top reads metrics-server, which is installed on this cluster. A sample that fails is
    # logged and skipped -- a missing memory row must never abort a load.
    $rows = @(kubectl top pods -n default --no-headers 2>$null)
    if ($rows.Count -eq 0) { Log "    (memory sample unavailable -- metrics-server not answering)"; return }
    foreach ($r in $rows) {
        $f = ($r -split '\s+') | Where-Object { $_ -ne "" }
        if ($f.Count -lt 3) { continue }
        $pod = $f[0]
        if ($pod -notmatch '^esquire-') { continue }
        $cpu = ($f[1] -replace 'm$','')
        $mem = ($f[2] -replace 'Mi$','')
        "$run,$($cfg.name),$($cfg.reps),$($cfg.o11y),$load,$pod,$cpu,$mem" |
            Out-File -FilePath $MEMCSV -Append -Encoding utf8
    }
}

# ---- one run: toggle -> prepare -> loads until steady -> clean-house -----------------------
function Invoke-Run($run, $cfg) {
    if (Test-Path $CSV) {
        $done = @(Import-Csv $CSV | Where-Object { $_.run -eq "$run" }).Count
        if ($done -ge $MinLoads) { Log "======== RUN $run : $($cfg.name) -- already complete, skipping ========"; return }
        if ($done -gt 0) {
            @(Import-Csv $CSV | Where-Object { $_.run -ne "$run" }) | Export-Csv -Path $CSV -NoTypeInformation -Encoding utf8
            Log "  (discarded $done rows from an interrupted run $run)"
        }
    }
    Log "======== RUN $run : $($cfg.name)  (toggle in place) ========"
    if (-not (Set-OkeConfig $cfg)) { Log "  ABORTING run $run"; return }

    Set-Location $HAUBERK
    # CHECK the prepare -- a broken environment must abort here, not produce NO-STATS loads.
    $prep = & .\hauberk.cmd run prepare-for-anything @HCFG --prep-depth 5 --prep-clients 5 --prep-accounts 2 2>&1
    if (@($prep | Select-String "request count").Count -eq 0) {
        Log "  !! PLAYGROUND PREPARE PRODUCED NO REQUESTS -- environment not usable (token? gateway? seeded DB?)"
        Log "  ABORTING run $run"
        return
    }
    Log "  playground prepared; loads (min $MinLoads, extend while climbing >$ClimbPct%, max $MaxLoads):"

    $rps = @()
    for ($i = 1; $i -le $MaxLoads; $i++) {
        # Sample memory WHILE the load runs, not after: the number wanted is the working set under
        # traffic, and a JVM gives some of it back once the load stops. The job is started here and
        # fires mid-load; the load itself is unaffected.
        $memJob = Start-Job -ScriptBlock {
            param($d, $csv, $run, $name, $reps, $o11y, $load)
            Start-Sleep -Seconds ([int]($d / 2))
            $rows = @(kubectl top pods -n default --no-headers 2>$null)
            foreach ($r in $rows) {
                $f = ($r -split '\s+') | Where-Object { $_ -ne "" }
                if ($f.Count -lt 3 -or $f[0] -notmatch '^esquire-') { continue }
                "$run,$name,$reps,$o11y,$load,$($f[0]),$($f[1] -replace 'm$',''),$($f[2] -replace 'Mi$','')" |
                    Out-File -FilePath $csv -Append -Encoding utf8
            }
        } -ArgumentList $Duration, $MEMCSV, $run, $cfg.name, $cfg.reps, $cfg.o11y, $i

        $out = & .\hauberk.cmd run super-load @HCFG @LOAD 2>&1
        Receive-Job $memJob -Wait -AutoRemoveJob *> $null
        $r = @(Save-Result $out $run $cfg $i)[-1]
        if ($null -ne $r -and $r -match '^\d+(\.\d+)?$') { $rps += [double]$r }
        if ($i -ge $MinLoads -and $rps.Count -ge 2) {
            $climb = 100.0 * ($rps[-1] - $rps[-2]) / $rps[-2]
            if ($climb -le $ClimbPct) { Log "    settled ($([math]::Round($climb,1))% vs previous) -- $i loads"; break }
            Log "    still climbing (+$([math]::Round($climb,1))%) -- extending"
            if ($i -eq $MaxLoads) { Log "    !! hit the $MaxLoads-load cap WHILE STILL CLIMBING -- treat this config's number as a floor" }
        }
    }

    # clean-house: clear the entities + KC orphans the create scenario left, so the next run
    # does not inherit them. (Audit rows remain -- OKE cannot wipe them; see the header.)
    Set-Location $HAUBERK
    Log "  clean-house (clear created entities + KC orphans)"
    & .\hauberk.cmd run clean-house @HCFG *> $null
}

# ---- the matrix ---------------------------------------------------------------------------
Log "############ OKE PERF MATRIX START ($($CONFIGS.Count) configs x $Runs runs) ############"
Log "### standard 200-VU super-load: read $($scaled.read) / update $($scaled.update) / create $($scaled.create) / move $($scaled.move) / tx $($scaled.tx)"
if ($Scale -ne 1.0) { Log "### LOAD SCALED x$Scale" }

foreach ($cfg in $CONFIGS) {
    $slot = $ALL_CONFIG_NAMES.IndexOf($cfg.name)
    for ($r = 1; $r -le $Runs; $r++) { Invoke-Run (($slot * $Runs) + $r) $cfg }
}

Restore-Default
Log "############ OKE MATRIX COMPLETE -> $CSV ############"
Log "Analyse with:  python perf-matrix-report.py `"$CSV`""
