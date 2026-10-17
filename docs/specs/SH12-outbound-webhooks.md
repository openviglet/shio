# SH12: Outbound webhooks on publish/unpublish/delete, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §III.1
> Status: 📋 designed · deps: SH1 (shipped)

## 1. Context: what already exists (do not rebuild)

- **The unified write path is the single chokepoint.** [ShPostUnifiedWriteService](../../shio-app/src/main/java/com/viglet/shio/migration/unified/ShPostUnifiedWriteService.java) is where `publish` / `unpublish` / `delete` actually complete (against `sh_post_unified`). Both the console API ([ShPostUnifiedAPI](../../shio-app/src/main/java/com/viglet/shio/api/post/unified/ShPostUnifiedAPI.java)) and the CDA write surface ([ShCdaWriteService](../../shio-app/src/main/java/com/viglet/shio/api/cda/ShCdaWriteService.java), SH10) route through it, so hooking it once covers every transition.
- **The consumer contract is already shipped (SH9).** [`create-shio-app`](../../create-shio-app)'s `app/api/revalidate/route.ts` verifies an HMAC-SHA256 signature over the **raw request body**, sent as `x-shio-signature: sha256=<hex>`, and expects a JSON body `{ event, siteId, postId, url, tags?, occurredAt? }`. SH12 is the **emitter**; its output MUST satisfy that receiver byte-for-byte. The contract is documented in [recipes/nextjs-rendering.md](../recipes/nextjs-rendering.md) §3.
- **No event/listener infrastructure exists yet.** There is no `ApplicationEventPublisher`, `@EventListener`, or `@Async` in the codebase. SH12 introduces a small, self-contained one rather than coupling HTTP I/O directly into the write service.
- **Per-resource config follows `ShApiToken`.** [ShApiToken](../../shio-app/src/main/java/com/viglet/shio/persistence/model/token/ShApiToken.java) is the template: a dedicated `@Entity @Table` with `@TenantId`, a thin repository, and a CRUD controller at `/api/v2/api-token` (no service layer). SH12 mirrors this for per-site webhook subscriptions.

**What SH12 adds:** per-site webhook **subscriptions** (target URL + HMAC secret + which events) stored in Shio, and an emitter that fires a signed POST after each publish/unpublish/delete **commits**. This is the backbone for incremental static regeneration (SH9) and any downstream integration.

## 2. Goal & scope

**In scope**
- `ShSiteWebhook` entity (per-site target: url, secret, event filter, enabled) + repository + CRUD controller at `/api/v2/site-webhook`.
- A Spring `ApplicationEvent` (`ShPostLifecycleEvent`) published from `ShPostUnifiedWriteService` after each `publish`/`unpublish`/`delete`.
- A `@TransactionalEventListener(phase = AFTER_COMMIT)` dispatcher that signs (HMAC-SHA256 over the raw body) and POSTs the SH9 payload to each enabled, subscribed target, with bounded retry and back-off, off the request thread.
- `shio.webhook.*` config (`ShWebhookProperties`): enable switch, connect/read timeout, max attempts, retry back-off.
- A Liquibase changeset for the new tables.

**Out of scope (deferred)**
- **Scheduled publish/unpublish (SH15)** reuses this emitter on a sweep, not built here.
- Delivery log / per-attempt audit UI, dead-letter queue, exponential jitter tuning. The retry here is a small in-process best-effort loop; durable delivery is a later concern.
- A console UI for managing subscriptions (mirrors the api-token list). The REST CRUD ships now; the React screen is a follow-up (tracked with SH19 console work).

## 3. Data model

`ShWebhookEventType` (`com.viglet.shio.persistence.model.webhook`): `PUBLISH`, `UNPUBLISH`, `DELETE`. The wire value is the **lower-cased** name (`publish`/`unpublish`/`delete`) to match the SH9 receiver.

`ShSiteWebhook` (`@Entity @Table(name = "site_webhook")`), mirroring `ShApiToken`:
```java
@Id @GeneratedValue(strategy = GenerationType.UUID) String id;
@TenantId @Column(name = "TENANT_ID", updatable = false) String tenantId;
@Column(name = "site_id") String siteId;          // subscription is per-site; null/empty = all sites
@Column(name = "name", length = 100) String name;
@Column(name = "target_url", length = 1024) String url;
@Column(name = "secret") String secret;            // shared HMAC secret (== SHIO_WEBHOOK_SECRET on the Next side)
@ElementCollection(fetch = EAGER) Set<ShWebhookEventType> events;  // empty = all three
@Column(name = "enabled") boolean enabled = true;
@Column(name = "created_date") Instant createdDate;
@Column(name = "owner") String owner;
```
The `events` element collection lives in `site_webhook_event(site_webhook_id, event_type)`. An empty set means "all events" (the common case).

## 4. Lifecycle event (decoupling I/O from the write transaction)

