# SH3: CDA rate limiting + caching headers, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block A · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §I.3
> Status: 📋 designed · deps: SH1 (shipped)

## 1. Context: what already exists (do not rebuild)

- **The CDA read surface is shipped (SH1).** [ShCdaAPI](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaAPI.java) returns frozen, deterministic DTO records. SH1 §6 committed to this on purpose: *"DTOs must be deterministic and side-effect free → SH3 can wrap responses in `ETag` = hash(body) trivially."*
- **Token auth + scopes are shipped (SH1/SH2).** [ShApiTokenFilter](../../shio-app/src/main/java/com/viglet/shio/spring/security/auth/ShApiTokenFilter.java) authenticates a `Key`, is confined to `/api/v2/cda/**`, and already identifies the token (`ShApiToken.id`), the natural key for a per-token limiter.
- **Security disables the global cache-control header.** `ShSecurityConfig` calls `cacheControl().disable()`, so Spring Security does **not** stamp `no-store` on responses, our CDA `Cache-Control` survives.
- **Compression is on** (`server.compression.enabled=true`). Tomcat compresses at the connector, *after* the servlet filter chain, so a body-hash `ETag` computed in a filter is consistent across requests.

**What SH3 adds:** `Cache-Control` + `ETag` (with `If-None-Match` → `304`) on every CDA read so a CDN/browser absorbs repeat traffic, and an in-memory per-token token-bucket limiter that returns `429` + `Retry-After` when a key exceeds its budget. Together these make a public delivery key safe on the open internet.

## 2. Goal & scope

**In scope**
- `Cache-Control: public, max-age=<configurable>` on `/api/v2/cda/**` responses.
- `ETag` = hash(response body) + conditional `304` on `If-None-Match`, via Spring's `ShallowEtagHeaderFilter` scoped to the CDA namespace.
- Per-token token-bucket limiter (capacity + refill), enforced in `ShApiTokenFilter`; `429` + `Retry-After` + `X-Rate-Limit-Remaining` header.
- Config under `shio.cda.*` with sane defaults; everything tunable / disable-able.

**Out of scope (deferred / noted)**
- Distributed (multi-node) buckets. The limiter is **per-instance** (in-memory). Documented caveat; a Hazelcast-backed bucket (Hazelcast is already a dependency) is a later hardening, not SH3.
- `Last-Modified` (the design says `ETag`/`Last-Modified`, `ETag` fully covers conditional GET for the deterministic DTOs; per-resource `Last-Modified` would need a modified-date lookup per endpoint and adds no CDN value over the body `ETag`).
- Auth/scope changes (SH2 owns those).

## 3. Configuration: `ShCdaProperties` (`shio.cda.*`)

`@ConfigurationProperties("shio.cda")`, mirroring [ShAppProperties](../../shio-app/src/main/java/com/viglet/shio/property/ShAppProperties.java):

| Property | Default | Meaning |
|---|---|---|
| `shio.cda.cache.max-age-seconds` | `60` | `Cache-Control: public, max-age=N`. `0` → `no-cache` (still revalidates via ETag). |
| `shio.cda.rate-limit.enabled` | `true` | Master switch. |
| `shio.cda.rate-limit.capacity` | `120` | Bucket size (max burst). |
| `shio.cda.rate-limit.refill-tokens` | `120` | Tokens added each refill period. |
| `shio.cda.rate-limit.refill-period-seconds` | `60` | Refill cadence. |

Defaults = 120 req burst, refilling 120/min ≈ 2 req/s sustained per token.

## 4. Rate limiter: `ShCdaRateLimiter`

In-memory, lock-per-bucket token bucket. `ConcurrentHashMap<String, Bucket>` keyed by token id. Lazy refill from a monotonic clock (`System.nanoTime`, injectable as a `LongSupplier` for tests).

```java
public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}
public Decision tryConsume(String key);            // production: now = System.nanoTime()
Decision tryConsume(String key, long nowNanos);    // package-visible: deterministic tests
```
- `capacity <= 0` or `enabled == false` → always `allowed` (limiter off).
- Deny → `retryAfterSeconds` = `ceil((1 - tokens) / refillPerSecond)`, floored at 1.

## 5. Enforcement: `ShApiTokenFilter`

After the token is validated + enabled + method-gated (SH2), and **before** granting authority:
```java
Decision d = rateLimiter.tryConsume(apiToken.getId());
response.setHeader("X-Rate-Limit-Remaining", Long.toString(Math.max(d.remaining(), 0)));
if (!d.allowed()) {
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(d.retryAfterSeconds()));
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
    return; // no auth set, chain stops
}
```
Keyed by token id so two clients sharing a key share a budget; an unauthenticated/invalid key never reaches the limiter (it's already rejected).

## 6. Caching: `ShCdaCacheConfig`

A `@Configuration` registering two **servlet** filters scoped to `/api/v2/cda/*` (prefix mapping covers nested paths):

1. **`ShallowEtagHeaderFilter`** (Spring built-in): buffers the CDA response, sets a content-hash `ETag`, and returns `304 Not Modified` on a matching `If-None-Match`. Registered outer (lower order) so it wraps the controller body.
2. **`ShCdaCacheControlFilter`** (`OncePerRequestFilter`): sets `Cache-Control: public, max-age=N` (or `no-cache` when `max-age=0`) on CDA responses before the body is written, so the header is present even on a `304`.

Both run *inside* the Spring Security chain (security order `-100` < these), so auth + rate-limit decide first; only a `200` body gets an ETag.

## 7. Acceptance criteria

1. `GET /api/v2/cda/site` (200) carries `Cache-Control: public, max-age=60` and a non-blank `ETag`.
2. Re-requesting with `If-None-Match: <etag>` returns `304` with an empty body.
3. A token with `capacity=1` gets `200` then `429` (with `Retry-After` ≥ 1 and `X-Rate-Limit-Remaining: 0`) on the second call within the window; after a full refill period it is allowed again.
4. `rate-limit.enabled=false` (or `capacity<=0`) never returns `429`.
5. Rate-limit `429` and ETag/Cache-Control do not regress any SH1/SH2 behaviour (existing tests stay green).

## 8. Tests

- `ShCdaRateLimiterTest` (plain unit, injected clock): allow up to capacity, then deny with a sane `Retry-After`; deny clears after a refill period; disabled/`capacity<=0` always allows.
- `ShApiTokenFilterTest` (extend): a `capacity=1` limiter → first CDA GET authenticates, second → `429` + `Retry-After`, no auth set.
- `ShCdaCacheControlFilterTest` (MockMvc + the two filters): `Cache-Control` present on a CDA GET; `ETag` present; `If-None-Match` round-trip → `304`.

## 9. Rollout

1. `ShCdaProperties` + register on the app.
2. `ShCdaRateLimiter`.
3. Wire limiter into `ShApiTokenFilter`.
4. `ShCdaCacheConfig` (two filters).
5. Tests.
6. Document the per-instance caveat + the `shio.cda.*` knobs.

## 10. Estimate

~0.5–1 day: properties+limiter (2h), filter wiring (1h), cache config (1h), tests (2h).
