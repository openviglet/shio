# Security — CSRF, auth, CORS

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Security Configuration

### CSRF (follows Turing pattern)
- Backend: `CookieCsrfTokenRepository` (HttpOnly cookie)
- `ShCsrfHeaderFilter` forces lazy token materialization + adds `X-XSRF-TOKEN` response header + `Access-Control-Expose-Headers`
- `ShCsrfController` at `GET /api/v2/csrf` returns token as JSON
- Frontend: `src/lib/axios.ts` interceptor fetches token from `/v2/csrf` before PUT/POST/DELETE, reads from response header, auto-retries on 403

**Every exemption is session-narrowed, and the one that is not is argued (SH167).**
`/api/v2/post-type`, `/api/v2/review/**`, `/api/v2/cda/**`, `/api/v2/agent/**`, `/mcp`, — since
SH189 — `/api/v2/import` — since SH327 — `/api/v2/render/**` and — since SH344 —
`/api/v2/preview-token` are wrapped in **`ShStatelessRequestMatcher`**: exempt for a caller holding no session cookie,
CSRF-checked for one that does. A blanket exemption is only safe while no cookie-bearing
client reaches the path, which was true of the post-type API right up to the commit (SH117)
that pointed the React editor at it — and nothing in that commit would have noticed. The
agent namespace is the one that made this urgent rather than tidy: it is gated by
`anyRequest().authenticated()` and nothing more, so a curator's session already reaches
`POST /api/v2/agent/batch`, which deletes content. The CDA is a pre-emption — a
session-bearing CDA write already failed for want of `ROLE_CDA_WRITE`, so narrowing it breaks
nothing that works.
**`ShCsrfExemptionLintTest` fails the build on a new blanket entry**, because the failure mode
is a *future* caller rather than a current request, which no functional test can watch for. To
add one, name the constant in its allow-list with the reason a forged request there can do
nothing an unauthenticated one could not. `/graphql` is the only current member (no
`Mutation`; GraphiQL is a cookie-bearing browser) — a premise of exactly the shape that expired
before, so **since SH234 it is enforced, not written down**: the same lint fails the build the
day a `Mutation` appears in `schema.graphqls` or a `.mutation(` in `ShGraphQLSchema`, and its
message carries the decision made in advance — **narrow `GRAPHQL_PATH` with
`ShStatelessRequestMatcher` and set `spring.graphql.graphiql.enabled=false`**. A
mutation-capable delivery endpoint does not get a cookie-bearing playground in the console's
browser; keeping GraphiQL would mean shipping a CSRF-aware replacement or accepting cross-site
writes on the delivery API, and neither is worth a query playground.

**And the inverse question has its own lint (SH339): which mutating paths can an agent write to at
all?** `ShCsrfExemptionLintTest` asks whether an exemption is a *decision* — the right question from
the security side, which leaves the operator side unasked, and the cost of that gap is a recurring
class: SH170 (creating a site needs session + CSRF, worked around by hand in `e2e/provision.mjs`),
then SH327 (`/api/v2/render/**`) and SH335 — the same defect three times, each found by a human doing
a build rather than by a build failing. `ShStatelessReachabilityLintTest` asserts over the same
configuration that **every mutating controller root is stateless-writable or classified**, and prints
the inventory: **25 roots, 8 writable**, and of the 17 that need a session, 5 are `AGENT_EQUIVALENT`
(the capability has an address-native form — SH170's own resolution was to grow `site.upsert`, not to
open `POST /api/v2/site` to a token), 9 are `CONSOLE_ADMIN` (an agent minting its own API token is the
escalation Block L prevents), and **1 is a real gap**: `/api/v2/site-webhook` (SH343).
`/api/v2/render/post-types` (SH327) and `/api/v2/preview-token` (SH344) were the other two and are
closed — which is the only way an entry may leave this list: the lint failed until the note was
removed. A path that becomes writable while
still listed fails the build — SH142's rule, so the inventory cannot rot. It is deliberately distinct
from SH338's `ShAgentP1ConformanceTest`: that one asks whether a capability has an agent route at
all, this one asks whether the route it has works without a cookie, and a capability can pass either
and fail the other.

**A CSRF refusal carries a body, and the body depends on who is asking (SH339).** Measured first: a
POST to `/api/v2/render/post-types`, `/api/v2/site`, `/api/v2/site-webhook` or
`/api/v2/preview-token` answered **403, no content type, empty body** — SH189's defect on the surface
whose caller is most often a program, and what made SH327 cost an experiment rather than a read.
`ShCsrfAccessDeniedHandler` writes an SH80 problem instead, and **branches on the session cookie**:
a browser gets `csrf-token-required` and is told to fetch `GET /api/v2/csrf`; a stateless caller gets
`csrf-token-unobtainable`, because it cannot obtain a token at all — for it the 403 is a fact about
the *route*, so the fix points at `POST /api/v2/agent/batch`. Telling that caller to fetch a token
would be advice it cannot follow, which is why both messages are asserted; one of them alone would
pass on a handler that gives everybody the browser's answer. Three rules: the **status stays 403**
(the console's axios interceptor re-fetches and retries on exactly that, so a teaching body is purely
additive); it is wired onto **`CsrfFilter` alone** via an `ObjectPostProcessor` and *not* as the
chain-wide `accessDeniedHandler`, since a CSRF rejection and an authorization denial have different
remedies and one handler for both would have to guess which it is sending; and the body is built as an
ordered map rather than by serializing `ProblemDetail`, because an order that reaches the wire is a
decision (P5).

**On the CDA, an explicitly presented `Key` beats an existing session (SH233).** `ShApiTokenFilter`
used to return early whenever anything had authenticated, which read as conservative and was why
the Universal Editor could not save: the console is **same-origin** with the API, so its session
cookie rides along whatever the client sets `withCredentials` to (that flag governs cross-origin
requests only), `SecurityContextHolderFilter` restores the session first, the `Key` header was
never read, and the PATCH authenticated as a curator with no `ROLE_CDA_WRITE`. Sending a key is a
request to be *that* principal; the token's scope, allow-list, expiry and rate limit still bound
it, and a key that cannot authenticate **leaves the context untouched** rather than clearing it.
The client half is the same fix from the other end: `universal-editor.service.ts` uses the shared
`@/lib/axios` singleton now, so the request carries CSRF (required of a session-bearing caller
since SH167) instead of a private `axios.create()` that silently opted out of every shared
interceptor.

### Authentication
- HTTP Basic Auth
- `HttpSessionSecurityContextRepository` for session persistence
- `BasicAuthenticationFilter` with custom `ShAuthenticationEntryPoint`
- Login flow: React sends `GET /api/v2` with Basic auth header via `fetch()`, then redirects to `/console`

### CORS
- `Customizer.withDefaults()` in security config
- `WebMvcConfigurer.addCorsMappings` for `/api/**` with `allowedOriginPatterns("http://localhost:*")`, `allowCredentials(true)`, `exposedHeaders("X-XSRF-TOKEN")`

### Security Config Key Points
- Uses `PathPatternRequestMatcher.withDefaults()` with `mvc.matcher()` (same as Turing)
- Filter order: `addFilterAfter(ShCsrfHeaderFilter, BasicAuthenticationFilter.class)` (NOT CsrfFilter.class)
- Logout deletes cookies: `JSESSIONID`, `XSRF-TOKEN`
- `/api/v2/csrf` must be in `permitAll()`

