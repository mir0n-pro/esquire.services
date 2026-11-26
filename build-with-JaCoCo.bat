@echo off
rem Run the test suite with JaCoCo coverage from a clean slate. The parent pom directs each
rem module's report to services\test\JaCoCo\<artifactId> (deliberately OUTSIDE module target\),
rem so the leading `clean` purges every module's target\ -- stale classes and any old in-target
rem reports -- WITHOUT touching the collected coverage under test\JaCoCo\.
rem The collected reports are emptied here first instead: the report step writes a page for every
rem class it finds and never deletes the page of a class that is gone. framed.html stays.
rem Open test\JaCoCo\framed.html to browse the results.
rem Prerequisite: Docker running (the *IntegrationTest classes use Testcontainers).
cd /d "%~dp0"

for /d %%D in ("test\JaCoCo\esquire-*") do rd /s /q "%%D"

mvn clean test "-Dmaven.test.failure.ignore=true"

echo JaCoCo results are under test\JaCoCo\  --  open test\JaCoCo\framed.html
