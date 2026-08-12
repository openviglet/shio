package com.viglet.shio.spring.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;
import com.viglet.shio.spring.security.auth.ShLogoutHandler;
import com.viglet.shio.tenant.ShTenantFilter;

/**
 * @author Alexandre Oliveira
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ComponentScan(basePackageClasses = ShCustomUserDetailsService.class)
public class ShSecurityConfig {

    private static final String LOGIN_PATH = "/api/v2/login";
    private static final String LOGOUT_PATH = "/logout";
    // OIDC Back-Channel Logout callback (Single Logout). Keycloak POSTs a signed
    // logout token here when the SSO session ends (e.g. logout from the Console),
    // and Spring invalidates the matching local session on its own. Server-to-
    // server, token-authenticated: no browser cookie/CSRF, so it must be CSRF-
    // exempt and permitAll (the logout token IS the credential). Path is Spring's
    // default: /logout/connect/back-channel/{registrationId} (registrationId=keycloak).
    private static final String BACK_CHANNEL_LOGOUT_PATH = "/logout/connect/back-channel/**";
    public static final String ERROR_PATH = "/error/**";
    /** The CDA namespace (SH1) — read (GET) for ROLE_CDA, write (SH10) for ROLE_CDA_WRITE. */
    private static final String CDA_PATH = "/api/v2/cda/**";

    /**
     * Where a rendered form posts (SH633).
     *
     * <p>Must precede the {@code POST CDA_PATH} rule, which requires {@code ROLE_CDA_WRITE} — a
     * {@code FORM} token deliberately carries neither that nor {@code ROLE_CDA} (SH631), so without
     * this rule first the endpoint is unreachable by the only credential meant to call it. A
     * narrower matcher ahead of a broader one is the ordering the CDA write surface already
     * depends on.</p>
     */
    private static final String FORM_PATH = "/api/v2/cda/form/**";

    /** Role a FORM-scope token carries, and the only thing it carries (SH631). */
    private static final String ROLE_FORM = "FORM";
    /**
     * GraphQL delivery (SH4) — a CDA surface, token-authenticated POST reads.
     * The one exemption SH167 deliberately left blanket: the schema exposes no
     * {@code Mutation}, so a forged POST here cannot write, and the GraphiQL console at
     * {@code /graphiql/**} is a cookie-bearing browser that would break under the narrowed
     * matcher for no gain. Revisit the moment a mutation is added — see SH234.
     */
    private static final String GRAPHQL_PATH = "/graphql";
    /** First-run setup (SH6) — open until a tenant exists. */
    private static final String SETUP_PATH = "/api/v2/setup";
    /** Role required for CDA writes (SH10); {@code hasRole} prepends {@code ROLE_}. */
    private static final String ROLE_CDA_WRITE = "CDA_WRITE";
    /** Post-types-as-code admin API (SH16) — console-authenticated, used by the stateless {@code shio} CLI. */
    private static final String POST_TYPE_PATH = "/api/v2/post-type/**";
    /**
     * SH327 — the SH183 render model, provisioned. Narrowed for SH167's reason and reachable for
     * SH339's: this is the endpoint that makes a site <em>visible</em>, and it was the one an agent
     * could not call. A curator's browser stays CSRF-checked; a credential-based caller holds no
     * cookie and could not obtain a token, so before this it got a bodiless 403 and no way forward.
     *
     * <p>It is the same class of thing as {@code POST_TYPE_PATH} beside it — provisioning a content
     * model, idempotent, creating only the three system types {@code PUT /api/v2/post-type/{name}}
     * refuses — which is why it gets the same treatment rather than a new op on the write surface.
     * SH170's site creation went the other way (an op, not an opened endpoint) because a site is
     * addressable and this is not: there is nothing to name, so {@code ensure} has no address and
     * would be a verb with no subject.</p>
     */
    private static final String RENDER_PATH = "/api/v2/render/**";
    /**
     * SH344 — the last step of SH184's answer. An agent that has just deployed a front end needs a
     * preview token so that front end can turn its own draft mode on, and could not mint one: the
     * route was undeclared and session-only, so a credential-based caller got a bodiless 403.
     *
     * <p><b>No new gate, and that is a decision rather than an omission.</b> The token this mints is
     * {@code PREVIEW} scope, one site, one post, and expiring — it grants draft read on a post the
     * caller can already read, and SH106 gives the {@code AGENT} scope draft reads outright. So there
     * is nothing here to escalate to, which is the test SH107's publish gate had to pass and this
     * does not: publishing changes what the public sees, minting a preview token does not.</p>
     */
    private static final String PREVIEW_TOKEN_PATH = "/api/v2/preview-token";
    /**
     * SH343 — webhook subscriptions. The agent reads its own delivery failures in
     * {@code /agent/diagnostics} (SH103, with SH269's frames) and had no route to inspect, repair or
     * remove the subscription that produced them: a diagnostic for a resource the reader cannot act
     * on is SH190's defect one level up.
     *
     * <p><b>Narrowed only after the read stopped leaking.</b> This path returns {@code ShSiteWebhook}
     * directly, and its HMAC {@code secret} serialized in plain text — measured. Narrowing widens who
     * reaches that read from a console session to any token-authenticated caller, so the exposure was
     * closed first ({@code @JsonProperty(WRITE_ONLY)}, lossless because the secret is caller-supplied).
     * The order matters more than either change: <b>a reachability change is also an access change.</b></p>
     *
     * <p>Not a {@code webhook.*} batch op, for SH327's reason: every op on that surface is addressed
     * ({@code site:}/{@code folder:}/{@code post:}), and a webhook has no SH79 address — an op there
     * would be a verb with no subject.</p>
     */
    private static final String WEBHOOK_PATH = "/api/v2/site-webhook/**";
    /**
     * SH189 — the exchange import. Narrowed rather than blanket for SH167's reason: the console
     * posts the multipart form with a session cookie and must stay CSRF-checked, while a
     * credential-based client (the CLI, a CI job) holds no cookie and cannot send a token. A
     * blanket exemption here would be an invitation to forge a *site creation* against a
     * curator's browser session, which is about as far from harmless as this list gets.
     */
    private static final String IMPORT_PATH = "/api/v2/import";
    /**
     * The media library (SH62) — narrowed for SH437's reason, which is the one the SH339 inventory
     * predicted somebody would hit.
     *
     * <p>Bytes are the one thing the agent surface cannot carry: {@code post.upsert} takes JSON, so
     * an asset reaches an instance through {@code POST /api/v2/staticfile/upload} and through
     * nothing else. That path was reachable only with a session cookie, so the refusal a stateless
     * caller got was accurate and its fix — "use the agent surface" — named no verb that could do
     * the job. {@code shio push --content} has uploaded assets since SH145 and every test of it
     * injects a {@code fetch}, so the whole feature was unreachable from a real CLI and nothing
     * said so; SH437 found it by driving a conversion against a running instance.</p>
     *
     * <p>Narrowed, not blanket, and the discriminator is SH117's: the console posts this multipart
     * form with a session cookie and stays CSRF-checked, while the CLI holds none and cannot obtain
     * a token. An upload is also the least destructive write on this list — it adds a post, it
     * replaces nothing — and {@code /{id}} replace and delete are covered by the same narrowing for
     * the same reason a curator's browser is still checked.</p>
     */
    private static final String STATIC_FILE_PATH = "/api/v2/staticfile/**";
    /**
     * The agent surface (Block H) — stateless callers: a CDA {@code Key} token or the
     * {@code shio} CLI's Basic auth. CSRF-exempt only for those (SH167): the namespace is
     * {@code authenticated()} and nothing else, so a browser session reaches its writes too.
     */
    private static final String AGENT_PATH = "/api/v2/agent/**";

    /**
     * The one path under {@link #AGENT_PATH} that needs more than {@code authenticated()} (SH219).
     *
     * <p>Re-reading the operator's package directory changes what every later call to this instance
     * can apply, so it is a write however little it stores — and the role is the statement SH96
     * makes about the directory: whoever configured the path is vouching for what is in it, so
     * whoever reloads it should be somebody the instance already trusts with that.</p>
     *
     * <p>It must precede the agent namespace's own rule in both chains, or the blanket
     * {@code authenticated()} would answer first and the role would never be checked.</p>
     */
    private static final String BLUEPRINT_RELOAD_PATH = "/api/v2/agent/blueprints/reload";

    /**
     * The package marketplace (SH646, SH675) — reads for anyone authenticated, writes for an admin.
     *
     * <p>SH675 first classified the two writes as console-only, on the reasoning that installing a
     * stranger's package is the operator's act. <b>That reasoning survives; the mechanism was the
     * wrong one.</b> Requiring a <em>session</em> makes the capability unreachable from a terminal,
     * which is law P1's console-only failure and the non-goal that states it — and it does not
     * actually identify the operator, it identifies a browser.</p>
     *
     * <p>So the gate moved from "holds a session" to the credential itself: the CLI (SH677) works
     * with the Basic auth it already has, and reads stay open to anything authenticated.</p>
     *
     * <p><b>The writes should be {@code hasRole("ADMIN")} and are not yet (SH682).</b>
     * {@link #BLUEPRINT_RELOAD_PATH} is the argument: merely <em>re-reading</em> the operator's
     * package directory is already admin-gated because it changes what every later apply can do,
     * and installing is that act plus a download. It was written that way and taken out again,
     * because that role is currently unreachable for a locally authenticated user (SH681) — the
     * reload itself answers 403 to the seeded admin — and gating a new capability on a role
     * nothing grants ships it dead.</p>
     */
    private static final String MARKETPLACE_PATH = "/api/v2/marketplace/**";

    /**
     * The SH109 review queue. Console-authenticated, and reached by both kinds of caller: the
     * browser (session + XSRF cookie) and a script or CI job with HTTP Basic and no session.
     * So it gets SH117's narrowed exemption rather than a blanket one — the same discriminator,
     * for the same reason: a review queue only a browser can act on would make SH109 a
     * console-only feature, which law P1 forbids.
     */
    private static final String REVIEW_PATH = "/api/v2/review/**";
    /** The MCP endpoint (SH75) — the same surface over JSON-RPC; equally stateless, equally narrowed. */
    private static final String MCP_PATH = "/mcp";
    /** Tenant catalog (SH22) — writes are admin-only; {@code GET /current} stays open to any user. */
    private static final String TENANT_PATH = "/api/v2/tenant";
    private static final String TENANT_SUBPATH = "/api/v2/tenant/**";
    /** Self-service signup + tenant switch (SH22/C) — authenticated, not admin-gated. */
    private static final String TENANT_SIGNUP_PATH = "/api/v2/tenant/signup";
    private static final String TENANT_SWITCH_PATH = "/api/v2/tenant/*/switch";

    private final UserDetailsService userDetailsService;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:''}")
    private String issuerUri;
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:''}")
    private String clientId;
    @Value("${shio.url:'http://localhost:2710'}")
    private String shioUrl;
    @Value("${server.servlet.session.cookie.name:JSESSIONID}")
    private String sessionCookieName;

    PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();

    public ShSecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @SuppressWarnings("java:S4502")
    SecurityFilterChain filterChain(HttpSecurity http,
            ShLogoutHandler shLogoutHandler,
            ShApiTokenFilter shApiTokenFilter,
            ShAppProperties shAppProperties,
            ShAuthenticationEntryPoint shAuthenticationEntryPoint,
            ShOAuth2UserService shOAuth2UserService,
            ShOidcUserService shOidcUserService,
            ShTenantFilter shTenantFilter,
            ShCsrfAccessDeniedHandler shCsrfAccessDeniedHandler,
            ShAccessDeniedHandler shAccessDeniedHandler) {

        http.headers(header -> header.frameOptions(
                HeadersConfigurer.FrameOptionsConfig::disable)
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable));
        http.cors(Customizer.withDefaults());
        http.userDetailsService(userDetailsService);
        http.securityContext(securityContext -> securityContext
                .securityContextRepository(new HttpSessionSecurityContextRepository()));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();

        // SH622 — the chain's own 403 gets a body. Wired here rather than on a filter because an
        // authorization denial can come from any of them; the CSRF handler stays on CsrfFilter
        // alone (SH339) because the two refusals have different remedies.
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(shAuthenticationEntryPoint)
                .accessDeniedHandler(shAccessDeniedHandler));
        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // SH339 — a CSRF refusal answers with a body. Measured before this: 403, no content
                // type, empty body, which is SH189's defect on the surface whose caller is most
                // likely to be a program. Wired onto CsrfFilter alone, and deliberately not as the
                // chain-wide accessDeniedHandler: a CSRF rejection and an authorization denial have
                // different remedies, and one handler for both would have to guess which it is
                // sending. The status stays 403 — the console's axios interceptor retries on it.
                .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                    @Override
                    public <O extends CsrfFilter> O postProcess(O filter) {
                        filter.setAccessDeniedHandler(shCsrfAccessDeniedHandler);
                        return filter;
                    }
                })
                .ignoringRequestMatchers(
                        mvc.matcher(GRAPHQL_PATH),
                        // CDA writes (SH10) are token-authenticated (Key header) and stateless,
                        // so there is no session/XSRF cookie to validate; reads were never
                        // CSRF-checked. Narrowed by SH167 all the same — see the note below.
                        new ShStatelessRequestMatcher(mvc.matcher(CDA_PATH), sessionCookieName),
                        // Post-types-as-code (SH16) — the shio CLI calls it statelessly with HTTP
                        // Basic, so there is no session/XSRF cookie; same rationale as CDA. But
                        // SH117 retired the id-keyed console twin and pointed the React editor
                        // here too, so this one path now serves a cookie-bearing browser as well:
                        // the exemption is narrowed to callers that hold no session, or unifying
                        // the APIs would have meant dropping CSRF on the content model.
                        new ShStatelessRequestMatcher(mvc.matcher(POST_TYPE_PATH), sessionCookieName),
                        // SH327 — see RENDER_PATH. The endpoint that makes a site visible was the
                        // one an agent could not call, which is what SH339's inventory named.
                        new ShStatelessRequestMatcher(mvc.matcher(RENDER_PATH), sessionCookieName),
                        // SH344 — see PREVIEW_TOKEN_PATH: the last step of SH184's answer, and it
                        // grants nothing an agent's own credential does not already carry.
                        new ShStatelessRequestMatcher(mvc.matcher(PREVIEW_TOKEN_PATH), sessionCookieName),
                        // SH343 — see WEBHOOK_PATH. Narrowed only after the secret stopped coming
                        // back on the read, because narrowing is what widens who can read it.
                        new ShStatelessRequestMatcher(mvc.matcher(WEBHOOK_PATH), sessionCookieName),
                        new ShStatelessRequestMatcher(mvc.matcher(IMPORT_PATH), sessionCookieName),
                        // SH437 — see STATIC_FILE_PATH. Bytes are the one thing the agent surface
                        // cannot carry, so this was a gap no other exemption could stand in for.
                        new ShStatelessRequestMatcher(mvc.matcher(STATIC_FILE_PATH), sessionCookieName),
                        // SH109 — same narrowed exemption, same reason (see REVIEW_PATH).
                        new ShStatelessRequestMatcher(mvc.matcher(REVIEW_PATH), sessionCookieName),
                        // SH677 — see MARKETPLACE_PATH. The narrowing is the usual one: the CLI
                        // holds Basic auth and no session, the console holds both and stays
                        // CSRF-checked. The privilege is carried by the ADMIN role below rather
                        // than by which kind of client is calling.
                        new ShStatelessRequestMatcher(mvc.matcher(MARKETPLACE_PATH),
                                sessionCookieName),
                        // Agent writes (SH81/SH82) and MCP (SH75) — the callers are a
                        // token-authenticated agent and the stateless shio CLI, neither of which
                        // holds a session or an XSRF cookie, so the narrowed matcher costs them
                        // nothing. SH167 applied it anyway: `anyRequest().authenticated()` means a
                        // logged-in curator's session already reaches `POST /api/v2/agent/batch`,
                        // which deletes content — a blanket exemption there is a cross-site write
                        // on the most destructive endpoint in the product, and no console feature
                        // would have surfaced it. Authentication still applies, and the ACL +
                        // quota checks inside the batch engine are what actually bound it.
                        new ShStatelessRequestMatcher(mvc.matcher(AGENT_PATH), sessionCookieName),
                        new ShStatelessRequestMatcher(mvc.matcher(MCP_PATH), sessionCookieName),
                        mvc.matcher(SETUP_PATH),
                        mvc.matcher(ERROR_PATH),
                        mvc.matcher(LOGOUT_PATH),
                        mvc.matcher(BACK_CHANNEL_LOGOUT_PATH),
                        mvc.matcher(LOGIN_PATH)))
                .addFilterAfter(new ShCsrfHeaderFilter(), BasicAuthenticationFilter.class);
        http.addFilterBefore(shApiTokenFilter, BasicAuthenticationFilter.class);
        http.addFilterAfter(shTenantFilter, BasicAuthenticationFilter.class);

        if (shAppProperties.isKeycloak()) {
            String keycloakUrlFormat = String.format(
                    "%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                    issuerUri, clientId, shioUrl);
            http.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(
                    userInfo -> userInfo
                            .userService(shOAuth2UserService)
                            .oidcUserService(shOidcUserService)));
            // Single Logout: accept Keycloak's OIDC Back-Channel Logout so a logout
            // at the Console (which ends the shared SSO session) cascades here — shio
            // invalidates its own session without the user having to visit /logout.
            // Requires this app's own Keycloak client (viglet-shio / per-silo
            // viglet-shio-<key>) with a Backchannel Logout URL; the shared viglet-cloud
            // client cannot fan out to every app.
            http.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()));
            http.authorizeHttpRequests(authorizeRequests -> {
                authorizeRequests.requestMatchers(
                        mvc.matcher(ERROR_PATH),
                        // OIDC Back-Channel Logout callback (Single Logout) —
                        // authenticated by Keycloak's signed logout token, not a session.
                        mvc.matcher(BACK_CHANNEL_LOGOUT_PATH),
                        mvc.matcher("/api/v2/discovery"),
                        mvc.matcher("/api/v2/csrf"),
                        mvc.matcher("/api/v2/ping"),
                        mvc.matcher("/assets/**"),
                        mvc.matcher("/favicon.ico"),
                        mvc.matcher("/*.png"),
                        mvc.matcher("/manifest.json"),
                        mvc.matcher("/browserconfig.xml"),
                        mvc.matcher("/content/**"),
                        // NOT /preview/** — see the note on the other chain. It was here from
                        // before the renderer existed and made every draft anonymous-readable.
                        mvc.matcher("/sites/**"),
                        mvc.matcher(SETUP_PATH),
                        mvc.matcher(LOGIN_PATH)).permitAll();
                // CDA write surface (SH10) — must precede the GET rule; a WRITE-scope token
                // carries ROLE_CDA_WRITE (see ShApiTokenFilter).
                // SH633 — ahead of the CDA write rule, for the reason on FORM_PATH: a form token
                // has no ROLE_CDA_WRITE and must not be given one.
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, FORM_PATH))
                        .hasRole(ROLE_FORM);
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.PATCH, CDA_PATH))
                        .hasRole(ROLE_CDA_WRITE);
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, CDA_PATH))
                        .hasRole(ROLE_CDA_WRITE);
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.GET, CDA_PATH))
                        .hasRole("CDA");
                // GraphQL delivery (SH4) is a CDA surface — token-authenticated, POST reads.
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, GRAPHQL_PATH))
                        .hasRole("CDA");
                // Self-service tenant signup and switch from SH22 part C are for any
                // authenticated user, so they must precede the admin catch-all below.
                authorizeRequests.requestMatchers(
                        mvc.matcher(HttpMethod.POST, TENANT_SIGNUP_PATH),
                        mvc.matcher(HttpMethod.POST, TENANT_SWITCH_PATH))
                        .authenticated();
                // SH219 — the blueprint reload is the one admin-gated path on the agent surface.
                authorizeRequests.requestMatchers(
                        mvc.matcher(HttpMethod.POST, BLUEPRINT_RELOAD_PATH))
                        .hasRole("ADMIN");
                // SH677 — the marketplace writes are `authenticated()`, which is the blanket rule
                // below and therefore no line at all. That is NOT the gate they want: installing a
                // package is BLUEPRINT_RELOAD_PATH's act plus a download, so ADMIN is the right
                // answer and it was written that way first.
                //
                // It is not here because `hasRole("ADMIN")` is currently unreachable for a locally
                // authenticated user (SH681): ShAuthorityResolver grants ROLE_ADMIN from the
                // Administrator group and is wired only into the OAuth2/OIDC services, while
                // ShCustomUserDetailsService builds authorities from ShRole rows alone — and
                // `GET /api/v2/role` answers `[]` on a fresh instance. Measured, not inferred:
                // POST /agent/blueprints/reload answers 403 to the seeded admin, over a session
                // and over Basic auth alike, and has since SH219. Gating a new capability on a
                // role nothing grants ships it dead.
                //
                // SH682 tightens this to ADMIN once SH681 makes the role real. Both are filed, so
                // this comment is a pointer rather than a promise nobody wrote down.
                // Tenant catalog writes (SH22) are admin-only; GET /current + listing stay authenticated.
                authorizeRequests.requestMatchers(
                        mvc.matcher(HttpMethod.POST, TENANT_PATH),
                        mvc.matcher(HttpMethod.POST, TENANT_SUBPATH),
                        mvc.matcher(HttpMethod.PUT, TENANT_SUBPATH),
                        mvc.matcher(HttpMethod.DELETE, TENANT_SUBPATH))
                        .hasRole("ADMIN");
                authorizeRequests.anyRequest().authenticated();
            });
            http.logout(logout -> logout.addLogoutHandler(shLogoutHandler)
                    .logoutSuccessUrl(keycloakUrlFormat));
        } else {
            http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(shAuthenticationEntryPoint))
                    .authorizeHttpRequests(authorizeRequests -> {
                        authorizeRequests.requestMatchers(
                                mvc.matcher(ERROR_PATH),
                                mvc.matcher("/api/v2/discovery"),
                                mvc.matcher("/api/v2/csrf"),
                                mvc.matcher("/api/v2/ping"),
                                mvc.matcher(LOGOUT_PATH),
                                mvc.matcher("/index.html"),
                                mvc.matcher("/login/**"),
                                mvc.matcher("/sites/**"),
                                mvc.matcher("/__tur/**"),
                                mvc.matcher("/"),
                                mvc.matcher("/assets/**"),
                                mvc.matcher("/favicon.ico"),
                                mvc.matcher("/*.png"),
                                mvc.matcher("/manifest.json"),
                                mvc.matcher("/browserconfig.xml"),
                                mvc.matcher("/content/**"),
                                // `/preview/**` is deliberately absent, and used to be here.
                                //
                                // The entry predates the renderer: it was written when `/preview`
                                // meant something else on the legacy console, and SH183 then built
                                // `GET /preview/{site}/{url}` on the same path — defaulting to
                                // DRAFT. The first matching rule wins, so the preview never reached
                                // the `anyRequest().authenticated()` its own class note says it
                                // sits under, and every unpublished draft on the instance was
                                // readable by anyone who could reach the port. Measured, not
                                // reasoned: an anonymous GET answered 404 for an unknown site —
                                // which means the request had already reached the renderer — and
                                // 200 for `/preview/_shio/editor.js`.
                                //
                                // A curator's browser carries a session, so the console and the
                                // Universal Editor are unaffected. `ShPreviewIsNotPublicTest`
                                // refuses the next reopening.
                                mvc.matcher("/console/**"),
                                mvc.matcher("/*/console/**"),
                                mvc.matcher("/graphiql/**"),
                                mvc.matcher("/store/**"),
                                mvc.matcher("/file_source/**"),
                                // SH304 removed `/universal-editor/**`: the only thing served there
                                // was a second, unspecified implementation of the SH185 bridge that
                                // posted to "*" and never checked event.origin. External editing is
                                // unsupported until the SDK emits the annotations (SH350); the
                                // specified bridge is injected into Shio's own preview instead.
                                mvc.matcher(SETUP_PATH),
                                mvc.matcher("/setup"),
                                mvc.matcher("/setup/**"),
                                mvc.matcher(LOGIN_PATH)).permitAll();
                        // CDA write surface (SH10) — must precede the GET rule; a WRITE-scope token
                        // carries ROLE_CDA_WRITE (see ShApiTokenFilter).
                        // SH633 — ahead of the CDA write rule, for the reason on FORM_PATH: a form token
                        // has no ROLE_CDA_WRITE and must not be given one.
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, FORM_PATH))
                                .hasRole(ROLE_FORM);
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.PATCH, CDA_PATH))
                                .hasRole(ROLE_CDA_WRITE);
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, CDA_PATH))
                                .hasRole(ROLE_CDA_WRITE);
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.GET, CDA_PATH))
                                .hasRole("CDA");
                        // GraphQL delivery (SH4) is a CDA surface — token-authenticated, POST reads.
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, GRAPHQL_PATH))
                                .hasRole("CDA");
                        // SH219 — the blueprint reload, admin-gated on this chain too.
                        authorizeRequests.requestMatchers(
                                mvc.matcher(HttpMethod.POST, BLUEPRINT_RELOAD_PATH))
                                .hasRole("ADMIN");
                        // SH677 — the marketplace writes are authenticated() on this chain too;
                        // see the note beside the other chain's blueprint reload for why the ADMIN
                        // gate they want is SH682 rather than this commit.
                        // Tenant catalog writes (SH22) are admin-only; GET /current + listing stay authenticated.
                        authorizeRequests.requestMatchers(
                                mvc.matcher(HttpMethod.POST, TENANT_PATH),
                                mvc.matcher(HttpMethod.POST, TENANT_SUBPATH),
                                mvc.matcher(HttpMethod.PUT, TENANT_SUBPATH),
                                mvc.matcher(HttpMethod.DELETE, TENANT_SUBPATH))
                                .hasRole("ADMIN");
                        authorizeRequests.anyRequest().authenticated();
                    });
            http.logout(logout -> logout
                    .logoutRequestMatcher(mvc.matcher(HttpMethod.GET, LOGOUT_PATH))
                    .addLogoutHandler(shLogoutHandler)
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies(sessionCookieName, "XSRF-TOKEN")
                    .logoutSuccessUrl("/login"));
        }
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        try {
            return authenticationConfiguration.getAuthenticationManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get AuthenticationManager", e);
        }
    }

    @Bean
    HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
}
