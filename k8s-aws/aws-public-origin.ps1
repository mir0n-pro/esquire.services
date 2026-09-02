# =============================================================================
# Register the public origin with KeyCloak -- T2.10.
#
# The realm baked into the esquire-keycloak image allows only the origins the
# other targets use: localhost, esquire.localhost and esquire.mir0n.pro. The AWS
# load balancer's hostname is none of those, so a browser sign-in through it is
# refused with "Invalid parameter: redirect_uri" -- which reads like a bug in the
# BFF and is not.
#
# This adds the public origin to the esq-angular client at run time. It persists
# in KeyCloak's own storage (the PVC), so it survives a pod restart but NOT a
# fresh volume -- re-run it after any KeyCloak reinstall that wipes the PVC.
#
#   .\aws-public-origin.ps1                 use the current ingress NLB hostname
#   .\aws-public-origin.ps1 -Origin https://esquire.example.com
# =============================================================================
param(
    [string] $Origin = "",
    [string] $AdminUser = "admin",
    [string] $AdminPass = "q"
)

$ErrorActionPreference = "Stop"

if (-not $Origin) {
    $host_ = kubectl get svc -n ingress-nginx ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
    if (-not $host_) { throw "no ingress load balancer hostname -- is ingress-nginx installed?" }
    $Origin = "http://$host_"
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
