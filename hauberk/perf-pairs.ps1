# ===========================================================================================
#  Esquire Haubergeon -- PER-OPERATION PAIRS
#
#  One operation per cell, and the two shapes measured back to back:
#
#      for each kind:  classic  (reseed -> data -> cycle -> capture)  -> drain
#                      compact  (reseed -> data -> cycle -> capture)  -> drain
#
#  WHY PAIRED. A matrix that runs all of one shape and then all of the other compares cells an hour
#  apart, on a box whose state drifts. Pairing a kind's two cells puts them minutes apart, so what is
#  left between them is the shape.
#
#  WHY A FULL DRAIN BETWEEN CELLS. k8s-down.bat uninstalls the releases of ITS OWN profile only. Going
#  from classic to compact without draining leaves the other profile's pods on the box, and they compete
#  for the same cores -- the co-tenancy that has already invalidated one set of numbers.
#
#  Each cell is a separate perf-matrix.ps1 invocation, so every rule that script enforces still holds:
#  PVCs dropped, fresh seed, realm re-imported, load 1 discarded, plateau by the climb rule.
# ===========================================================================================

param(
    [string[]] $Kinds    = @("cache","cachesmall","read","readacct","update","tx"),
    [string]   $OutDir   = "$PSScriptRoot\output\perf-pairs",
    [int]      $KindVus  = 64,
    [int]      $Duration = 120,
    [int]      $MinLoads = 4,
    [int]      $MaxLoads = 6,
    [int]      $MetricsVus = 8,
    [int]      $MetricsDuration = 60,
    [int]      $PrepClients = 100,
    [int]      $PrepAccounts = 1,
    [int]      $PrepDepth = 5,
    [int]      $PoolMax = 0,
    [switch]   $RecycleVm
)

$ErrorActionPreference = "Continue"
$HAUBERK = $PSScriptRoot

function Drain {
    foreach ($rel in (helm list --short 2>$null)) { helm uninstall $rel *> $null }
    for ($i = 0; $i -lt 60; $i++) {
        if (@(kubectl get pods --no-headers 2>$null | Select-String "^esquire-").Count -eq 0) { break }
        Start-Sleep -Seconds 5
    }
    $left = @(kubectl get pods --no-headers 2>$null | Select-String "^esquire-").Count
    if ($left -gt 0) {
        Write-Host "!! $left pods survived the drain -- refusing to measure on a shared box"
        return $false
    }
    if ($RecycleVm) { return (Recycle-Vm) }
    return $true
}

# THE VM KEEPS WHAT IT TOOK. WSL2 reclaims lazily, so the Docker VM grows across from-scratch cells and
# never hands the pages back: eleven rebuilds took it to 8.5 GB and the host ran out of room mid-run. A cell
# is ~17 minutes; this is ~90 seconds, and it makes every cell start on the same machine.
function Recycle-Vm {
    Write-Host "   recycling the Docker VM"
    Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 5
    wsl --shutdown
    Start-Sleep -Seconds 5
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 5
        $node = kubectl get nodes --no-headers 2>$null | Select-String " Ready "
        if ($node) {
            $free = [math]::Round((Get-Counter '\Memory\Available MBytes').CounterSamples[0].CookedValue / 1024, 1)
            Write-Host "   cluster back after $((($i + 1) * 5)) s, ${free} GB available"
            return $true
        }
    }
    Write-Host "!! the cluster did not come back after the VM recycle"
    return $false
}

function RowCount {
    $csv = Join-Path $OutDir "matrix.csv"
    if (-not (Test-Path $csv)) { return 0 }
    return @(Import-Csv $csv).Count
}

function Cell($cfgName, $kind) {
    Write-Host "######## $cfgName / $kind ########"
    # STOPPED COMPLETELY BEFORE THE CELL, NOT ONLY AFTER IT. A cell must never start against anything
    # left standing -- not the other profile's pods, not this profile's own.
    if (-not (Drain)) { throw "cluster not drained before $cfgName/$kind" }

    $before = RowCount
    & "$HAUBERK\perf-matrix.ps1" -Only $cfgName -Kinds $kind -KindVus $KindVus `
        -Runs 1 -Duration $Duration -MinLoads $MinLoads -MaxLoads $MaxLoads `
        -MetricsVus $MetricsVus -MetricsDuration $MetricsDuration -ServiceMetrics -PoolMax $PoolMax `
        -PrepDepth $PrepDepth -PrepClients $PrepClients -PrepAccounts $PrepAccounts `
        -OutDir $OutDir
    $after = RowCount

    # A cell that writes NOTHING has not measured anything -- a skipped resume, a failed build, a token
    # that never came. It once passed quietly with a COMPLETE line; it stops the run now.
    if ($after -le $before) { throw "$cfgName/$kind produced no rows -- refusing to continue" }
    Write-Host "   $cfgName/$kind wrote $($after - $before) rows"

    if (-not (Drain)) { throw "cluster not drained after $cfgName/$kind" }
}

Write-Host "######## PAIRS START: $($Kinds -join ', ') ########"
if (-not (Drain)) { throw "cluster not drained at start" }

foreach ($kind in $Kinds) {
    Cell "k8sc-x2-OFF" $kind
    Cell "k8s-x2-OFF"  $kind
}

Write-Host "######## PAIRS COMPLETE -> $OutDir\matrix.csv ########"
