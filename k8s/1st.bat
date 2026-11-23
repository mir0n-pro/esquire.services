REM Confirm context first!                                                                 
  kubectl config current-context                                                             
rem  kubectl config current-context  
  REM should print: docker-desktop       
kubectl config use-context docker-desktop
