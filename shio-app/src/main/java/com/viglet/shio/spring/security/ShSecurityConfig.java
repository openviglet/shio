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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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
    public static final String ERROR_PATH = "/error/**";
    /** The CDA namespace (SH1) — read (GET) for ROLE_CDA, write (SH10) for ROLE_CDA_WRITE. */
    private static final String CDA_PATH = "/api/v2/cda/**";
    /** Post-types-as-code admin API (SH16) — console-authenticated, used by the stateless {@code shio} CLI. */
    private static final String POST_TYPE_PATH = "/api/v2/post-type/**";
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
            ShTenantFilter shTenantFilter) {

        http.headers(header -> header.frameOptions(
                HeadersConfigurer.FrameOptionsConfig::disable)
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable));
        http.cors(Customizer.withDefaults());
        http.userDetailsService(userDetailsService);
        http.securityContext(securityContext -> securityContext
                .securityContextRepository(new HttpSessionSecurityContextRepository()));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();

        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        mvc.matcher("/graphql"),
                        // CDA writes (SH10) are token-authenticated (Key header) and stateless,
                        // so there is no session/XSRF cookie to validate; reads were never CSRF-checked.
                        mvc.matcher(CDA_PATH),
                        // Post-types-as-code admin API (SH16) — the shio CLI calls it statelessly
                        // with HTTP Basic, so there is no session/XSRF cookie; same rationale as CDA.
                        mvc.matcher(POST_TYPE_PATH),
                        mvc.matcher("/api/v2/setup"),
                        mvc.matcher(ERROR_PATH),
                        mvc.matcher(LOGOUT_PATH),
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
            http.authorizeHttpRequests(authorizeRequests -> {
                authorizeRequests.requestMatchers(
                        mvc.matcher(ERROR_PATH),
                        mvc.matcher("/api/v2/discovery"),
                        mvc.matcher("/api/v2/csrf"),
                        mvc.matcher("/api/v2/ping"),
                        mvc.matcher("/assets/**"),
                        mvc.matcher("/favicon.ico"),
                        mvc.matcher("/*.png"),
                        mvc.matcher("/manifest.json"),
                        mvc.matcher("/browserconfig.xml"),
                        mvc.matcher("/content/**"),
                        mvc.matcher("/preview/**"),
                        mvc.matcher("/sites/**"),
                        mvc.matcher("/api/v2/setup"),
                        mvc.matcher(LOGIN_PATH)).permitAll();
                // CDA write surface (SH10) — must precede the GET rule; a WRITE-scope token
                // carries ROLE_CDA_WRITE (see ShApiTokenFilter).
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.PATCH, CDA_PATH))
                        .hasRole("CDA_WRITE");
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, CDA_PATH))
                        .hasRole("CDA_WRITE");
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.GET, CDA_PATH))
                        .hasRole("CDA");
                // GraphQL delivery (SH4) is a CDA surface — token-authenticated, POST reads.
                authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, "/graphql"))
                        .hasRole("CDA");
                // Self-service tenant signup + switch (SH22/C) are for any authenticated
                // user — must precede the admin catch-all below.
                authorizeRequests.requestMatchers(
                        mvc.matcher(HttpMethod.POST, TENANT_SIGNUP_PATH),
                        mvc.matcher(HttpMethod.POST, TENANT_SWITCH_PATH))
                        .authenticated();
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
                                mvc.matcher("/welcome/**"),
                                mvc.matcher("/login/**"),
                                mvc.matcher("/sites/**"),
                                mvc.matcher("/__tur/**"),
                                mvc.matcher("/"),
                                mvc.matcher("/assets/**"),
                                mvc.matcher("/thirdparty/**"),
                                mvc.matcher("/fonts/**"),
                                mvc.matcher("/favicon.ico"),
                                mvc.matcher("/*.png"),
                                mvc.matcher("/manifest.json"),
                                mvc.matcher("/browserconfig.xml"),
                                mvc.matcher("/content/**"),
                                mvc.matcher("/preview/**"),
                                mvc.matcher("/media/**"),
                                mvc.matcher("/console/**"),
                                mvc.matcher("/*/console/**"),
                                mvc.matcher("/graphiql/**"),
                                mvc.matcher("/css/**"),
                                mvc.matcher("/js/**"),
                                mvc.matcher("/img/**"),
                                mvc.matcher("/store/**"),
                                mvc.matcher("/file_source/**"),
                                mvc.matcher("/template/**"),
                                mvc.matcher("/vendor/**"),
                                mvc.matcher("/svg/**"),
                                mvc.matcher("/universal-editor/**"),
                                mvc.matcher("/api/v2/setup"),
                                mvc.matcher("/setup"),
                                mvc.matcher("/setup/**"),
                                mvc.matcher(LOGIN_PATH)).permitAll();
                        // CDA write surface (SH10) — must precede the GET rule; a WRITE-scope token
                        // carries ROLE_CDA_WRITE (see ShApiTokenFilter).
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.PATCH, CDA_PATH))
                                .hasRole("CDA_WRITE");
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, CDA_PATH))
                                .hasRole("CDA_WRITE");
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.GET, CDA_PATH))
                                .hasRole("CDA");
                        // GraphQL delivery (SH4) is a CDA surface — token-authenticated, POST reads.
                        authorizeRequests.requestMatchers(mvc.matcher(HttpMethod.POST, "/graphql"))
                                .hasRole("CDA");
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
