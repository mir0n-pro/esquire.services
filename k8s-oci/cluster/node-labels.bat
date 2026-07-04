@echo off
rem ===========================================================================
rem Label OKE nodes for tier-based scheduling.
rem
rem v1.2.10 Always-Free layout: 4 nodes -- 1 infra + 3 app (each 1 OCPU / 6 GB,
rem the full 4 OCPU / 24 GB free envelope). The x2 service fleet spreads across
rem the 3 app-tier nodes (see the topologySpread setting in k8s-oci/values/*).
rem
rem Set INFRA_NODE from `kubectl get nodes` (node IPs change when the pool is
rem resized or its nodes cycle). Every OTHER worker node is labelled app-tier
rem automatically, so a re-shaped pool needs no per-node edits here.
rem ===========================================================================

set INFRA_NODE=10.0.1.77

kubectl label node %INFRA_NODE% tier=infra --overwrite

rem All non-infra worker nodes -> app tier.
for /f "tokens=1" %%n in ('kubectl get nodes --no-headers') do (
  if not "%%n"=="%INFRA_NODE%" kubectl label node %%n tier=app --overwrite
)

kubectl get nodes --show-labels
