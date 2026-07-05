@echo off
setlocal
rem Generate the API reference (Javadoc) for the reusable Esquire LIBRARY modules
rem (common, messaging, dataKeep, audit, tp-activemq, tp-redis, tp-kafka) and publish it
rem OUTSIDE target, under doc\java-doc\<module>\ -- a visible, browsable deliverable.
rem On-demand only (not part of the normal build); uses the -Pjavadoc profile.
cd /d "%~dp0"

set MODS=common,messaging,dataKeep,audit,tp-activemq,tp-redis,tp-kafka

call mvn -q -Pjavadoc -DskipTests compile javadoc:javadoc -am -pl %MODS%
if errorlevel 1 (
    echo Javadoc generation FAILED.
    exit /b 1
)

rem The javadoc goal writes to <module>\target\reports\apidocs; mirror each into doc\java-doc.
for %%M in (common messaging dataKeep audit tp-activemq tp-redis tp-kafka) do (
    if exist "%%M\target\reports\apidocs" (
        robocopy "%%M\target\reports\apidocs" "doc\java-doc\%%M" /MIR /NJH /NJS /NDL /NP >nul
    )
)

echo.
echo Javadoc published under doc\java-doc\ (one folder per library module).
endlocal
