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
SH189 — `/api/v2/import` — since SH327 — `/api/v2/render/**`, — since SH344 —
`/api/v2/preview-token`, — since SH343 — `/api/v2/site-webhook/**` and — since SH437 —
`/api/v2/staticfile/**` are wrapped in
**`ShStatelessRequestMatcher`**: exempt for a caller holding no session cookie,
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
the inventory: **25 roots**, of which the writable count and the classified ones are printed by the
run. `AGENT_EQUIVALENT` means the capability has an address-native form (SH170's own resolution was to
grow `site.upsert`, not to open `POST /api/v2/site` to a token); `CONSOLE_ADMIN` means an agent
minting its own API token is the escalation Block L prevents; and **the gap list is empty** since
SH343. `/api/v2/render/post-types` (SH327), `/api/v2/preview-token` (SH344) and `/api/v2/site-webhook`
(SH343) each left the only way an entry may: the lint failed until the note was removed.

**SH437 removed a fourth, and it is the one that shows a classification can be wrong rather than
merely stale.** `/api/v2/staticfile` sat under `AGENT_EQUIVALENT` on the reasoning that MCP's
`shio_assets` tool and the SH145 projection cover it. They do not: an agent can *read* the media
library over JSON-RPC and cannot put bytes into it, because **a multipart upload is not a shape MCP
has** — which is an argument for the HTTP path being reachable, not for the gap being acceptable. The
consequence had been shipping since SH145: `shio push --content` uploads assets and every test of it
injects a `fetch`, so the feature was unreachable from a real CLI and nothing said so. Found by
driving a fidelity conversion against a running instance (SH448's suite). **When you write an
`AGENT_EQUIVALENT` note, name the verb and check it can carry the payload** — "there is a tool for
that area" is not the same claim.

**And SH343 found the limit both instruments share: neither asks what a newly-widened path
*returns*.** Narrowing CSRF on `/api/v2/site-webhook` widens who reaches its `GET` from a console
session to any token-authenticated caller — and that read was serializing the subscription's HMAC
`secret` in plain text, because the controller returns the entity directly. So the exposure closed
first (`@JsonProperty(WRITE_ONLY)`, lossless since the secret is caller-supplied and must equal the
receiver's `SHIO_WEBHOOK_SECRET`). **A reachability change is also an access change**: ask what the
path returns in the same commit as the matcher. A path that becomes writable while
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
- `BasicAuthenticationFilter` with custom `ShAuthenticationEntryPoint` — **and since SH450 it
  challenges on `/preview/**` and nowhere else.** Spring's own entry point sends
  `WWW-Authenticate: Basic`, which opens the browser's native credential dialog; that is the wrong
  answer to every unauthenticated console `fetch`, because the React app has a login page and a
  native dialog in front of it is unanswerable — dismiss it and the console is blank. So the entry
  point was replaced with one that answers JSON, and the replacement was right for the API and wrong
  for the one route a **browser** is meant to open with Basic. Measured: `shio snapshot --against`
  reported `100.00% of pixels` on a byte-identical replica, and the PNG was a white page reading
  `{"error":"Unauthorized"}` — Playwright's `httpCredentials` answers a *challenge* and never sends
  `Authorization` unprompted, so with no header there was nothing to answer. The rule is **a path,
  not a heuristic**: `Accept: text/html` would have caught console fetches too, and a path is
  checkable. `ShPreviewChallengeTest` asserts both directions, because the second one is the reason
  the entry point exists at all. The body stays the same JSON either way — a browser acts on the
  header and never renders that body, so making the two responses differ further would be a second
  contract with no reader.
  **And since SH459 it answers 404 for a path no controller claims.** Measured: `GET /fonts/x.woff2`
  and `GET /nope` answered **401** while `/assets/missing.js` answered 404 — the only difference
  being that `/assets/**` is on the `permitAll` list, so it reached the dispatcher and got the truth.
  Every other root path fell through to `anyRequest().authenticated()`, so a visitor asking for a
  missing font was told to authenticate for a file that exists nowhere. **Answered here rather than
  by widening `permitAll`, and that is the safer of the two**: permitting the path would make the
  resource handler anonymously reachable, a change to *what is served*; this changes only *what the
  refusal says*, and nothing new is readable. The condition is asked of **every**
  `RequestMappingHandlerMapping` — the actuator contributes a second, and a single-bean injection
  does not merely miss it, it throws `NoUniqueBeanDefinitionException` inside the entry point and
  turns the 401 into a 500. A *resource* handler is deliberately not consulted: Spring Boot maps
  `classpath:/public/` at `/**`, so it claims every path and asking it would make the rule never
  fire. SH167's default is untouched — a new controller is claimed the moment it exists, which is
  what keeps the console's login flow reading a 401 — and an undecidable mapping
  (`HttpRequestMethodNotSupportedException`: the path *is* a controller's, only the verb is wrong)
  counts as claimed, so this can never turn a real route's 401 into a 404. 404 is also the *less*
  disclosing answer: 401 tells a prober a path is behind a door.
- Login flow: React sends `GET /api/v2` with Basic auth header via `fetch()`, then redirects to `/console`

### CORS

**This section described a mechanism that did not exist, and SH637 is what found it.** It claimed a
`WebMvcConfigurer.addCorsMappings` for `/api/**` with `allowedOriginPatterns("http://localhost:*")`
and `allowCredentials(true)`. There was no such method and no `CorsConfigurationSource` bean
anywhere in `shio-app` — so `http.cors(Customizer.withDefaults())` configured **nothing**, and every
cross-origin request failed preflight. Nobody noticed because nothing needed CORS: the console
reaches the API same-origin through the dev proxy. It surfaced only when SH633 shipped an endpoint
whose whole purpose is to be called from another origin, and it had been read at least once by
somebody checking the CORS posture, who would have come away satisfied. **A doc describing a control
that is absent is worse than no doc.**

What exists now:

- `http.cors(Customizer.withDefaults())` in `ShSecurityConfig`, as before.
- **One** `CorsConfigurationSource`, `ShFormCorsConfig`, registering **only**
  `/api/v2/cda/form/**`: `POST`/`OPTIONS`, headers `Content-Type` and `Key`,
  `allowCredentials(false)` (the endpoint authenticates by header, not cookie), `maxAge` 1h.
  `UrlBasedCorsConfigurationSource` returns `null` for any other path, which means no CORS headers —
  byte-identical to the previous behaviour everywhere else.

**CORS is not the control here, and the split is deliberate.** CORS protects the *browser*; a
non-browser client ignores it, so an origin allowed only there is allowed only against callers who
were going to behave. The control is `sh_site_form.allowed_origins`, checked against the request's
`Origin` in `ShFormSubmissionAPI` — per site, because a form token names one site and a global list
would let any configured origin post to every site's form. Empty means **no browser origin**, and a
request with no `Origin` at all passes: that header is something a browser adds, so refusing its
absence would break the documented `curl` while stopping nobody. Matched **exactly** — suffix
matching is how `acme.com` comes to accept `evil-acme.com`.

Per-site origins are *not* resolved in the CORS layer, and that was tried first: `ShTenantFilter` is
registered after `BasicAuthenticationFilter` while CORS runs at the head of the chain, so a
`@TenantId` read at preflight time runs with no tenant resolved and answers for the wrong one.

### Security Config Key Points
- Uses `PathPatternRequestMatcher.withDefaults()` with `mvc.matcher()` (same as Turing)
- Filter order: `addFilterAfter(ShCsrfHeaderFilter, BasicAuthenticationFilter.class)` (NOT CsrfFilter.class)
- Logout deletes cookies: `JSESSIONID`, `XSRF-TOKEN`
- `/api/v2/csrf` must be in `permitAll()`
- **`/preview/**` is NOT in `permitAll()`, and putting it back makes every draft public.**
  It was there — from before the renderer existed, when `/preview` meant something else on the
  legacy console. SH183 then built `GET /preview/{site}/{url}` on that path, defaulting to
  `DRAFT`, and inherited the entry. The first matching rule wins, so the route never reached the
  `anyRequest().authenticated()` that `ShRenderAPI`'s own class note says it sits under, and
  **every unpublished draft was readable by anyone who could reach the port**. Measured rather
  than reasoned: an anonymous GET answered **404** for an unknown site — meaning it had already
  reached the renderer — and **200** for `/preview/_shio/editor.js`. A curator's browser carries a
  session, so the console and the Universal Editor never noticed. `ShPreviewIsNotPublicTest`
  refuses the next reopening, from the outside and with no credentials, because a test over the
  config would only restate it.
- **The general rule this is an instance of:** an allow-list entry outlives the route it was
  written for. Both halves read correctly on their own — the entry names a path that once meant
  something else, the new controller's comment names the rule it believes it inherits — and
  nothing anywhere disagrees out loud. When you mount a controller on an existing path prefix,
  the question is not "is this route safe" but **"what does the allow-list already say about this
  prefix"**.
- **`/sites/**` *is* in `permitAll()`, and since SH451 that is deliberate rather than inherited.**
  It was the same shape as the entry above — left over from 0.3.x's `com.viglet.shio.website`,
  pointing at nothing — and the difference is only that nothing served there, so it was a latent
  hazard instead of a live one. `ShSitesAPI` now mounts the public delivery route on it *because*
  it is anonymous, and every other guard is inverted to match: **PUBLISHED only** (the route takes
  no `state` parameter, so there is no way to ask it for a draft), no banner, no editor bridge,
  cacheable, indexable. Asked the question the bullet above prescribes, and answered it in the
  direction the allow-list already pointed — which is the one case where inheriting an entry is
  right, and it is right only because the route was designed to the entry rather than the other
  way round.


## The allow-list is checked from both sides now (SH363)

SH359's shape was not a widened permission. `mvc.matcher("/preview/**")` was written when
`/preview` meant something on the legacy console; the console went, the entry stayed matching
nothing, and years later SH183 mounted `GET /preview/{site}/{url}` — defaulting to `DRAFT` — on the
prefix. The first matching rule wins, so a **dead entry adopted a new route** and every unpublished
page on the instance became readable by anyone who could reach the port. Both halves read correctly
on their own, which is why no review could have found it.

Two instruments, deliberately approaching from opposite directions, because either alone is blind
to what the other sees:

- **`ShAnonymousRouteAccessTest` works from the request in.** Every mapping a Shio controller
  registers is covered by one line of a table declaring `PUBLIC` or `AUTHENTICATED`, and an
  anonymous GET is issued against each: `401` where the classification says governed, *not* `401`
  where it says public. Spring Security authorizes before dispatch, so the answer holds whatever
  the handler does. Mount a controller on a new path and the build fails naming it, which is the
  moment SH359 never had.
- **`ShAllowListIsGroundedTest` works from the config out**, and it is the one that catches the
  actual cause. A `permitAll()` entry matching **no route at all** has nothing to probe, so no
  request can find it — it is invisible to the test above by construction. This one reads the
  literals out of `ShSecurityConfig` (resolving constant references, because half the entries are
  written that way) and fails on any that denotes nothing and is not declared in
  `NOT_A_CONTROLLER` with the reason it is public. That map is read in **both** directions: an
  exemption whose allow-list entry has gone is deleted, because kept it pre-approves whoever adds
  that path back. Two named negatives carry the incident itself — `/preview/**` must not be listed,
  `/sites/**` must be.

It reads the source on purpose rather than for convenience. Spring's rules are not introspectable
as a list once built, and asking the container which paths are permitted would interrogate the
object the config produced — restating the config, which is what SH363 ruled out. The allow-list is
a literal in one file, and the question is whether each literal still denotes anything.

**Proved by injecting one.** A `/ghost-of-a-retired-console/**` added to a `permitAll()` block
failed the build naming the entry; the line was then reverted. A lint that has only ever been run
on a clean tree is green for two indistinguishable reasons.

## Form submissions: the door is scoped, and it is never anonymous (SH364)

SH247 rendered the fields and deliberately left the receiving half open, because a public endpoint
that writes content is a security decision and not a rendering one. SH364 took it. **Submissions
are token-scoped; Shio grows no anonymous write route.** The rejected alternative is the 2018 shape
— a genuinely public endpoint bounded only by site config — and it was rejected one iteration after
SH363 shipped an instrument whose entire purpose is proving no unauthenticated write route exists
by accident.

**The token is public by construction, so the scope is the control rather than the secrecy.** It
sits in a page a stranger can read. A `FORM` scope therefore names one site and may do exactly one
thing: create a `DRAFT` post of an eligible type in that site's configured folder. No publish, no
read, no move, no second site.

**Eligibility excludes every system post-type, and that predicate carries the whole severity.**
`PageLayout`, `Region` and `Theme` are Handlebars, compiled and executed by the renderer — so a form
that could create one is not a spam problem, it is a stranger writing a template this instance
runs. The rule is the site's allow-list *intersected with* "not a system type": an allow-list alone
still lets an operator name `Theme` by mistake.

**The destination is site configuration, never a template setting.** This is the one part of 2018's
`ShFormConfiguration` not adopted: it carried `folder` on the template, and a folder chosen in a
template is a folder an attacker chooses, because a template is content and content is what a
compromised author edits. `/api/v2/site-webhook` is the precedent — a table keyed by site, its own
API, no column on `ShSite`.

**Owner is the token.** An anonymous submission has no user to attribute, and naming the token is
both true and what an operator needs when one form starts producing rubbish. Retention is a
non-question: it is a post, so the trash, the export, the ACL and the agent surface already apply.

`ShRenderFormTest.theHelperNamesNoDestination` holds the half that is decidable before the endpoint
exists — `{{#form}}` emits no `action`, no `/api/` path and no token, and the page's single `action`
stays the author's. The page says what the form is *about*; the site says where it *goes*; neither
can answer the other's question. SH631 (the scope), SH632 (the site's destination) and SH633 (the
endpoint) build it, and SH633 must classify its route `AUTHENTICATED` in
`ShAnonymousRouteAccessTest` — the one endpoint on the product where a future reader is likeliest
to reason it "should" be allow-listed.

## The FORM scope, and the one line that makes it a scope rather than a label (SH631)

SH364 settled that submissions are token-scoped and never anonymous. `ShApiTokenScope.FORM` is that
credential, and it is the first scope bounded by **data** — a site, a folder, a type list — where
the other four are bounded by verb.

**It carries `ROLE_FORM` and nothing else.** Every scope above it starts from `ROLE_CDA` +
`ROLE_USER` and *adds* capability, which is right while the weakest of them may still read the
delivery API. A form token may not: it rides in a page a stranger can read, so `ROLE_CDA` would hand
the whole site to anyone who viewed the source — the privilege escalation §XII forbids, arriving
through the one credential that is public by construction. `authoritiesFor` therefore **returns
before** the common grant rather than subtracting afterwards, which is the difference between a rule
and an omission somebody later "tidies".

**A non-GET method only under `/api/v2/cda/form`.** Bounded in the filter rather than left to the
authorization rule, so a form token cannot POST elsewhere under `/api/v2/cda/**` even if a future
mapping accepted `ROLE_FORM` by accident. Under the CDA namespace on purpose: a submission is
delivery-side traffic from a public page, so it inherits the confinement, the rate limiter and the
`Key` header the other delivery surfaces already have, instead of becoming a fourth write contract
(P10).

**`ShFormEligibility` is the floor the endpoint's severity turns on.** The rate limit, the honeypot
and the `DRAFT` default each bound how *much* or how *visible* the damage is; this bounds whether
there is a class of damage at all, because `PageLayout`, `Region` and `Theme` are Handlebars the
renderer compiles and executes. It refuses a platform type **two ways at once** — by its `system`
flag *and* by its name — deliberately, because those fail differently: the flag is data an import or
a restore can carry wrongly, and the name list is code that cannot describe a type this build has
not heard of. A type satisfying one and not the other is exactly the case worth refusing. `Redirect`,
`SiteScripts` and `File` are on the list too: a submission that could write a `Redirect` could point
any URL of the site anywhere, and one that could write `SiteScripts` could add a `<script src>` to
every page — the same defect in different clothes.

Every default points at refusal: an unresolved type is not eligible, and an empty allow-list invites
nothing rather than everything. `eligibleNames` exists so SH633's refusal can tell an operator what
they *could* have typed (P6) instead of leaving them to discover it by absence.

The endpoint is SH633 and the site's allow-list and folder are SH632; until both land a `FORM` token
authenticates on a path that answers 404, which is inert and is the point of shipping the bound
first.

## Where a rendered form posts (SH633)

`POST /api/v2/cda/form/{site}` writes **one `DRAFT` post** into the site's configured folder, or
refuses. It is deliberately ordinary, because SH631 bounded the scope and SH632 owned the
destination — and there is no submissions subsystem, which is the argument rather than an omission:
**a submission is a post**, so the trash, the export, the ACL, the review queue and the agent
surface already apply to it.

**`DRAFT` is not a parameter and never becomes one.** SH7's review queue is the moderation step, so
a submission is visible to a curator and invisible to a visitor, and nothing here has to decide what
"approved" means. The rate limit is the CDA bucket `ShApiTokenFilter` has already spent before the
method runs — a form token *is* an API token, so what this needed was a tight default and not a
mechanism.

**The honeypot answers exactly as a success does.** `shio_hp` is a fixed field name, rendered hidden
and required to be empty; a tripped one returns `{"received": true}` and writes nothing. A refusal
naming the field would be a free description of how to evade it, so there is no signal to tune
against. When empty it is *stripped* from the stored data — a spam-control artefact in every content
record is not content.

**Which refusals may teach.** P6 is bounded here in a way it is nowhere else on the agent surface,
because the caller is a browser nobody controls. A **configuration** refusal is a message to the
operator and says everything: the site invites nothing, the folder is missing, the type is not on
the list, here is what is. It prints only the *eligible* names — a site whose list says `Theme` must
not be told it invites `Theme`, or an operator goes looking for a bug that is their configuration. A
**honeypot** refusal says nothing at all.

**A missing folder refuses rather than creating one**: a public path that can create folders is a
public path that can fill a disk. Owner is `form-token:<id>` — the **token**, not a person: an
anonymous submission has no user to attribute, and naming the credential is what an operator needs
when one form starts producing rubbish, without identifying a visitor.

Two ordering facts that are easy to get wrong and would both fail closed:

- The `POST /api/v2/cda/form/**` rule must precede `POST /api/v2/cda/**`, which requires
  `ROLE_CDA_WRITE` — a `FORM` token carries neither that nor `ROLE_CDA`, so without it the endpoint
  is unreachable by the only credential meant to call it. **Both** security chains carry it.
- It has its **own** `AUTHENTICATED` row in `ShAnonymousRouteAccessTest`, ahead of the
  `/api/v2/cda/**` row that already covered it. This is the one endpoint in the product whose whole
  purpose is to be reachable from a public page, so it is exactly where a future reader reasons it
  "should" be allow-listed. It must not be: the page carries the token, and the token is the
  authorization — allow-listing it would turn a scoped write into an anonymous one.

## The form endpoint's origins (SH637)

SH633 shipped an endpoint whose whole purpose is to be called from another origin, and it could not
be: `http.cors(Customizer.withDefaults())` had **no `CorsConfigurationSource` bean** to configure it,
so every preflight failed. Correct, tested, and unreachable from its only caller. Nothing had
noticed because nothing needed CORS — the console reaches the API same-origin through the dev proxy.

**`ShFormCorsConfig` registers one path.** `UrlBasedCorsConfigurationSource` returns `null` for a
path it does not know and a null configuration means no CORS headers, so opening
`/api/v2/cda/form/**` leaves every other endpoint byte-identical to before. `allowCredentials` is
`false`: the endpoint authenticates by `Key` header, not cookie.

**CORS is not the control, and that decides the whole shape.** It protects the *browser*; a
non-browser client ignores it, so an origin allowed only there is allowed only against callers who
were going to behave. The control is `sh_site_form.allowed_origins`, checked against `Origin` in the
endpoint — per site, because a form token names one site and a global list would let any configured
origin post to every site's form, which is the scope SH631 spent a task bounding. So the CORS layer
is deliberately permissive (`*` patterns) and the endpoint is not.

Three decisions in the check: **matched exactly**, because suffix matching is how `acme.com` comes
to accept `evil-acme.com`; **empty means none**, the direction every default on this path takes; and
**no `Origin` at all passes**, because that header is something a browser adds — refusing its absence
would break the documented `curl` while stopping nobody, since a caller who wanted to lie about it
would simply omit it. What the header is good for is the case it is honest in: a browser cannot
forge it, so a page on an unnamed origin cannot spend the token sitting in its own markup.

**Per-site origins are not resolved in the CORS layer, and that was the first attempt.**
`ShTenantFilter` is registered after `BasicAuthenticationFilter` while CORS runs at the head of the
chain, so a `@TenantId` read at preflight time runs with no tenant resolved and answers for the
wrong one. The layering is not a preference; it is what the filter order permits.

The stale `### CORS` section above is the other half of this task and the more uncomfortable one: it
documented an `addCorsMappings` the code never had, and would have satisfied anybody auditing the
posture.