`ShPostLifecycleEvent` is an immutable snapshot captured **inside** the write transaction (so it survives the row being deleted on unpublish/delete):
```java
public record ShPostLifecycleEvent(ShWebhookEventType type, String postId, String siteId,
                                   String url, Instant occurredAt) {}
```
`ShPostUnifiedWriteService` injects `ApplicationEventPublisher` and, after the repository call:
- `publish(id)` → reads the published row's `furl`/site, publishes `PUBLISH`.
- `unpublish(id)` → captures `furl`/site from the `PUBLISHED` row **before** deleting it, publishes `UNPUBLISH`.
- `delete(id)` → captures from the first existing row **before** deleting, publishes `DELETE`.

`url` is the friendly URL normalised to a leading slash (`/blog/hello`); `null`/blank furl yields a `null` url (the receiver still revalidates `/`).

## 5. Dispatcher

`ShWebhookDispatcher`:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`: fires **only** after the DB commit, so a rolled-back publish never emits. (A non-transactional fallback `@EventListener(condition=...)` is **not** added; all three writers are `@Transactional`.)
- Looks up enabled `ShSiteWebhook` rows matching the event's `siteId` (and whose `events` set is empty or contains the type), builds the SH9 JSON body once, and for each target dispatches off the request thread via a small fixed `ExecutorService`.
- **Signing:** `mac = HMAC-SHA256(secret, rawBodyBytes)`; header `x-shio-signature: sha256=<lowercase hex>`. Body `Content-Type: application/json`. Identical bytes are signed and sent (no re-serialisation between sign and send).
- **Retry:** up to `shio.webhook.max-attempts` (default 3); a non-2xx response or `IOException` triggers a `retry-backoff-millis` (default 2000) sleep × attempt before the next try. Exhausted attempts log a warning and drop (best-effort; no DLQ, out of scope §2).
- Guarded by `shio.webhook.enabled` (default `true`).

## 6. Config (`shio.webhook.*`)

`ShWebhookProperties` (`@ConfigurationProperties("shio.webhook")`), mirroring `ShCdaProperties`:
```
shio.webhook.enabled=true
shio.webhook.connect-timeout-millis=3000
shio.webhook.read-timeout-millis=5000
shio.webhook.max-attempts=3
shio.webhook.retry-backoff-millis=2000
```
All commented (defaults shown) in `application.properties`.

## 7. CRUD (`/api/v2/site-webhook`)

`ShSiteWebhookAPI`, mirroring `ShApiTokenAPI` exactly (constructor-injected repo, `saveAndFlush`, `@Slf4j`, `Principal` for `owner`):
- `GET /api/v2/site-webhook`: list ordered by name.
- `GET /api/v2/site-webhook/{id}`: one or 404.
- `POST`: create; `secret` auto-generated (25-char) when the body omits one; `owner` from `Principal`; `createdDate = Instant.now()`.
- `PUT /{id}`: update name/url/siteId/events/enabled (and `secret` only if a non-blank one is supplied, never clobber to blank).
- `DELETE /{id}`: `{ "deleted": true }`.

Falls under the existing `anyRequest authenticated` rule (console-only), like `/api/v2/api-token`. **Not** on the CDA token surface.

## 8. Acceptance criteria

1. Publishing a post fires exactly one POST per enabled, matching subscription, with body `{event:"publish", siteId, postId, url, occurredAt}` and a valid `x-shio-signature: sha256=<hex>` over the exact bytes sent.
2. The signature verifies against the SH9 receiver's `verifySignature` (HMAC-SHA256, raw body, constant-time), a wrong secret produces a body whose signature the receiver rejects with 401.
3. `unpublish` and `delete` still emit the right `siteId`/`url` even though the row is gone after the call (snapshot captured pre-delete).
4. A rolled-back transaction emits **nothing** (AFTER_COMMIT).
5. A subscription with a non-empty `events` set only receives those types; an empty set receives all three. A subscription for a different `siteId` is not called.
6. A failing target is retried up to `max-attempts`, then dropped with a logged warning; the publish HTTP response is unaffected (dispatch is off-thread).
7. `shio.webhook.enabled=false` suppresses all emission.

## 9. Tests

- `ShWebhookSignatureTest` (unit): the hex HMAC the dispatcher computes equals the openssl reference for a known body+secret (locks the SH9 contract).
- `ShWebhookDispatcherTest` (`@SpringBootTest` or sliced): seed two subscriptions (one matching event filter, one not, one for another site); publish a `ShPostLifecycleEvent`; assert only the matching target is POSTed (using a stub `HttpClient`/local server), payload shape, and that a non-2xx is retried `max-attempts` times.
- `ShPostUnifiedWebhookEventTest`: assert `publish`/`unpublish`/`delete` each publish one `ShPostLifecycleEvent` with the correct type and a pre-delete snapshot (crit 3), via an `@EventListener` test capture; and that no event escapes a rolled-back tx (crit 4).

## 10. Estimate

~1 day: entity+repo+enum+migration (2h), event + write-service hooks (2h), dispatcher + signing + retry + properties (3h), CRUD controller (1h), tests (2h).
