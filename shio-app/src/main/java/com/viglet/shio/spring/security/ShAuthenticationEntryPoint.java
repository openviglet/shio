package com.viglet.shio.spring.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

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

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        if (challenges(request)) {
            // The body stays JSON so a programmatic caller reads one shape everywhere; the header is
            // what a browser acts on, and it never renders the body it is attached to.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    /** Path-prefix on the request's own URI, which is what the rule above is stated in terms of. */
    private static boolean challenges(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri != null && uri.startsWith(CHALLENGED_PREFIX);
    }
}
