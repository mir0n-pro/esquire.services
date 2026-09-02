# =============================================================================
# Register the public origin with KeyCloak -- T7.
#
# The realm baked into the esquire-keycloak image allows only the origins the
# other targets use: localhost, esquire.localhost and esquire.mir0n.pro. The AWS
# host is none of those, so a browser sign-in through it is refused with
# "Invalid parameter: redirect_uri" -- which reads like a bug in the BFF and is not.
#
# This adds the public origin to the esq-angular client at run time. It persists in
# KeyCloak's own storage (the PVC), so it survives a pod restart but NOT a fresh
# volume -- and this cluster is created fresh every time, so it is a step of every
# bring-up rather than a one-off.
#
# THE ADMIN PASSWORD IS NOT DEFAULTED HERE. It is the same value aws-up.bat took as
# mir0n_pwd; a script that carries a working credential is a credential in the repo.
#
#   $env:mir0n_pwd = '...'
#   .\aws-public-origin.ps1                                   the ingress host
#   .\aws-public-origin.ps1 -Origin https://esquire.example.com
# =============================================================================
param(
    [string] $Origin = "",
    [string] $AdminUser = "admin",
    [string] $AdminPass = $env:mir0n_pwd
)

$ErrorActionPreference = "Stop"

if (-not $AdminPass) {
    throw "no KeyCloak admin password -- set mir0n_pwd, or pass -AdminPass"
}

# The INGRESS host, not the load balancer name. The *.elb.amazonaws.com name answers
# too, but the certificate is issued for the ingress host, so using the LB name gives
# a TLS mismatch that reads as a broken site rather than a wrong URL.
if (-not $Origin) {
    $ingressHost = kubectl get ingress esquire-public -o jsonpath='{.spec.rules[0].host}'
    if (-not $ingressHost) { throw "no host on the esquire-public ingress -- apply cluster\ingress.yaml first" }
    $Origin = "https://$ingressHost"
}
Write-Output "origin: $Origin"

$kc = "$Origin/kc-auth"

# The admin token comes from the MASTER realm, not esquire.
$body = "grant_type=password&client_id=admin-cli&username=$AdminUser&password=$AdminPass"
$tok = (Invoke-RestMethod -Uri "$kc/realms/master/protocol/openid-connect/token" -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -TimeoutSec 30).access_token
$h = @{ Authorization = "Bearer $tok" }

foreach ($clientId in @("esq-angular")) {
    $c = Invoke-RestMethod -Uri "$kc/admin/realms/esquire/clients?clientId=$clientId" -Headers $h -TimeoutSec 30
    if (-not $c -or $c.Count -eq 0) { throw "client $clientId not found in the esquire realm" }
    $c = $c[0]

    $redirect = "$Origin/auth/callback"
    $uris = @($c.redirectUris)
    $origins = @($c.webOrigins)

    $changed = $false
    if ($uris -notcontains $redirect) { $uris += $redirect; $changed = $true }
    if ($origins -notcontains $Origin) { $origins += $Origin; $changed = $true }

    if ($changed) {
        $c.redirectUris = $uris
        $c.webOrigins = $origins
        $json = $c | ConvertTo-Json -Depth 20
        Invoke-RestMethod -Uri "$kc/admin/realms/esquire/clients/$($c.id)" -Headers $h -Method Put -Body $json -ContentType "application/json" -TimeoutSec 30
        Write-Output "  $clientId : added $redirect"
    } else {
        Write-Output "  $clientId : already allows $redirect"
    }
}

Write-Output "done."
