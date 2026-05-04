@echo off
rem ===========================================================================
rem Label OKE nodes for tier-based scheduling per WhereToGo.md plan.
rem Node names come from `kubectl get nodes`. Edit the placeholders below.
rem ===========================================================================

set INFRA_NODE=10.0.1.77
set APP_A_NODE=10.0.1.93
set APP_B_NODE=10.0.1.131

kubectl label node %INFRA_NODE% tier=infra --overwrite
kubectl label node %APP_A_NODE% tier=app   --overwrite
kubectl label node %APP_B_NODE% tier=app   --overwrite

kubectl get nodes --show-labels
