@echo off
rem ===========================================================================
rem oke-pg-forward.bat -- expose production Postgres on localhost:25432
rem
rem Production Postgres is ClusterIP only (no public LB, no inbound from the
rem internet). For data monitoring / maintenance with pgAdmin4 (or any
rem libpq client), this script forwards the in-cluster service to your
rem laptop via kubectl port-forward.
rem
rem Local port = 25432 ON PURPOSE -- separate from:
rem   - 5432  : OS-level / system Postgres (and any future local k8s exposure)
rem   - 5433  : services/compose/compose.yaml maps host 5433 -> container 5432
rem Picking 25432 keeps OKE prod distinct so you can have all forwards open
rem at the same time in pgAdmin4 without collision.
rem
rem Auth: relies on your kubeconfig (already gated to OKE). No DB password
rem changes; once the tunnel is up, connect with the existing prod creds.
rem
rem pgAdmin4 connection:
rem   Name:     OKE prod
rem   Host:     localhost
rem   Port:     25432
rem   Database: esq2025
rem   Username: esq2025
rem   Password: (the prod %mir0n_pwd% value)
rem
rem Usage:
rem   oke-pg-forward.bat        -- foreground, Ctrl+C to stop
rem
rem Tip: run in its own terminal window; pgAdmin reconnects on its own when
rem the tunnel is up.
rem ===========================================================================

setlocal
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run unless kubectl context is the OKE cluster.
rem Mirror of oke-up.bat's guard -- forwarding postgres from the wrong
rem cluster is just confusing, not destructive, but better to fail loud.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is oke-pg-forward.bat ^(production^).
  echo Refusing to run. Switch context with: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

echo.
echo Forwarding esquire-infra-postgres -- pgAdmin4 -^> localhost:25432
echo Connection: host=localhost port=25432 db=esq2025 user=esq2025
echo Press Ctrl+C to stop.
echo.

kubectl port-forward -n default svc/esquire-infra-postgres 25432:5432
