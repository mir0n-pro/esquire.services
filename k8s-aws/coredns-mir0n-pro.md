# CoreDNS override for mir0n.pro -- why it exists

## The symptom

`cert-manager` presented the HTTP-01 challenge and then sat `pending` forever, with:

```
Waiting for HTTP-01 challenge propagation: failed to perform self check GET request
'http://aws-esquire.mir0n.pro/.well-known/acme-challenge/...':
dial tcp: lookup aws-esquire.mir0n.pro on 10.100.0.10:53: no such host
```

The name resolved perfectly from a public resolver. It did not resolve inside the cluster.

## The cause

**A negative DNS cache, and the zone's TTL for one is 24 hours.**

`mir0n.pro` publishes an SOA minimum of `86400`. That value is how long resolvers are told to remember
that a name **does not exist**. The name was looked up while diagnosing, BEFORE the CNAME was created,
so every resolver that answered then cached NXDOMAIN for a day -- including the VPC resolver that
CoreDNS forwards to. Creating the record does not clear those caches, and nothing reports it: the
challenge simply never completes.

## The fix

Forward this one zone straight to public resolvers, which had the correct answer:

```
mir0n.pro:53 {
    errors
    cache 30
    forward . 8.8.8.8 1.1.1.1
}
```

prepended to the CoreDNS Corefile in `kube-system`, then `kubectl rollout restart deployment/coredns`.

## Applying it again

```
kubectl -n kube-system edit configmap coredns      # add the stanza above the .:53 block
kubectl -n kube-system rollout restart deployment/coredns
kubectl run dnstest --rm -i --restart=Never --image=busybox:1.37 --command -- nslookup aws-esquire.mir0n.pro
```

## When it can be removed

Once the negative cache has expired -- a day after the record was created -- the VPC resolver answers
correctly and this stanza does nothing useful. It is harmless to leave, and it is one more thing that
differs from a stock cluster, so remove it on the next rebuild rather than carrying it forward.

## The lesson worth keeping

**Do not look up a DNS name before creating it.** A miss is cached as firmly as a hit, and on this zone
for a full day. Create the record first, then query it. If a name has already been missed, expect every
resolver in the path to hold that answer -- the cluster's, the VPC's, and the workstation's -- and plan
to bypass rather than wait.
