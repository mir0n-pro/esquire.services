@echo off
rem Thin launcher for the Esquire Haubergeon CLI.
rem Adds the JVM flags Gatling 3.13 needs (reflection into java.base internals)
rem and forwards every argument to HauberkCli.
rem
rem Build first: mvn -pl hauberk install
java --add-opens=java.base/java.lang=ALL-UNNAMED ^
     --add-opens=java.base/java.util=ALL-UNNAMED ^
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED ^
     -jar "%~dp0target\hauberk.jar" %*
