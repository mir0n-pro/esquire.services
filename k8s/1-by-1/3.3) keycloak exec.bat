kubectl exec esquire-infra-kc-keycloak-0 -- bash -c "exec 3<>/dev/tcp/localhost/9000 && echo OK"
