@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-config-parity.bat -- verify LOCAL-k8s <-> OKE deploy config are in sync,
rem                          for the COMPACT profile.
rem
rem Thin launcher for test\config-parity\config-parity.py (the script lives with
rem the other test scripts under test\; this runner keeps it accessible next to
rem the oke-* tooling). It renders each service's ConfigMap for the local overlay
rem (k8s\values) AND the OKE overlay (k8s-oci\values) with `helm template` and
rem diffs the delivered env-var set, so a setting applied to local k8s but MISSED
rem on OKE is a failing check.
rem
rem OFFLINE: reads the charts only -- NO cluster, NO kubectl context. helm must be
rem on PATH. Exit 0 = in sync, 1 = drift found, 2 = a render failed.
rem ===========================================================================
python "%~dp0..\test\config-parity\config-parity.py" --profile compact
endlocal
