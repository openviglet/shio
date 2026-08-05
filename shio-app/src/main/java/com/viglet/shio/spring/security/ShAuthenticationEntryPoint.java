package com.viglet.shio.spring.security;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The 401 an unauthenticated request gets, and — since SH450 — whether it carries a challenge.
 *
 * <h2>Why the default was replaced, and why the replacement was half wrong</h2>
 *
 * <p>Spring's own entry point emits {@code WWW-Authenticate: Basic}, which makes a browser open its
 * native credential dialog. On the console that is the wrong answer to every unauthenticated
 * {@code fetch}: the React app has a login page, and a native dialog in front of it is both uglier
 * and unanswerable — a user who dismisses it gets a blank console. So this one answers JSON.</p>
 *
 * <p>That is right for the API and wrong for <b>{@code /preview/**}</b>, which is a page a
 * <em>browser</em> is meant to open with HTTP Basic. Measured (SH450): {@code shio snapshot
 * --against} reported {@code 100.00% of pixels} on a replica whose markup is the source's, verbatim,
 * and the PNG it wrote is a white page reading {@code {"error":"Unauthorized"}}. The credentials
 * were computed and handed to Playwright — but {@code httpCredentials} answers a <b>challenge</b>
 * and does not send {@code Authorization} unprompted, so with no {@code WWW-Authenticate} there was
 * nothing to answer and the browser never retried. {@code curl -u} works against the same URL for
 * the one reason that matters here: it can be told to send the header first.</p>
 *
 * <h2>The rule, and why it is a path and not a heuristic</h2>
 *
 * <p>Challenge where a browser is the intended client and Basic is the intended credential; answer
 * JSON everywhere else. That is exactly {@code /preview/**} — an HTML route, authenticated, reached
 * by a human's browser or by a headless one. It is deliberately <b>not</b> keyed on
 * {@code Accept: text/html}: a console {@code fetch} can carry that header too, and the console must
 * never get a dialog. A path is checkable, and {@code ShPreviewChallengeTest} checks both halves.</p>
 *
 * <h2>And a path nothing serves answers 404, not 401 (SH459)</h2>
 *
 * <p>Measured: {@code GET /fonts/x.woff2} and {@code GET /nope} answered <b>401</b>, while
 * {@code /assets/missing.js} answered 404 — the difference being only that {@code /assets/**} is on
 * the {@code permitAll} list, so it reached the dispatcher and got the truth. Every other root path
 * fell through to {@code anyRequest().authenticated()} and a visitor asking for a missing font was
 * told to authenticate for a file that does not exist anywhere. Same family as SH450: what an
 * anonymous client receives is a fact about the security chain rather than about any controller.</p>
 *
 * <p><b>Answered here rather than by widening {@code permitAll}, and that is the safer of the two.</b>
 * Permitting the path would make the resource handler reachable anonymously, which is a change to
 * <em>what is served</em>; this changes only <em>what the refusal says</em>. Nothing new is
 * readable. The condition is "no controller claims this URI" — asked of
 * {@link RequestMappingHandlerMapping}, so SH167's default holds exactly: a new controller is
 * claimed the moment it exists, and its unauthenticated callers keep getting the 401 the console's
 * login flow depends on. A resource handler is deliberately <em>not</em> consulted: Spring Boot maps
 * {@code classpath:/public/} at {@code /**}, so it claims every path and asking it would make this
 * rule never fire.</p>
 *
 * <p>404 rather than 403 for an absent path, and 404 is also the <em>less</em> disclosing answer —
 * 401 tells a prober that a path is behind a door, and this tells it nothing.</p>
 *
 * @author Alexandre Oliveira
 */
@Component
public class ShAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** The one route whose caller is a browser that should authenticate with Basic. */
    static final String CHALLENGED_PREFIX = "/preview/";

    /**
     * The realm. Named rather than left to the container, because it is what a browser shows in its
     * dialog and "Realm" would tell a curator nothing about which system is asking.
     */
    static final String CHALLENGE = "Basic realm=\"Shio preview\", charset=\"UTF-8\"";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

    /**
     * Lazily, through a provider: this bean is built by the security chain and the mapping by the MVC
     * one, and injecting it directly is a cycle between the two configurations.
     */
    public ShAuthenticationEntryPoint(ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        if (unclaimed(request)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Not Found\"}");
            return;
        }
        if (challenges(request)) {
            // The body stays JSON so a programmatic caller reads one shape everywhere; the header is
            // what a browser acts on, and it never renders the body it is attached to.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    /**
     * True when no {@code @RequestMapping} anywhere would have served this URI (SH459).
     *
     * <p>Read-only as far as the caller is concerned: {@code getHandler} sets the request attributes
     * the dispatcher would set anyway, on a request that is about to be terminated. A
     * {@code HttpRequestMethodNotSupportedException} means the path <em>is</em> a controller's and
     * only the method is wrong, so it counts as claimed — 401 is then the honest answer and 404
     * would be a lie about a route that exists.</p>
     */
    private boolean unclaimed(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // Every mapping of this type, not "the" one: the actuator contributes a second
        // (`controllerEndpointHandlerMapping`), and asking only the MVC one would call an actuator
        // endpoint absent. A single-bean injection does not merely miss them - it throws
        // NoUniqueBeanDefinitionException inside the entry point, which is a 500 in place of a 401.
        return handlerMapping.orderedStream().noneMatch(mapping -> claims(mapping, request));
    }

    private static boolean claims(RequestMappingHandlerMapping mapping, HttpServletRequest request) {
        try {
            return mapping.getHandler(request) != null;
        } catch (Exception undecidable) {
            // Treated as claimed, which keeps this incapable of turning a real route's 401 into a
            // 404. `HttpRequestMethodNotSupportedException` is the ordinary case and means exactly
            // that: the path is a controller's and only the verb is wrong. Not recorded - the
            // degradation is that a refusal stays the refusal it already was.
            return true;
        }
    }

    /** Path-prefix on the request's own URI, which is what the rule above is stated in terms of. */
    private static boolean challenges(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri != null && uri.startsWith(CHALLENGED_PREFIX);
    }
}
