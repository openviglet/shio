/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;

import com.viglet.shio.spring.security.auth.ShApiTokenContext;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantMembershipService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.viglet.shio.api.token.dto.ShApiTokenRequest;
import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.token.ShApiTokenRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * CDA (Content Delivery API) Token management.
 * Tokens are read-only: they only allow fetching published content.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/api-token")
@Tag(name = "API Token", description = "CDA Token management for read-only content access")
@Slf4j
public class ShApiTokenAPI {

    private final ShApiTokenRepository shApiTokenRepository;
    private final ShTenantMembershipService shTenantMembershipService;
    private final String sessionCookieName;

    public ShApiTokenAPI(ShApiTokenRepository shApiTokenRepository,
                         ShTenantMembershipService shTenantMembershipService,
                         @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) {
        this.shApiTokenRepository = shApiTokenRepository;
        this.shTenantMembershipService = shTenantMembershipService;
        this.sessionCookieName = sessionCookieName;
    }

    @GetMapping
    public List<ShApiToken> list() {
        // Durable tokens only — short-lived preview tokens (SH13) are hidden here.
        return shApiTokenRepository.findByEphemeralFalseOrderByTitleAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShApiToken> get(@PathVariable String id) {
        return shApiTokenRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Issue a token.
     *
     * <p>SH1379 — reachable without a session, for a <b>person</b> only. A device token or HTTP
     * Basic caller may issue one if it runs the tenant the request resolved (OWNER or ADMIN there,
     * or a platform admin), and the token lands in that tenant through {@code @TenantId}. An API
     * token may never issue another, whatever its scope: a credential that can mint credentials is
     * the escalation Block L exists to prevent. A console session keeps its old reach.</p>
     */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody ShApiTokenRequest body, Principal principal,
                                         HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ShApiTokenContext) {
            return refuse("An API token cannot issue another",
                    "Issue it from the console, or as the person who runs the tenant (a device token "
                            + "or HTTP Basic). A credential that mints credentials is never a token's.");
        }
        if (!hasSessionCookie(request) && !runsTheTenant(authentication)) {
            return refuse("Only the tenant's OWNER or ADMIN issues a token without a session",
                    "You are signed in to '" + ShTenantContext.getTenantId() + "' without being its "
                            + "OWNER or ADMIN. Ask one of them, or issue it from the console.");
        }
        return ResponseEntity.ok(issue(body, principal));
    }

    private ShApiToken issue(ShApiTokenRequest body, Principal principal) {
        ShApiToken token = new ShApiToken();
        token.setTitle(body.title());
        token.setDescription(body.description());
        token.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        token.setOwner(principal != null ? principal.getName() : "system");
        token.setCreatedDate(Instant.now());
        token.setEnabled(true);
        token.setScope(body.scope() != null ? body.scope() : ShApiTokenScope.READ);
        token.setSiteIds(body.siteIds() != null ? new HashSet<>(body.siteIds()) : new HashSet<>());
        token.setEnv(body.env() != null ? body.env() : ShApiTokenEnv.PROD);
        token.setMayPublish(body.mayPublish());

        shApiTokenRepository.saveAndFlush(token);
        log.info("Created CDA API token '{}' by {}", token.getTitle(), token.getOwner());
        return token;
    }

    /** A platform admin, or an OWNER or ADMIN of the tenant this request resolved to. */
    private boolean runsTheTenant(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean platformAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return platformAdmin
                || shTenantMembershipService.isActiveManager(ShTenantContext.getTenantId(), authentication.getName());
    }

    /**
     * The same discriminator {@code ShStatelessRequestMatcher} uses for the CSRF exemption, so the
     * two agree on which callers are session-less: the cookie, not {@code getSession(false)}.
     */
    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (sessionCookieName.equalsIgnoreCase(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<Object> refuse(String title, String fix) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("status", 403);
        problem.put("title", title);
        problem.put("fix", fix);
        return ResponseEntity.status(403).body(problem);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShApiToken> update(@PathVariable String id, @RequestBody ShApiTokenRequest body) {
        return shApiTokenRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(body.title());
                    existing.setDescription(body.description());
                    existing.setEnabled(body.enabled());
                    existing.setScope(body.scope() != null ? body.scope() : ShApiTokenScope.READ);
                    existing.setSiteIds(body.siteIds() != null ? new HashSet<>(body.siteIds()) : new HashSet<>());
                    existing.setEnv(body.env() != null ? body.env() : ShApiTokenEnv.PROD);
                    existing.setMayPublish(body.mayPublish());
                    // Token value is never updated
                    shApiTokenRepository.saveAndFlush(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String id) {
        shApiTokenRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
