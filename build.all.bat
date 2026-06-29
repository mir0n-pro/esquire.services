@echo off
echo ---- services ----

pushd common
call mvn clean install
popd 
rem pause

pushd dataKeep
call mvn clean install
popd 
rem pause

pushd audit
call mvn clean install
popd 
rem pause

pushd bizTree
call docker-compose-build.bat
popd 
rem pause

pushd enyMan
call docker-compose-build.bat
popd 
rem pause

pushd pacMan
call docker-compose-build.bat
popd 
rem pause

pushd keySmith
call docker-compose-build.bat
popd 
rem pause

pushd kcMaster
call docker-compose-build.bat
popd 
rem pause

pushd auKeep
call docker-compose-build.bat
popd 
rem pause

pushd gateway
call docker-compose-build.bat
popd 
rem pause

echo ---- frontend ----

pushd ..\explorer\frontend
call docker-build-test-deploy.bat
popd 
rem pause

pushd ..\explorer\backend
call docker-compose-build.bat
popd 
rem pause

echo ---- components ----

pushd keycloak
call docker-build.bat
popd
rem pause

pushd postgres
call docker-build.bat
popd

pushd activemq
call docker-build.bat
popd

pause


