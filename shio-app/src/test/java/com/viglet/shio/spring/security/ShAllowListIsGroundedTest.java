/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Every {@code permitAll()} entry names something that still exists, and says why (SH363).
 *
 * <h2>Why this is not the test next door</h2>
 *
 * <p>{@link ShAnonymousRouteAccessTest} is SH363's other half and it works from the request in:
 * for every route a controller registers, an anonymous call must be refused or not, as a table
 * declares. That is the stronger check where a route exists, and it is <b>structurally blind to
 * the thing that actually caused SH359</b> — an allow-list entry matching <em>no route at all</em>.
 * There is nothing to probe, so no request can find it, and it sits in the config reading
 * perfectly well until somebody mounts a controller on the prefix years later.</p>
 *
 * <p>That is not a hypothetical, it is the incident. {@code mvc.matcher("/preview/**")} was written
 * when {@code /preview} meant something on the legacy console. The console went. The entry stayed,
 * matching nothing, harmless and invisible. SH183 then built {@code GET /preview/{site}/{url}}
 * defaulting to {@code DRAFT}, the first matching rule won, and every unpublished draft on the
 * instance became readable by anyone who could reach the port. Nobody widened an allow-list; a
 * dead entry <b>adopted</b> a new route.</p>
 *
 * <p>So this reads the config from the other side: every pattern inside a {@code permitAll()} is
 * <b>grounded</b> — it matches a live controller route, or it is declared here as a non-controller
 * resource with the reason it is public. An entry that is neither fails the build.</p>
 *
 * <h2>It reads the source, and that is the point rather than a shortcut</h2>
 *
 * <p>Spring's authorization rules are not introspectable as a list once built, and a test that
 * asked the container which paths are permitted would be asking the same object the config
 * produced — restating the config, which SH363 explicitly rules out. The allow-list is a
 * <em>literal</em> in one file and the question is whether each literal still denotes anything, so
 * the file is the input. Constant references ({@code mvc.matcher(SETUP_PATH)}) are resolved from
 * the same source, because half the entries are written that way and skipping them would exempt
 * exactly the paths somebody cared enough to name.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAllowListIsGroundedTest {

    private static final Path CONFIG = Path.of(
            "src/main/java/com/viglet/shio/spring/security/ShSecurityConfig.java");

    /**
     * Allow-list entries that are deliberately not controller routes, and why each is public.
     *
     * <p>A declaration, not a suppression: the value is the reason, and an entry with no reason
     * cannot be added without writing one. These are served by the servlet container, by Spring
     * Boot's static resource handling, or by a filter — so no {@code RequestMappingHandlerMapping}
     * entry will ever match them, and without this table they would read as ungrounded.</p>
     *
     * <p>Keeping it here rather than beside the config is deliberate. A reason written next to the
     * line it excuses is read by whoever is already editing that line; one written in the test that
     * would otherwise fail is read by whoever is trying to make it pass, which is the person adding
     * the next entry.</p>
     */
    private static final Map<String, String> NOT_A_CONTROLLER = new LinkedHashMap<>();

    static {
        NOT_A_CONTROLLER.put("/error/**", "Spring Boot's error dispatch; refusing it would hide every error page");
        NOT_A_CONTROLLER.put("/logout", "Spring Security's own logout filter, before any controller");
        NOT_A_CONTROLLER.put("/logout/connect/back-channel/**",
                "OIDC back-channel logout, authenticated by Keycloak's signed logout token rather than a session");
        NOT_A_CONTROLLER.put("/assets/**", "the console's built JS/CSS bundle, static");
        NOT_A_CONTROLLER.put("/favicon.ico", "static, and requested by every browser before login");
        NOT_A_CONTROLLER.put("/*.png", "static app icons at the root, referenced by manifest.json");
        NOT_A_CONTROLLER.put("/manifest.json", "the PWA manifest, read before a session exists");
        NOT_A_CONTROLLER.put("/browserconfig.xml", "the Windows tile manifest, same reason");
        NOT_A_CONTROLLER.put("/content/**", "static content served from the classpath");
        NOT_A_CONTROLLER.put("/store/**", "the runtime store directory, served as files");
        NOT_A_CONTROLLER.put("/file_source/**", "uploaded static files (SH25), served as files");
        NOT_A_CONTROLLER.put("/index.html", "the console shell; the app decides what an anonymous visitor sees");
        NOT_A_CONTROLLER.put("/login/**", "the OAuth2 authorization redirect endpoints, before authentication");
        NOT_A_CONTROLLER.put("/console/**", "the React console's client-side routes, all serving the shell");
        // SH926 — the bento route tree, a sibling of /console rather than a child of it (SH888).
        // Grounded the same way and for the same reason: what these serve is the shell, and every
        // API it then calls is governed by the rules below.
        NOT_A_CONTROLLER.put("/bento", "the bento shell's index route, serving the same SPA shell");
        NOT_A_CONTROLLER.put("/bento/**", "the bento route tree's client-side routes (SH888)");
        NOT_A_CONTROLLER.put("/bento-vocabulary", "the bento layer's proving page (SH887), outside every shell");
        NOT_A_CONTROLLER.put("/bento-vocabulary/**", "the same page's client-side routes");
        NOT_A_CONTROLLER.put("/graphiql/**", "the GraphiQL explorer's static assets; the /graphql endpoint itself is CDA-scoped");
        NOT_A_CONTROLLER.put("/__tur/**", "the Turing integration's callback prefix");
        NOT_A_CONTROLLER.put("/setup", "the first-run setup shell (SH6), open only until an admin exists");
        NOT_A_CONTROLLER.put("/setup/**", "the same shell's client-side routes");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** Every literal inside a {@code permitAll()} call, from both filter chains. */
    private static Set<String> allowListed() throws IOException {
        String source = Files.readString(CONFIG, StandardCharsets.UTF_8);
        Map<String, String> constants = new LinkedHashMap<>();
        Matcher declared = Pattern
                .compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"\\s*;")
                .matcher(source);
        while (declared.find()) {
            constants.put(declared.group(1), declared.group(2));
        }

        Set<String> patterns = new LinkedHashSet<>();
        // Each block runs from a `requestMatchers(` to the `.permitAll()` that closes it. Anchoring
        // on permitAll rather than on requestMatchers is what keeps the role-scoped blocks out:
        // `hasRole("CDA")` on the same shape of call is not an allow-list entry.
        Matcher block = Pattern.compile("requestMatchers\\(([\\s\\S]*?)\\)\\s*\\.permitAll\\(\\)")
                .matcher(source);
        while (block.find()) {
            String body = block.group(1);
            Matcher matcher = Pattern.compile("mvc\\.matcher\\(\\s*([^)]*?)\\s*\\)").matcher(body);
            while (matcher.find()) {
                String argument = matcher.group(1).trim();
                if (argument.startsWith("\"")) {
                    patterns.add(argument.substring(1, argument.length() - 1));
                } else if (constants.containsKey(argument)) {
                    patterns.add(constants.get(argument));
                }
                // Anything else — an expression, a call — is not a literal path and cannot be
                // grounded by reading. There are none today; if one appears, the count assertion
                // below is what notices, because it drops.
            }
        }
        return patterns;
    }

    /** Every path pattern a Shio controller registers, as parsed patterns. */
    private List<PathPattern> controllerRoutes() {
        PathPatternParser parser = new PathPatternParser();
        RequestMappingHandlerMapping handlerMapping = webApplicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<PathPattern> routes = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getName().startsWith("com.viglet.shio")) {
                return;
            }
            for (String pattern : patternsOf(info)) {
                routes.add(parser.parse(pattern));
            }
        });
        return routes;
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return Set.of();
    }

    /**
     * A concrete path standing in for an allow-list pattern, so it can be matched against a route.
     *
     * <p>{@code /*.png} is the one shape that has to be special-cased: a wildcard inside a segment
     * is a filename, not a path variable.</p>
     */
    private static String concrete(String pattern) {
        String path = pattern.replace("/**", "/x")
                .replaceAll("\\{[^/{}]+}", "x")
                .replace("/*.", "/x.")
                .replace("/*", "/x");
        return path.isEmpty() ? "/" : path;
    }

    @Test
    @DisplayName("every permitAll entry matches a live route or is declared as a static resource")
    void everyAllowListEntryIsGrounded() throws IOException {
        List<PathPattern> routes = controllerRoutes();
        List<String> ungrounded = new ArrayList<>();

        for (String pattern : allowListed()) {
            if (NOT_A_CONTROLLER.containsKey(pattern)) {
                continue;
            }
            PathContainer path = PathContainer.parsePath(concrete(pattern));
            boolean matchesARoute = routes.stream().anyMatch(route -> route.matches(path));
            if (!matchesARoute) {
                ungrounded.add(pattern);
            }
        }

        assertTrue(ungrounded.isEmpty(),
                "These permitAll() entries in ShSecurityConfig match no controller route and are not "
                        + "declared in NOT_A_CONTROLLER. An entry that denotes nothing is not harmless: it is "
                        + "what /preview/** was for years before SH183 mounted the renderer on it and every "
                        + "draft became anonymous-readable (SH359). Delete it, or declare it with the reason "
                        + "it is public (SH363): " + ungrounded);
    }

    @Test
    @DisplayName("a declared static exemption that has become a real route is not left undeclared")
    void noStaticExemptionQuietlyBecameARoute() throws IOException {
        List<PathPattern> routes = controllerRoutes();
        Set<String> listed = allowListed();
        List<String> adopted = new ArrayList<>();

        for (Map.Entry<String, String> exemption : NOT_A_CONTROLLER.entrySet()) {
            if (!listed.contains(exemption.getKey())) {
                continue;
            }
            PathContainer path = PathContainer.parsePath(concrete(exemption.getKey()));
            if (routes.stream().anyMatch(route -> route.matches(path))) {
                adopted.add(exemption.getKey());
            }
        }

        assertTrue(adopted.isEmpty(),
                "These paths are declared here as 'not a controller' and a controller now answers them. "
                        + "That is the SH359 sequence exactly, one step from the end: the exemption was true "
                        + "when written and a later mapping inherited it. Decide whether the new route is "
                        + "meant to be anonymous — if it is, classify it PUBLIC in ShAnonymousRouteAccessTest "
                        + "and remove it from NOT_A_CONTROLLER; if it is not, take it off the allow-list "
                        + "(SH363): " + adopted);
    }

    @Test
    @DisplayName("every declared exemption carries a reason, and none has outlived its entry")
    void theDeclarationsAreLiveAndSigned() throws IOException {
        Set<String> listed = allowListed();

        List<String> unsigned = NOT_A_CONTROLLER.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();
        assertTrue(unsigned.isEmpty(),
                "An exemption with no reason is a suppression nobody signed (SH363): " + unsigned);

        // Read in both directions, the way every ledger in this repository is: an exemption whose
        // allow-list entry has gone is a permission the config no longer grants, and leaving it here
        // pre-authorizes whoever adds that path back.
        List<String> stale = NOT_A_CONTROLLER.keySet().stream()
                .filter(pattern -> !listed.contains(pattern))
                .toList();
        assertTrue(stale.isEmpty(),
                "These paths are declared here as public and no permitAll() entry names them any more. "
                        + "Delete the declaration — kept, it silently pre-approves the next entry for that "
                        + "path (SH363): " + stale);
    }

    @Test
    @DisplayName("SH363: the reader actually found the allow-list, and it is not empty")
    void theReaderIsNotVacuous() throws IOException {
        Set<String> listed = allowListed();

        // A parse that found nothing would make all three assertions above pass forever, which is
        // the failure mode every instrument in this repository is warned about. The floor is well
        // under today's count on purpose: this asserts the reader works, not the config's shape.
        assertTrue(listed.size() > 15,
                "only " + listed.size() + " permitAll() entr(y|ies) parsed from " + CONFIG
                        + " — the config's shape changed and this reader is now blind");

        // The load-bearing negative, and the whole reason SH359 is a closed loop rather than a fixed
        // line: /preview/** must not be back on the allow-list, in either chain.
        assertFalse(listed.contains("/preview/**"),
                "/preview/** is on the permitAll list again. That is SH359 verbatim: the renderer "
                        + "defaults to DRAFT, so this makes every unpublished page on the instance readable "
                        + "by anyone who can reach the port (SH363)");

        assertTrue(listed.contains("/sites/**"),
                "/sites/** is the published delivery path and must stay public, or the site is only "
                        + "visible to people who can log in");
    }
}
