# 4. Actuator on a private port

**Status:** Accepted

## Context

Prometheus needs `/actuator/prometheus`, and the kubelet needs
`/actuator/health`. Both were on the application port, which the public
ingress routes — so exposing metrics to the scraper would have exposed heap
size, endpoint names and request rates to the internet.

## Decision

Actuator listens on `MANAGEMENT_PORT` (9090), separate from the application
port (8080). The ingress routes only 8080. Health and metrics are therefore
reachable from inside the cluster and nowhere else.

## Consequences

**Gained.** Metrics are scrapeable without being public, and no
authentication layer had to be invented for them.

**Given up.** The liveness/readiness/startup probes must point at the
management port. They are coupled: changing one without the other stalls the
rollout with pods failing their probes — which is exactly what happened when
this landed.

**Given up.** `https://api.../actuator/health` no longer answers. External
uptime monitors have to be pointed at a real endpoint instead; CI checks
readiness through the Kubernetes API rather than over HTTP.
