# Security: CSRF, auth, CORS

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

## Security Configuration

### CSRF (follows Turing pattern)
- Backend: `CookieCsrfTokenRepository` (HttpOnly cookie)
- `ShCsrfHeaderFilter` forces lazy token materialization + adds `X-XSRF-TOKEN` response header + `Access-Control-Expose-Headers`
- `ShCsrfController` at `GET /api/v2/csrf` returns token as JSON
- Frontend: `src/lib/axios.ts` interceptor fetches token from `/v2/csrf` before PUT/POST/DELETE, reads from response header, auto-retries on 403

**Every exemption is session-narrowed, and the one that is not is argued (SH167).**
`/api/v2/post-type`, `/api/v2/review/**`, `/api/v2/cda/**`, `/api/v2/agent/**`, `/mcp`, since
SH189 (`/api/v2/import`) since SH327 (`/api/v2/render/**`,) since SH344,
`/api/v2/preview-token`, (since SH343) `/api/v2/site-webhook/**` and, since SH437,
`/api/v2/staticfile/**` are wrapped in
**`ShStatelessRequestMatcher`**: exempt for a caller holding no session cookie,
CSRF-checked for one that does. A blanket exemption is only safe while no cookie-bearing
client reaches the path, which was true of the post-type API right up to the commit (SH117)
that pointed the React editor at it, and nothing in that commit would have noticed. The
agent namespace is the one that made this urgent rather than tidy: it is gated by
`anyRequest().authenticated()` and nothing more, so a curator's session already reaches
`POST /api/v2/agent/batch`, which deletes content. The CDA is a pre-emption, a
session-bearing CDA write already failed for want of `ROLE_CDA_WRITE`, so narrowing it breaks
nothing that works.
**`ShCsrfExemptionLintTest` fails the build on a new blanket entry**, because the failure mode
is a *future* caller rather than a current request, which no functional test can watch for. To
add one, name the constant in its allow-list with the reason a forged request there can do
nothing an unauthenticated one could not. `/graphql` is the only current member (no
`Mutation`; GraphiQL is a cookie-bearing browser), a premise of exactly the shape that expired
before, so **since SH234 it is enforced, not written down**: the same lint fails the build the
day a `Mutation` appears in `schema.graphqls` or a `.mutation(` in `ShGraphQLSchema`, and its
message carries the decision made in advance, **narrow `GRAPHQL_PATH` with
`ShStatelessRequestMatcher` and set `spring.graphql.graphiql.enabled=false`**. A
mutation-capable delivery endpoint does not get a cookie-bearing playground in the console's
browser; keeping GraphiQL would mean shipping a CSRF-aware replacement or accepting cross-site
writes on the delivery API, and neither is worth a query playground.

**And the inverse question has its own lint (SH339): which mutating paths can an agent write to at
all?** `ShCsrfExemptionLintTest` asks whether an exemption is a *decision*, the right question from
the security side, which leaves the operator side unasked, and the cost of that gap is a recurring
class: SH170 (creating a site needs session + CSRF, worked around by hand in `e2e/provision.mjs`),
then SH327 (`/api/v2/render/**`) and SH335, the same defect three times, each found by someone doing
a build rather than by a build failing. `ShStatelessReachabilityLintTest` asserts over the same
configuration that **every mutating controller root is stateless-writable or classified**, and prints
the inventory (the roots, how many are writable and how each of the rest is classified) as well as
writing it to `shio-app/target/p1-inventory.json`, which is where a figure about it is read from
since SH974 rather than quoted here. `AGENT_EQUIVALENT` means the capability has an address-native form (SH170's own resolution was to
grow `site.upsert`, not to open `POST /api/v2/site` to a token); `CONSOLE_ADMIN` means an agent
minting its own API token is the escalation Block L prevents; and **the gap list is empty** since
SH343. `/api/v2/render/post-types` (SH327), `/api/v2/preview-token` (SH344) and `/api/v2/site-webhook`
(SH343) each left the only way an entry may: the lint failed until the note was removed.

