@echo off

rem ===========================================================================
rem aws-e2e-public.bat -- run the Playwright e2e against the PUBLIC AWS URL.
rem
rem   aws-e2e-public.bat
rem
rem NO PORT-FORWARDING. Nothing is tunnelled, no localhost port is bound, and the
rem docker lab can be running at the same time without interfering -- which is the
rem whole reason T2.10 exists. The suite talks to https://aws-esquire.mir0n.pro
rem exactly as a browser does.
rem
rem THERE IS NO SECOND SUITE SCRIPT. A forwarding one existed and was removed:
rem public access replaced it, and keeping it would only invite a run through
rem four tunnels that die silently. hauberk reaches the same public host through
rem explorer/hauberk/hauberk-aws.properties.
rem
rem Prereqs, all one-time:
rem   - kubectl context on the EKS cluster        (1st.bat)
rem   - ingress + certificate live                (ingress.yaml, cluster-issuer.yaml)
rem   - the public origin registered in KeyCloak  (aws-public-origin.ps1)
rem ===========================================================================

setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1
set WS=%HERE%..\..

rem --- context guard ---------------------------------------------------------
for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/esquire-aws
for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
if not "%HAVE%"=="%WANT%" (
    echo [FAIL] wrong kubectl context.
    echo        want: %WANT%
    echo        have: %HAVE%
    echo        run 1st.bat
    exit /b 1
)

rem --- the host comes from the INGRESS, not the load balancer ----------------
rem The *.elb.amazonaws.com name answers too, but the certificate is issued for
rem the ingress host, so using the LB name gives a TLS mismatch that reads as a
rem broken site rather than a wrong URL.
for /f "usebackq tokens=*" %%H in (`powershell -NoProfile -Command "$j = kubectl get ingress esquire-public -o json; (ConvertFrom-Json ($j -join '')).spec.rules[0].host"`) do set PUB=%%H
if "%PUB%"=="" (
    echo [FAIL] no host on the esquire-public ingress -- apply ingress.yaml first.
    exit /b 1
)

echo === target: https://%PUB%

rem --- prove it before running 46 tests against nothing ----------------------
rem Chromium uses the OS resolver, so this is the same lookup the suite will do.
rem A negative DNS cache elsewhere in the path is exactly what made this fail
rem before -- see coredns-mir0n-pro.md.
powershell -NoProfile -Command "try { [System.Net.Dns]::GetHostAddresses('%PUB%') | Out-Null; exit 0 } catch { exit 1 }"
if errorlevel 1 (
    echo [FAIL] %PUB% does not resolve on this machine.
    echo        Chromium uses the OS resolver, so the suite cannot reach it either.
    echo        A resolver in the path is probably holding a cached miss.
    exit /b 1
)

rem A trusted certificate, checked WITHOUT skipping validation -- because that is
rem precisely what Playwright will not skip either.
powershell -NoProfile -Command "try { $r = Invoke-WebRequest 'https://%PUB%/' -TimeoutSec 25 -UseBasicParsing; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
if errorlevel 1 (
    echo [FAIL] https://%PUB%/ did not answer 200 with a trusted certificate.
    echo        kubectl get certificate      -- is esquire-tls READY?
    exit /b 1
)
echo === resolves, and the certificate is trusted

rem --- run -------------------------------------------------------------------
set BASE_URL=https://%PUB%
set GATEWAY_URL=https://%PUB%/esq-api
set KC_URL=https://%PUB%/kc-auth
set RELAY_DISABLED=true

pushd "%WS%\explorer\e2e-test"
if not exist node_modules (
    call npm install
    call npx playwright install chromium
)
call npm test
set RC=%errorlevel%
popd

echo.
if "%RC%"=="0" (
    echo === e2e PASSED against https://%PUB%
) else (
    echo === e2e FAILED -- report at explorer\e2e-test\playwright-report\index.html
)
exit /b %RC%