**SH437 removed a fourth, and it is the one that shows a classification can be wrong rather than
merely stale.** `/api/v2/staticfile` sat under `AGENT_EQUIVALENT` on the reasoning that MCP's
`shio_assets` tool and the SH145 projection cover it. They do not: an agent can *read* the media
library over JSON-RPC and cannot put bytes into it, because **a multipart upload is not a shape MCP
has**, which is an argument for the HTTP path being reachable, not for the gap being acceptable. The
consequence had been shipping since SH145: `shio push --content` uploads assets and every test of it
injects a `fetch`, so the feature was unreachable from a real CLI and nothing said so. Found by
driving a fidelity conversion against a running instance (SH448's suite). **When you write an
`AGENT_EQUIVALENT` note, name the verb and check it can carry the payload**, "there is a tool for
that area" is not the same claim.

**And SH343 found the limit both instruments share: neither asks what a newly-widened path
*returns*.** Narrowing CSRF on `/api/v2/site-webhook` widens who reaches its `GET` from a console
session to any token-authenticated caller, and that read was serializing the subscription's HMAC
`secret` in plain text, because the controller returns the entity directly. So the exposure closed
first (`@JsonProperty(WRITE_ONLY)`, lossless since the secret is caller-supplied and must equal the
receiver's `SHIO_WEBHOOK_SECRET`). **A reachability change is also an access change**: ask what the
path returns in the same commit as the matcher. A path that becomes writable while
still listed fails the build, SH142's rule, so the inventory cannot rot. It is deliberately distinct
from SH338's `ShAgentP1ConformanceTest`: that one asks whether a capability has an agent route at
all, this one asks whether the route it has works without a cookie, and a capability can pass either
and fail the other.

**A CSRF refusal carries a body, and the body depends on who is asking (SH339).** Measured first: a
POST to `/api/v2/render/post-types`, `/api/v2/site`, `/api/v2/site-webhook` or
`/api/v2/preview-token` answered **403, no content type, empty body**, SH189's defect on the surface
whose caller is most often a program, and what made SH327 cost an experiment rather than a read.
`ShCsrfAccessDeniedHandler` writes an SH80 problem instead, and **branches on the session cookie**:
a browser gets `csrf-token-required` and is told to fetch `GET /api/v2/csrf`; a stateless caller gets
`csrf-token-unobtainable`, because it cannot obtain a token at all, for it the 403 is a fact about
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
- `BasicAuthenticationFilter` with custom `ShAuthenticationEntryPoint`, **and since SH450 it
  challenges on `/preview/**` and nowhere else.** Spring's own entry point sends
  `WWW-Authenticate: Basic`, which opens the browser's native credential dialog; that is the wrong
  answer to every unauthenticated console `fetch`, because the React app has a login page and a
  native dialog in front of it is unanswerable, dismiss it and the console is blank. So the entry
  point was replaced with one that answers JSON, and the replacement was right for the API and wrong
  for the one route a **browser** is meant to open with Basic. Measured: `shio snapshot --against`
  reported `100.00% of pixels` on a byte-identical replica, and the PNG was a white page reading
  `{"error":"Unauthorized"}`: Playwright's `httpCredentials` answers a *challenge* and never sends
  `Authorization` unprompted, so with no header there was nothing to answer. The rule is **a path,
  not a heuristic**: `Accept: text/html` would have caught console fetches too, and a path is
  checkable. `ShPreviewChallengeTest` asserts both directions, because the second one is the reason
  the entry point exists at all. The body stays the same JSON either way, a browser acts on the
  header and never renders that body, so making the two responses differ further would be a second
  contract with no reader.
  **And since SH459 it answers 404 for a path no controller claims.** Measured: `GET /fonts/x.woff2`
  and `GET /nope` answered **401** while `/assets/missing.js` answered 404, the only difference
  being that `/assets/**` is on the `permitAll` list, so it reached the dispatcher and got the truth.
  Every other root path fell through to `anyRequest().authenticated()`, so a visitor asking for a
  missing font was told to authenticate for a file that exists nowhere. **Answered here rather than
  by widening `permitAll`, and that is the safer of the two**: permitting the path would make the
  resource handler anonymously reachable, a change to *what is served*; this changes only *what the
  refusal says*, and nothing new is readable. The condition is asked of **every**
  `RequestMappingHandlerMapping`: the actuator contributes a second, and a single-bean injection
  does not merely miss it, it throws `NoUniqueBeanDefinitionException` inside the entry point and
  turns the 401 into a 500. A *resource* handler is deliberately not consulted: Spring Boot maps
  `classpath:/public/` at `/**`, so it claims every path and asking it would make the rule never
  fire. SH167's default is untouched, a new controller is claimed the moment it exists, which is
  what keeps the console's login flow reading a 401, and an undecidable mapping
  (`HttpRequestMethodNotSupportedException`: the path *is* a controller's, only the verb is wrong)
  counts as claimed, so this can never turn a real route's 401 into a 404. 404 is also the *less*
  disclosing answer: 401 tells a prober a path is behind a door.
- Login flow: React sends `GET /api/v2` with Basic auth header via `fetch()`, then redirects to `/console`

### CORS

**This section described a mechanism that did not exist, and SH637 is what found it.** It claimed a
`WebMvcConfigurer.addCorsMappings` for `/api/**` with `allowedOriginPatterns("http://localhost:*")`
and `allowCredentials(true)`. There was no such method and no `CorsConfigurationSource` bean
anywhere in `shio-app`, so `http.cors(Customizer.withDefaults())` configured **nothing**, and every
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
  `UrlBasedCorsConfigurationSource` returns `null` for any other path, which means no CORS headers,
  byte-identical to the previous behaviour everywhere else.

**CORS is not the control here, and the split is deliberate.** CORS protects the *browser*; a
non-browser client ignores it, so an origin allowed only there is allowed only against callers who
were going to behave. The control is `sh_site_form.allowed_origins`, checked against the request's
`Origin` in `ShFormSubmissionAPI`, per site, because a form token names one site and a global list
would let any configured origin post to every site's form. Empty means **no browser origin**, and a
request with no `Origin` at all passes: that header is something a browser adds, so refusing its
absence would break the documented `curl` while stopping nobody. Matched **exactly**, suffix
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
  It was there, from before the renderer existed, when `/preview` meant something else on the
  legacy console. SH183 then built `GET /preview/{site}/{url}` on that path, defaulting to
  `DRAFT`, and inherited the entry. The first matching rule wins, so the route never reached the
  `anyRequest().authenticated()` that `ShRenderAPI`'s own class note says it sits under, and
  **every unpublished draft was readable by anyone who could reach the port**. Measured rather
  than reasoned: an anonymous GET answered **404** for an unknown site, meaning it had already
  reached the renderer, and **200** for `/preview/_shio/editor.js`. A curator's browser carries a
  session, so the console and the Universal Editor never noticed. `ShPreviewIsNotPublicTest`
  refuses the next reopening, from the outside and with no credentials, because a test over the
  config would only restate it.
- **A share link opens it for one credential, and the entry above did not move (SH1042).** A draft
  Shio renders itself could be shown to nobody but a curator, and SH184 minted **nothing** for an
  internal front end on the reasoning that "a credential for a console-authenticated path would
  unlock nothing", correct while the path unlocked for no credential at all, and the reason the
  only previewable sites were the ones with a deployed external front end. That is the product's
  premise inverted: a site an agent has just built has neither a front end nor a stakeholder who can
  log in. Four decisions carry it, and each answers a way this could have gone wrong:
  - **A new scope, not `PREVIEW`.** `SHARE` grants only `ROLE_PREVIEW_SHARE`, which no rule outside
    the renderer accepts, `FORM`'s reasoning one surface over. Reusing `PREVIEW` was the obvious
    move and the escalation: it grants `ROLE_CDA` + `ROLE_CDA_PREVIEW`, so a recipient who lifted
    the value out of the cookie into a `Key` header would read that site's whole delivery API,
    drafts included. The confinement runs **both ways** in `ShApiTokenFilter`: only `SHARE`
    authenticates the preview, and `SHARE` authenticates nowhere else.
  - **The public route is `/api/v2/preview-share/**`, a sibling of the mint and not a path under
    `/preview/`.** That is the bullet below applied to this task's own work: an allow-list entry
    under that prefix is the thing that made every draft readable once. Being public grants
    nothing, it validates a credential and sets a cookie, and answers **404** without one.
  - **The credential is exchanged for a cookie, and leaves the URL on the first hop.** A link is
    copied, forwarded and pasted into a ticket, so a token in the query string ends up in history
    and in the `Referer` of every image on the page. The cookie is `HttpOnly` (the page is content
    somebody authored and this instance renders), `SameSite=Lax`, `Secure` when the request is, and
    `Path=/preview`: so the browser cannot send it to the console or the API even by accident.
    It is the first cookie Shio itself writes.
  - **The site is enforced at the renderer, where the site is already resolved.** The filter sees
    only a path whose first segment may be an id, a furl or a name, and a second resolver there
    would be a second opinion about SH181's order. `ShPreviewShare.allows` reads an empty allow-list
    as **nothing** rather than everything, which is where it differs from `ShApiTokenContext` by
    exactly one bit and in the only direction that matters here. The editor bridge is forced off
    whatever `?editor=true` says, and the banner stays.

  **And to one page (SH1110).** `api_token.path_prefix` (v2026.3.49) holds the page a share was
  minted for, `ShPreviewTokenAPI` sets it to the post's friendly URL, normalized, on a SHARE and
  never on an external front end's PREVIEW token, and `ShRenderAPI.page` checks
  `ShPreviewShare.allowsPath` right after the site bound. The rule is a string comparison, never a
  content lookup: `/pricing` matches `/pricing` and `/pricing/` and not `/pricing-old` or
  `/pricing/annual`; a value ending in `/` other than `/` itself is a folder and its subtree; `/` is
  the home page alone; `null` is the site-wide bound every earlier token had. **Sub-resources were
  the open question, and the answer is that pages are narrowed and assets are not:** outside the
  prefix a share still reaches the site's own assets (`ShSiteAssetDelivery`, under the site bound),
  and a path that resolves to a page is a 404. That covers what a composed page pulls, its images
  are `/file_source`, public and outside the cookie's `Path=/preview`; its theme is inlined; its
  site assets come through the asset branch, and an asset is a file the site serves, not a draft.
  `ShBlueprintRendersTest` mints a link for `/pricing` through the real endpoint, exchanges it,
  renders the page whole (banner, stylesheet, every `/preview/` sub-resource) and gets 404 on `/`,
  `/about`, `/menu/home` and on another site, the site bound SH1042 claimed and nothing had
  asserted. `ShPreviewSharePathTest` holds the rule.
  **And every live link can be read and stopped (SH1111).** The token screen hides ephemeral rows by
  design, so a sent link was visible nowhere. `GET /api/v2/preview-token` (`ShShareLinksAPI`) lists
  the live SHARE rows, tenant-scoped by the repository's derived query, narrowed to sites the
  caller may read by the content ACL, each with its site, page, expiry and owner, by **row id and
  never the credential**; `DELETE /api/v2/preview-token/{id}` disables one, which the filter already
  refuses. On the mint's authenticated route rather than `GET /api/v2/preview-share`, as the design
  had it: that path is `permitAll` for the anonymous exchange, and carving an authenticated exception
  out of it would make one path answer to two rules. `shio share <address>` prints the link (the
  same mint), `--list` and `--revoke <id>` beside it. `ShBlueprintRendersTest` lists, revokes and
  gets 401 on the reader's next request; `ShTenantCdaDeliveryIT` holds the listing to one tenant.
- **The general rule this is an instance of:** an allow-list entry outlives the route it was
  written for. Both halves read correctly on their own, the entry names a path that once meant
  something else, the new controller's comment names the rule it believes it inherits, and
  nothing anywhere disagrees out loud. When you mount a controller on an existing path prefix,
  the question is not "is this route safe" but **"what does the allow-list already say about this
  prefix"**.
- **`/sites/**` *is* in `permitAll()`, and since SH451 that is deliberate rather than inherited.**
  It was the same shape as the entry above, left over from 0.3.x's `com.viglet.shio.website`,
  pointing at nothing, and the difference is only that nothing served there, so it was a latent
  hazard instead of a live one. `ShSitesAPI` now mounts the public delivery route on it *because*
  it is anonymous, and every other guard is inverted to match: **PUBLISHED only** (the route takes
  no `state` parameter, so there is no way to ask it for a draft), no banner, no editor bridge,
  cacheable, indexable. Asked the question the bullet above prescribes, and answered it in the
  direction the allow-list already pointed, which is the one case where inheriting an entry is
  right, and it is right only because the route was designed to the entry rather than the other
  way round.


## The allow-list is checked from both sides now (SH363)

SH359's shape was not a widened permission. `mvc.matcher("/preview/**")` was written when
`/preview` meant something on the legacy console; the console went, the entry stayed matching
nothing, and years later SH183 mounted `GET /preview/{site}/{url}` (defaulting to `DRAFT`) on the
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
  request can find it, it is invisible to the test above by construction. This one reads the
  literals out of `ShSecurityConfig` (resolving constant references, because half the entries are
  written that way) and fails on any that denotes nothing and is not declared in
  `NOT_A_CONTROLLER` with the reason it is public. That map is read in **both** directions: an
  exemption whose allow-list entry has gone is deleted, because kept it pre-approves whoever adds
  that path back. Two named negatives carry the incident itself, `/preview/**` must not be listed,
  `/sites/**` must be.

It reads the source on purpose rather than for convenience. Spring's rules are not introspectable
as a list once built, and asking the container which paths are permitted would interrogate the
object the config produced, restating the config, which is what SH363 ruled out. The allow-list is
a literal in one file, and the question is whether each literal still denotes anything.

**Proved by injecting one.** A `/ghost-of-a-retired-console/**` added to a `permitAll()` block
failed the build naming the entry; the line was then reverted. A lint that has only ever been run
on a clean tree is green for two indistinguishable reasons.

## Form submissions: the door is scoped, and it is never anonymous (SH364)

SH247 rendered the fields and deliberately left the receiving half open, because a public endpoint
that writes content is a security decision and not a rendering one. SH364 took it. **Submissions
are token-scoped; Shio grows no anonymous write route.** The rejected alternative is the 2018 shape,
a genuinely public endpoint bounded only by site config, and it was rejected one iteration after
SH363 shipped an instrument whose entire purpose is proving no unauthenticated write route exists
by accident.

**The token is public by construction, so the scope is the control rather than the secrecy.** It
sits in a page a stranger can read. A `FORM` scope therefore names one site and may do exactly one
thing: create a `DRAFT` post of an eligible type in that site's configured folder. No publish, no
read, no move, no second site.

**Eligibility excludes every system post-type, and that predicate carries the whole severity.**
`PageLayout`, `Region` and `Theme` are Handlebars, compiled and executed by the renderer, so a form
that could create one is not a spam problem, it is a stranger writing a template this instance
runs. The rule is the site's allow-list *intersected with* "not a system type": an allow-list alone
still lets an operator name `Theme` by mistake.

**The destination is site configuration, never a template setting.** This is the one part of 2018's
`ShFormConfiguration` not adopted: it carried `folder` on the template, and a folder chosen in a
template is a folder an attacker chooses, because a template is content and content is what a
compromised author edits. `/api/v2/site-webhook` is the precedent, a table keyed by site, its own
API, no column on `ShSite`.

**Owner is the token.** An anonymous submission has no user to attribute, and naming the token is
both true and what an operator needs when one form starts producing rubbish. Retention is a
non-question: it is a post, so the trash, the export, the ACL and the agent surface already apply.

`ShRenderFormTest.theHelperNamesNoDestination` holds the half that is decidable before the endpoint
exists, `{{#form}}` emits no `action`, no `/api/` path and no token, and the page's single `action`
stays the author's. The page says what the form is *about*; the site says where it *goes*; neither
can answer the other's question. SH631 (the scope), SH632 (the site's destination) and SH633 (the
endpoint) build it, and SH633 must classify its route `AUTHENTICATED` in
`ShAnonymousRouteAccessTest`: the one endpoint on the product where a future reader is likeliest
to reason it "should" be allow-listed.

## The FORM scope, and the one line that makes it a scope rather than a label (SH631)

SH364 settled that submissions are token-scoped and never anonymous. `ShApiTokenScope.FORM` is that
credential, and it is the first scope bounded by **data** (a site, a folder, a type list) where
the other four are bounded by verb.

**It carries `ROLE_FORM` and nothing else.** Every scope above it starts from `ROLE_CDA` +
`ROLE_USER` and *adds* capability, which is right while the weakest of them may still read the
delivery API. A form token may not: it rides in a page a stranger can read, so `ROLE_CDA` would hand
the whole site to anyone who viewed the source, the privilege escalation §XII forbids, arriving
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
renderer compiles and executes. It refuses a platform type **two ways at once**, by its `system`
flag *and* by its name, deliberately, because those fail differently: the flag is data an import or
a restore can carry wrongly, and the name list is code that cannot describe a type this build has
not heard of. A type satisfying one and not the other is exactly the case worth refusing. `Redirect`,
`SiteScripts` and `File` are on the list too: a submission that could write a `Redirect` could point
any URL of the site anywhere, and one that could write `SiteScripts` could add a `<script src>` to
every page, the same defect in different clothes.

Every default points at refusal: an unresolved type is not eligible, and an empty allow-list invites
nothing rather than everything. `eligibleNames` exists so SH633's refusal can tell an operator what
they *could* have typed (P6) instead of leaving them to discover it by absence.

The endpoint is SH633 and the site's allow-list and folder are SH632; until both land a `FORM` token
authenticates on a path that answers 404, which is inert and is the point of shipping the bound
first.

## Where a rendered form posts (SH633)

`POST /api/v2/cda/form/{site}` writes **one `DRAFT` post** into the site's configured folder, or
refuses. It is deliberately ordinary, because SH631 bounded the scope and SH632 owned the
destination, and there is no submissions subsystem, which is the argument rather than an omission:
**a submission is a post**, so the trash, the export, the ACL, the review queue and the agent
surface already apply to it.

**`DRAFT` is not a parameter and never becomes one.** SH7's review queue is the moderation step, so
a submission is visible to a curator and invisible to a visitor, and nothing here has to decide what
"approved" means. The rate limit is the CDA bucket `ShApiTokenFilter` has already spent before the
method runs, a form token *is* an API token, so what this needed was a tight default and not a
mechanism.

**The honeypot answers exactly as a success does.** `shio_hp` is a fixed field name, rendered hidden
and required to be empty; a tripped one returns `{"received": true}` and writes nothing. A refusal
naming the field would be a free description of how to evade it, so there is no signal to tune
against. When empty it is *stripped* from the stored data, a spam-control artefact in every content
record is not content.

**Which refusals may teach.** P6 is bounded here in a way it is nowhere else on the agent surface,
because the caller is a browser nobody controls. A **configuration** refusal is a message to the
operator and says everything: the site invites nothing, the folder is missing, the type is not on
the list, here is what is. It prints only the *eligible* names, a site whose list says `Theme` must
not be told it invites `Theme`, or an operator goes looking for a bug that is their configuration. A
**honeypot** refusal says nothing at all.

**A missing folder refuses rather than creating one**: a public path that can create folders is a
public path that can fill a disk. Owner is `form-token:<id>`, the **token**, not a person: an
anonymous submission has no user to attribute, and naming the credential is what an operator needs
when one form starts producing rubbish, without identifying a visitor.

Two ordering facts that are easy to get wrong and would both fail closed:

- The `POST /api/v2/cda/form/**` rule must precede `POST /api/v2/cda/**`, which requires
  `ROLE_CDA_WRITE`: a `FORM` token carries neither that nor `ROLE_CDA`, so without it the endpoint
  is unreachable by the only credential meant to call it. **Both** security chains carry it.
- It has its **own** `AUTHENTICATED` row in `ShAnonymousRouteAccessTest`, ahead of the
  `/api/v2/cda/**` row that already covered it. This is the one endpoint in the product whose whole
  purpose is to be reachable from a public page, so it is exactly where a future reader reasons it
  "should" be allow-listed. It must not be: the page carries the token, and the token is the
  authorization, allow-listing it would turn a scoped write into an anonymous one.

## The form endpoint's origins (SH637)

SH633 shipped an endpoint whose whole purpose is to be called from another origin, and it could not
be: `http.cors(Customizer.withDefaults())` had **no `CorsConfigurationSource` bean** to configure it,
so every preflight failed. Correct, tested, and unreachable from its only caller. Nothing had
noticed because nothing needed CORS, the console reaches the API same-origin through the dev proxy.

**`ShFormCorsConfig` registers one path.** `UrlBasedCorsConfigurationSource` returns `null` for a
path it does not know and a null configuration means no CORS headers, so opening
`/api/v2/cda/form/**` leaves every other endpoint byte-identical to before. `allowCredentials` is
`false`: the endpoint authenticates by `Key` header, not cookie.

**CORS is not the control, and that decides the whole shape.** It protects the *browser*; a
non-browser client ignores it, so an origin allowed only there is allowed only against callers who
were going to behave. The control is `sh_site_form.allowed_origins`, checked against `Origin` in the
endpoint, per site, because a form token names one site and a global list would let any configured
origin post to every site's form, which is the scope SH631 spent a task bounding. So the CORS layer
is deliberately permissive (`*` patterns) and the endpoint is not.

Three decisions in the check: **matched exactly**, because suffix matching is how `acme.com` comes
to accept `evil-acme.com`; **empty means none**, the direction every default on this path takes; and
**no `Origin` at all passes**, because that header is something a browser adds, refusing its absence
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

## The first 4xx a caller meets (SH622)

SH499's execution run found `GET /api/v2/cda/site` and `/api/v2/cda/post/{id}/editable` answering
**403 to console HTTP Basic with Spring's own `{timestamp, status, error, path}`**, and anonymous
getting the same shape with 401.

**The cause is structural, not an omission on those two endpoints.** P6 is implemented in
`@ControllerAdvice`, and a rejected credential never reaches a controller, the filter chain answers
first. So *every* endpoint on this product had one 4xx carrying no `fix`, and it is the one a caller
hits before any other.

Two handlers now write the same problem document the advice does: the 401 entry point (which was
emitting `{"error":"Unauthorized"}`, the status restated as a word) and a **chain-wide**
`ShAccessDeniedHandler` for 403. `ShCsrfAccessDeniedHandler` stays wired to `CsrfFilter` alone for
SH339's stated reason: a CSRF rejection and an authorization denial have different remedies, and one
handler for both would have to guess which it is sending.

**The missing field was never prose, it was *which credential*.** A caller reading 403 could not
tell "your credential is real but too narrow" from "this endpoint is not for this kind of credential
at all", and those need opposite moves, widen a scope, or stop sending a token and sign in.
`ShCredentialProblems` answers per surface: a FORM token for `/cda/form`, an AGENT token for the
agent surface and MCP, a CDA token for the CDA and GraphQL, a console session everywhere else, each
with the call that mints one.

Derived from the **path** and deliberately not from the manifest: the manifest also knows, but
reaching it from the security chain means the entry point depending on a service the chain runs
before. The prefixes *are* the chain's own rules, restated where the refusal is written, a mismatch
between them is a mismatch between two lines of one config, which is much smaller than a cycle
between two of them.

`ShProblemJson` is shared by all three refusals, because three copies of "which fields, in which
order" is three answers to one question, and the one that drifts is the field a caller reads to tell
two refusals apart.

**Why nobody had noticed, and it is worth writing down: no test asked an unauthenticated question
and read the body.** `ShAnonymousRouteAccessTest` asserts every route's *status*, which is what it
is for, and a 401 whose body is one word passes it exactly as a teaching one does. Four assertions
now read bytes. One existing test had to change with it: `ShPreviewChallengeTest` pinned
`{"error":"Unauthorized"}` literally, and SH450's actual claim, a browser-challenged refusal and a
programmatic one differ in the *header* and nothing else, is now asserted as that comparison
instead of as a copy of the old bytes.

## What an ACL decision is allowed to depend on (SH225)

`ShContentAclService.requireRead`/`requireWrite` carry `@Transactional(readOnly = true)`
themselves, and `aclChain` climbs the folder tree with
`ShFolderRepository.findParentIdOf` rather than through `parentFolder`.

**Both halves came from the same finding, and the task's premise was wrong about it.**
SH225 was filed saying the check needed `spring.jpa.open-in-view` because the walk touched
a *lazy* association. It is not lazy, `ShFolder.parentFolder` is a plain `@ManyToOne`, and
JPA's default there is EAGER, so the whole chain arrived with the `findById`. A
`@SpringBootTest` with `open-in-view=false` over a three-level chain passed on the unfixed
code. It was withdrawn: a test that is green either way is not evidence, and it was
spending one of the two remaining slots under `ShContextCacheHeadroomTest` to be green.

What *was* real is the dependency one level down. The check worked because of an entity
mapping's default fetch type, so the first person to mark that association `LAZY`, the
ordinary JPA hygiene move, made for reasons with nothing to do with permissions, would
have broken **authorization**, and broken it as a 500 rather than as a denial. Neither an
allow nor a deny, and not handleable as either. Hence the rule:

> **A permission decision may not depend on a fetch strategy.** Resolve the chain by id.

**`ShAclAssociationWalkLintTest` enforces it (SH650).** The property is a *shape* and no fixture
can express it (any behavioural test is green on both sides of the line) so the deliverable was
the instrument (SH338), and it is a source scan rather than a runtime check for exactly that reason.
It asks two questions:

- **The narrow one, on `ShContentAclService`:** the chain walk may not call any association getter.
  The failure message names the line and the two id projections that exist for this
  (`findParentIdOf`, `findFolderIdOf`).
- **The wider one the design asked for:** *which security-relevant reads navigate associations at
  all*, over every main source that references `AccessDeniedException`, `ShAclPermission` or
  `GrantedAuthority`, derived from what the file does rather than from where it lives, so a check
  that moves package cannot leave a path-keyed list quietly scanning nothing.

**The banned getters are derived from the entity model**, not written down: every
`@ManyToOne`/`@OneToMany`/`@OneToOne`/`@ManyToMany` field as the accessor Lombok generates for it
(18 on the last run; the number prints, so it is never a comment that has started rotting). A list
in the test would be a second copy of the mapping, and the failure it would miss is precisely the
one this exists for, an association added later, navigated by an edit nobody connected to
authorization.

**The answer to the wider question is currently zero, and it was measured rather than assumed.** The
first draft of the declared list guessed two entries, the principal walk in `ShAuthorityResolver`
and `ShCustomUserDetails`, and the run said none: those read `role.getName()` on an already-loaded
row, which is a property and not a navigation. The only two textual hits in the whole population are
the *prose* in `ShContentAclService` explaining what SH225 removed, stripped by `ShSourceText`
(SH613), SH206's defect avoided by construction rather than by luck. So the list is empty and it is
a **declared list rather than a ceiling**: a ceiling lets one task pay the debt and the next spend it.

The SH22 half of the same walk, navigating past the `id` of an `ShObject`-subclass association in
JPQL, is `ShTenantJoinLintTest`'s, and is not duplicated here.

The half that does have an assertion is the annotation. `requireRead`/`requireWrite` reached
`canRead`/`canWrite` by **self-invocation**, which never crosses the Spring proxy, so the
`@Transactional` on those methods never applied to the path callers take, the third time
this session (SH357, SH358). `ShContentAclEntryPointTest` checks it by reflection at no
context cost, and it fails on the code SH225 replaced.

**One regression the change introduced and the suite caught.** The first draft used a single
`chain.contains` guard for both deduplication and cycle-braking. For a folder,
`startFolderId` returns the object's own id, which `chain` already holds, so the walk
stopped before its first hop and every folder silently stopped inheriting its parent's ACL.
Deduplication and termination are two questions; `visited.add` answers only the second.
`ShContentAclServiceTest.aclInheritsDownTheFolderTreeToSubfolderAndPost` is what failed.

## Only an OAuth login could be an administrator (SH681)

`ShAuthorityResolver` reads user → groups → roles and adds **`ROLE_ADMIN`** for anyone in the
`Administrator` group. Exactly two classes referenced it: `ShOAuth2UserService` and
`ShOidcUserService`. A password login goes through `ShCustomUserDetailsService`, which walked the
**same tables** and built authorities from `ShRole` names alone, no group rule, no `ROLE_ADMIN`.

On a fresh instance `GET /api/v2/role` answers `[]`. So a password-authenticated user carried **no
role authorities at all**, and every `hasRole("ADMIN")` gate was unreachable from a local login.

Measured rather than reasoned: `POST /api/v2/agent/blueprints/reload` answered **403** to the seeded
`admin`: over a console session *and* over Basic auth, while `/api/v2/user/current` reported
`"admin": true`. That endpoint exists to be called by an administrator and had not been callable by
one since SH219.

**Two things this was not.** The OAuth path is not wrong, it works, which is why nobody noticed. And
`admin: true` is not wrong: the flag is group membership, which is correct. What was missing is the
step that turns membership into an authority.

### One rule, three logins

The repair was a choice, and the alternative was seeding the `ShRole` rows the group is meant to
carry. Wiring the resolver into the local `UserDetailsService` was chosen because seeding leaves
**two sources of truth** about who is an administrator (the group and the table) free to disagree,
and leaves every existing instance broken until it migrates. Delegating means the rule cannot drift
between authentication paths, which is exactly how it came to hold on two of three.

### The assertion is at the gate, not at the resolver

SH527's rule applied honestly. A unit test of `ShAuthorityResolver` would have passed throughout,
**the resolver was never wrong**. What nothing asked is whether the authority it grants *arrives at a
request*, which is one composition away and is where the 403 lived. So `ShLocalAdminAuthorityTest`
asserts both halves: that the local login carries `ROLE_ADMIN`, and that a locally authenticated
administrator does not get 403 from the admin-gated path.

Anything but 403 is the claim. Whether the reload then succeeds is SH219's business, and asserting
its body here would couple this test to that one.

### What this unblocked

`ShSecurityConfig` carried a note at the marketplace writes saying they *should* be `hasRole("ADMIN")`
and were `authenticated()` instead, because ADMIN was unreachable. That was SH682, below.

## The marketplace writes want the role the reload already has (SH682)

`POST /api/v2/marketplace/{id}/install` and `DELETE /api/v2/marketplace/{id}` were `authenticated()`,
the blanket rule, and therefore no line in the config at all. They are now `hasRole("ADMIN")` on
both branches of `filterChain`, and the argument is `BLUEPRINT_RELOAD_PATH`: merely **re-reading**
the operator's package directory is admin-gated because it changes what every later apply can do,
and installing is that act **plus a download**, so it cannot honestly be gated more weakly than the
thing it contains. Reads stay `authenticated()`; the gate is on POST and DELETE, not on the path,
because browsing the marketplace is not an operator act and hiding it would keep a curator from
seeing what was installed.

**The interesting part is why it took two tasks.** It was written this way in SH677 and *taken out
again*, because `ROLE_ADMIN` was unreachable from a password login (SH681, above), the reload
answered 403 to the seeded admin. Gating a capability on a role nothing grants does not make it
safer, it makes it dead, and a dead gate teaches the next reader that the gate is decorative. So the
pair was filed as two lines in one backlog rather than a note-to-self in the config, on the reasoning
that a precondition owned by somebody else's task is only honest if both are visible.

### Both halves are asserted, and that is the whole point

`ShMarketplaceAdminGateTest` asserts that a caller authenticated but not an administrator gets 403
from each write, **and** that the seeded admin, loaded through `ShCustomUserDetailsService`, not a
fabricated `ROLE_ADMIN`, does not. A test with only the refusal half passes perfectly well against a
gate nobody can pass, which is precisely the state SH681 found and the reason "it is gated" was
believed for four tasks.

Both directions were injected before the green was trusted. With the matchers removed a non-admin
reaches the controller (404 on install, 409 on uninstall) instead of 403; with the role swapped for
one nothing grants, the admin half fails at 403 and the refusals still pass, the dead-gate
signature, reproduced on purpose.

**Not covered:** the Keycloak branch of `filterChain` carries the same two matchers and reaching it
needs an OIDC provider this suite does not stand up. The rule there is a copy, asserted only by
reading. The console needed no change, the marketplace page already lives in the admin area beside
users, groups and roles, so the gate matches where the UI is.

## Getting in on a fresh instance (SH706)

`ShUserOnStartup` seeds `admin` with a **null** password. That is deliberate, no shipped default
credential, but it left `POST /api/v2/setup`, behind the console's first-access screen, as the only
writer. A fresh container therefore started in a state no script could leave: every surface answers
401, and the remedy was a person opening a browser. Law P1, on the one capability that gates every
other one, because an agent cannot reach a first correct call at all.

**`SHIO_ADMIN_PASSWORD`** is the second door, bound as `shio.admin.password` so it is configuration
rather than a `System.getenv` somewhere. `ShOnStartup` applies it **outside** the first-time gate:
inside it, it would help exactly the installs that do not need help, while the case an automated
deploy actually meets, an instance seeded before the variable was supplied, or one whose setup
screen was never completed, would be the one it skipped.

**`ShAdminPasswordService` is the single owner of that field**, and the endpoint delegates to it.
Two code paths encoding the same password eventually disagree about the minimum length or the
encoder; SH245 is the open task about that exact shape, filed after the console's copy of a shared
verb turned out to be the careless one four times.

Three rules make it safe to leave in a compose file, and each is asserted by name in
`ShAdminPasswordServiceTest`: it **never overwrites** a configured password (a restart is a no-op,
and an operator's console change is not reverted by their own deployment); it **never fails
startup**, blank, too short and unset are each a log line and a return, because `ShOnStartup` is an
`ApplicationRunner` and a throw there turns an env-var typo into an outage; and it stores the value
**encoded**, so the compose file's secret is not in the database in clear.

**Not covered:** nothing asserts the wiring end to end, that `ShOnStartup` calls it, and that
`shio.admin.password` binds from `SHIO_ADMIN_PASSWORD`. The service is asserted directly and the
binding is a one-line property; a context test for it would cost a slot `ShContextCacheHeadroomTest`
counts (SH315) to check Spring does its own job.

## Uploaded bytes are never a document this origin renders (SH950)

Every asset route sent `Content-Disposition: inline`, and a grep of the backend for
`X-Content-Type-Options` or `Content-Security-Policy` returned nothing. The content type is probed
from the file name, so `evil.svg` was served as `image/svg+xml` and `payload.html` as `text/html`,
scriptable documents, on the console's own origin, through an upload endpoint that accepts an
**agent token**. Stored XSS against the curator's session, needing no other defect to exploit.

`ShAssetResponseHeaders` is the one place that decides, and every asset response goes through it:

- `X-Content-Type-Options: nosniff`: the browser may not upgrade bytes to a document type the
  server did not declare.
- `Content-Disposition: attachment` for anything outside a short list of **raster** image types.
  `image/svg+xml` is deliberately not on it: an SVG is the one image type a reader assumes is safe
  because the rest of its family is. **The delivered site is unaffected**, a browser honours the
  disposition on a navigation and ignores it for a subresource, so `<img>`, `<link rel=stylesheet>`
  and `<script src>` load exactly as before.
- `Content-Security-Policy: default-src 'none'; sandbox`: the backstop for an asset opened
  directly as a top-level document. Inert on a subresource, because CSP binds the document that
  embeds a response and never the response itself.

**Three routes, not the two the task named.** `ShStaticFileAPI` and `ShSiteAssetDelivery` build a
`ResponseEntity` and call `harden`. `/file_source/**` is a Spring *resource handler* with no
`ResponseEntity` at all, and it is the `permitAll` one, so it was the worst of the three. It is
covered by `ShAssetSecurityHeadersFilter`, registered in `ShAssetSecurityConfig` on
`/file_source/*` **and nothing wider**: a delivered page is `text/html` by design, and an
`attachment` on `/sites/**` would take every replicated site down.

**`nosniff` is why the content-type table is part of this fix.** A wrong type used to be sniffed
past; now a stylesheet that is not `text/css` and a module script that is not a JavaScript type are
refused outright. Measured on this JDK: `guessContentTypeFromName` is right for `.css` and `.js`,
and returns nothing for `.mjs` (probed as `text/plain`), `.woff2` and `.avif`. Shipping `nosniff`
without `BY_EXTENSION` would have taken a replicated site's ES modules off the air, the class of
breakage SH555 and SH556 each cost a round.

The boundary is asserted in `ShStaticFileAPITest` (`stage02d/e/f`: upload an SVG, an HTML file and
a PNG, GET each, read the headers) rather than in a class of its own, because a second
`@SpringBootTest` would have spent the last slot SH423's headroom budget had.
`ShAssetSecurityHeadersTest` holds the rest, including the grep that fails if any future route
writes its own `Content-Disposition`.

**Not covered:** the allow-list on what may be uploaded at all is SH951. This is the one-commit
mitigation, deliberately separate so the hole does not stay open while that is argued.

**The download name is quoted by Spring's `ContentDisposition`, never interpolated (SH1071).** The
name comes from `sanitizeFileName`, which guards the *filesystem* and leaves a `"` alone, so
`a".svg` was sent as `filename="a".svg"`, and a name ending `"; filename*=UTF-8''x.html` became a
second filename that RFC 6266 tells a client to prefer. The builder escapes `\` and `"`. Two things
it does not do, measured on 7.0.9: it keeps a control character in `filename*` as `%0D%0A`, which a
client decodes into the name it saves, so `disposition` drops control characters first; and a
charset on an ASCII name adds a `filename*` anyway, so UTF-8 is asked for only when the name needs
it, an ASCII header is byte-identical to what SH950 shipped. The filter now **decodes** the path
segment: `getRequestURI()` is undecoded by the servlet spec, so `relatório.pdf` downloaded as
`relat%C3%B3rio.pdf`, and `"` could only ever arrive as `%22`, which is why the quoting defect was
invisible on the public route until this. The tests read each header back through
`ContentDisposition.parse`, because the defect was what a client made of it, not how it looked.

## What an instance accepts as an upload (SH951)

SH950 stopped uploaded bytes being *served* as documents. They were still **stored**, still listed,
and still copied into a content tree by `shio pull`, so the hole had moved rather than closed.
`ShUploadPolicy` is the half that decides whether the bytes arrive.

`shio.upload.policy.<media-type>` takes `allow`, `deny` or `sanitize`;
`shio.upload.default-verdict` covers everything unnamed and ships as `allow`, because a policy is a
list of exceptions and a deny-by-default list would have to enumerate every font and image format
anyone ships. An exact key beats a `family/*` key beats the default.

**Three verdicts, because two would force a bad choice about SVG.** `text/html` and
`application/xhtml+xml` are denied outright: neither is legitimately an asset, and a page is
content. SVG is **sanitized**, a replicated site's logo is very often one, and refusing it would
make the product worse at its own job. `ShSvgSanitizer` removes `<script>`, `<foreignObject>`,
`<handler>`, every `on*` attribute, a non-local `href`/`xlink:href`, `<!DOCTYPE>` and
`<?xml-stylesheet?>`, and leaves everything else byte-identical, a clean SVG comes back unchanged,
or every replication digest would move and `shio diff` would report a change nobody made. It is
regex-based on purpose: an XML parse of attacker-controlled bytes is the surface XXE lives on, and
re-serialising rewrites the whole document. **It is the second of two layers, not the only one**,
SH950 already serves every SVG as an attachment under `default-src 'none'; sandbox`, and neither
layer may be removed on the strength of the other.

**The JavaScript default had to be settled, not assumed, and it has a cost.** A capture does not
collect scripts: `clone-assets.mjs` splits its inventory into `DOWNLOADABLE` (image, stylesheet,
font, icon, media, manifest) and `BEHAVIOUR` (script, data). So an ordinary replication uploads no
JavaScript and is unaffected, but **`shio clone --scripts` (SH469) uploads a site's own bundle on
purpose, and this default refuses it.** Nothing in the suites would have caught that: `--scripts`
is exercised only in `planAssets` unit tests and never through a live upload, so the feature would
have broken quietly rather than redly. The refusal is a teaching 400 naming
`shio.upload.policy.text/javascript=allow`, which is the difference between a blocked replication
and a mystery.

**The verdict is taken on the name as well as the declared type, and the stricter wins.** The name
is how the bytes will be *served* (`ShAssetResponseHeaders` reads it), so keying only on what the
client declared would let `evil.svg` sent as `image/png` past the policy and then be served as an
SVG anyway.

`replace` is an upload path and gets the same treatment. Where bytes were cleaned, the post carries
`SANITIZED: true` in its `data`, content rather than a log line, deliberately, because `shio pull`
projects it and a reader comparing a replica against its source can then see the difference was a
policy and not a defect.

**What an agent can see about it.** `shio_assets` rows carry two `flag`s: `sanitized` (the stored
bytes were cleaned) and `unsafe`, the policy's verdict taken on bytes that are **already stored**,
which is the question an upload check cannot answer. `DENY` is unsafe outright; `SANITIZE` is unsafe
only where the post does not say it was sanitized, because that is exactly the SVG uploaded before
the cleaning existed. Both are flags rather than attributes, so the ordinary asset, every asset on
a healthy instance, pays nothing for them (P3). Naming them in the tool description was paid for
out of the same description: the closing rationale about base64 moved to the class javadoc, because
a payload re-sent every turn may carry what a caller *decides on* and not why the design is what it
is. `shio_assets` came out at 184 tokens against 183 before, and `mcp.tools.list.total` is untouched.

`shio verify` gains **`asset-unsafe-type`** over the projected tree: an asset in `shio/assets/**`
whose type a default policy denies, reported as an error before `shio push --content` meets the
400. It reports the denied class only and never SVG, a default policy cleans an SVG rather than
refusing it, so a finding would fire on every logo in every replicated site, and a warning that
appears on every run is the next thing nobody reads. The extension table mirrors
`ShUploadPolicyProperties` and says so: the CLI has no instance to ask, which is also why the fix
names the property rather than claiming to know the verdict.

## What a rich-text field is allowed to contain (SH956)

`html-editor.widget.tsx` is `document.execCommand` over a `contentEditable`, reads `innerHTML` as
its value, and has no paste handler. **Nothing anywhere sanitised content**, the only `sanitize`
calls in the tree were for tenant and file names, and the renderer emits field HTML unescaped by
design. So a paste carrying `<img onerror=…>`, or an agent's `post.upsert` writing the same field,
put script on the published page.

`ShHtmlSanitizer` runs in `ShPostUnifiedWriteService`, the one place the console, REST, the agent
batch and the Universal Editor's draft merge all pass through. A sanitizer in the widget would
defend the writer easiest to go around, and the analysis found the editor's draft merge about to
grow a second one of its own, two sanitizers disagreeing about one field is worse than one that is
sometimes late. Cleaned **before it is stored**, not at the render: a sanitizer on the way out
leaves the payload in the database for the next reader that does not have one.

**`HTML Editor` only, and that exclusion is load-bearing.** `PageLayout` and `Region` declare
`Ace Editor - HTML` for their `HTML` field, and those values are Handlebars templates carrying
`<script>`, `{{#region}}` and a whole document *on purpose*. An allow-list over them strips every
site's own layout on its next save, a larger outage than the defect being closed. A code editor is
where writing markup **is** the point; a rich-text box is where it is an accident.
`ShHtmlSanitizerWriteBoundaryTest` asserts both directions rather than trusting this paragraph.

jsoup, because it is already declared here (SH183), already used by the render service, is a real
parser, and `Safelist` is a maintained allow-list instead of a list of attacks somebody remembered.

**Two things the suite found that a reading would not have:**

- **jsoup cannot pass a relative URL.** With `preserveRelativeLinks(false)` it resolves against a
  base and there is none, so `/pricing` becomes empty and is refused; with it `true` it tests the
  raw value against a scheme list, which a path also fails. It stripped `href="/pricing"` and
  `src="/file_source/…"` out of ordinary rich text. So the protocol restriction is removed from
  those attributes and replaced by `hasDangerousScheme`: no scheme is relative and fine, a declared
  scheme must be one of `http`/`https`/`mailto`/`tel`/`ftp`. Control characters are stripped before
  the scheme is read, because the browser strips them too, that is how `java\tscript:` gets past a
  naive check. `data:` is refused outright: a `data:` document is a same-origin navigation in some
  browsers, and an inline image belongs in the media library where it can be found again.
- **jsoup's `relaxed` predates HTML5 sectioning.** `ShAgentRenderTest.landmarksAreCounted` went red
  because the render digest counts `header`/`main`/`section`/`footer` as landmarks *because content
  has them*, the allow-list was flattening every page's structure and taking the accessibility
  landmarks with it. They carry no behaviour and are now allowed.

A clean value is returned **byte for byte**, the original string, not jsoup's re-serialisation,
or every digest moves and `shio diff` reports a change nobody made. What was refused is *named*
rather than counted, because "3 things removed" is not a fact anybody can act on.

**What a plan says, and what the corpus is checked against.** A `dryRun` batch now reports what
the write *would* strip, in the op's `detail`, "`body` loses `<script>`, event handler onerror to
the content allow-list", so an agent learns its markup will change shape before the write rather
than by reading the result back. A plan over clean markup says nothing about the allow-list, or an
agent told about it on every op stops reading the detail at all.

`shio verify` gains **`html-unsafe`**, an error on a published `HTML Editor` value the allow-list
would refuse today: a sanitizer on writes is silent about the corpus it inherited, and those rows
are the ones already serving visitors. The fix is a save away, because every write runs the
allow-list. `Ace Editor - HTML` is exempt here too, or every `PageLayout` in the estate reads as
unsafe and this is the first rule anybody switches off.

**All three readers (write, plan and verify) call the same `ShHtmlSanitizer`.** Two answers to
"what is allowed here" is how a plan comes to disagree with the run it predicts, or a corpus with a
write (SH245).

`ShHtmlUnsafeRuleTest` drives the rule directly rather than through the published-site scan, and
that is recorded as a decision: three fixtures the scan should have treated as published, a
hand-persisted row, one with its site set, one created through the write path and overwritten, all
came back with an empty report, and each attempt was becoming a test of `scan()` rather than of this
rule. The rule's one call sits beside the link and image checks that walk the same fields.

## PUBLISH, a permission above WRITE (SH995)

`ShAclPermission` was READ and WRITE for two blocks, and "may put content live" lived only on an API
token, whose accessor returns **true when no token is in play**, which is every console session. So
the *agent*, whose work the review queue exists to gate, was the one actor the product could stop from
publishing, and a team had no separation between the person who writes and the person who approves.

There are now three levels, ordered: PUBLISH ⊇ WRITE ⊇ READ, with `satisfies` comparing ordinals rather
than listing pairs. `ShContentAclService.requirePublish` is what every path that changes the live site
asks, and its refusal says what to do about it, the draft is saved either way, and somebody granted
PUBLISH on that branch is who finishes it. **Every existing WRITE grant became PUBLISH**
(`v2026.3.34`): an upgrade must not take a capability from somebody who had it, so separation is opt-in
from the day it exists rather than a surprise the morning after. The API token's `mayPublish` is
unchanged and still a second lock, a token needs both.

**The sweep landed before the fixes (SH322).** "Where does this product publish" is not a question a
reader can answer, thirteen call sites across six classes, and the one that is missed does not fail,
it silently lets somebody publish. `ShPublishPermissionLintTest` reads every method that calls the
write service's publish, unpublish or schedule and asserts it asks, with four declared exemptions:
`ShPostUnifiedWriteService` (it *is* the writer), `ShPostScheduler` (a timer acting as nobody, firing
what somebody authorised when they scheduled it), `ShReleaseRunner` (the ops ask; asking here would
gate the timer) and `ShPostDraftMergeService` (its callers ask). It found nine unguarded methods,
including the console's own publish, unpublish and schedule routes and the review queue's approve,
the one act a team most wants held apart from writing.

## The login follows `returnUrl` only to a path on this host (SH1299)

`/login?returnUrl=` was built by the console's own 401 handler and followed whatever it held, so
`/login?returnUrl=https://elsewhere` sent a freshly signed-in user off the instance. SH1299 made
`/sites/**` send turned-away visitors to the login with the page as `returnUrl`, which puts the
parameter in links the console did not build, so `safeReturnUrl` (`shio-react/src/lib/return-url.ts`)
now accepts a value only when it starts with one `/` (not `//`, not `/\`) and falls back to the
root otherwise. `shio-react/test/return-url.test.mjs` holds the hostile spellings.
