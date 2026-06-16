package com.viglet.shio.tenant;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ShTenantFilter extends OncePerRequestFilter {

    private static final String WORKSPACE_ID_CLAIM = "workspace_id";

    /** Authority that bypasses tenant lifecycle gates (the platform admin). */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * Request attribute carrying the tenant of an authenticated CDA token (SH22
     * Part D). {@code ShApiTokenFilter} runs earlier in the chain and sets it; this
     * filter prefers it over the principal-derived tenant, so token delivery is
     * scoped to the content owner's tenant rather than the token owner's username.
     * A token-scoped request is exempt from the membership gate — the token itself
     * authorizes delivery to its tenant.
     */
    public static final String TENANT_REQUEST_ATTR = "sh.tenantId";

    /** Session attribute set by {@code POST /tenant/{name}/switch} and {@code /impersonate} (SH22/C). */
    public static final String TENANT_SESSION_ATTR = "SH_TENANT";

    private final ShAppProperties shAppProperties;
    private final ShTenantRepository shTenantRepository;
    private final ShTenantMembershipService shTenantMembershipService;

    public ShTenantFilter(ShAppProperties shAppProperties, ShTenantRepository shTenantRepository,
                          ShTenantMembershipService shTenantMembershipService) {
        this.shAppProperties = shAppProperties;
        this.shTenantRepository = shTenantRepository;
        this.shTenantMembershipService = shTenantMembershipService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = resolveTenant(request);
        ShTenantContext.setTenantId(tenantId);
        try {
            if (shAppProperties.isMultiTenant()) {
                // SH22/B — a suspended tenant blocks its members (non-admins) with 403.
                if (isSuspendedForCurrentUser(tenantId)) {
                    reject(response, "tenant is suspended");
                    return;
                }
                // SH22/C — a non-default tenant requires an ACTIVE membership (hard 403),
                // unless authorized by a CDA token (token-scoped delivery) or an admin.
                if (!isTokenScoped(request) && isMembershipDenied(tenantId)) {
                    reject(response, "no active membership in tenant");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            ShTenantContext.clear();
        }
    }

    /**
     * Whether the resolved tenant is suspended and the current authenticated
     * principal is a non-admin member (admins still reach a suspended tenant to
     * manage it; the reserved default tenant is never suspended).
     */
    private boolean isSuspendedForCurrentUser(String tenantId) {
        if (tenantId == null || ShTenantContext.DEFAULT_TENANT.equals(tenantId)) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isAdmin(authentication)) {
            return false;
        }
        return shTenantRepository.findByName(tenantId).map(ShTenant::isSuspended).orElse(false);
    }

    /**
     * Whether the authenticated, non-admin principal lacks an ACTIVE membership in
     * the resolved non-default tenant. Unauthenticated requests are left to Spring
     * Security; the default {@code shio} tenant and admins always pass.
     */
    private boolean isMembershipDenied(String tenantId) {
        if (tenantId == null || ShTenantContext.DEFAULT_TENANT.equals(tenantId)) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isAdmin(authentication)) {
            return false;
        }
        return !shTenantMembershipService.isActiveMember(tenantId, authentication.getName());
    }

    private static boolean isTokenScoped(HttpServletRequest request) {
        return request.getAttribute(TENANT_REQUEST_ATTR) instanceof String t && !t.isBlank();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Forbidden: " + reason + "\"}");
    }

    private String resolveTenant(HttpServletRequest request) {
        if (!shAppProperties.isMultiTenant()) {
            return ShTenantContext.DEFAULT_TENANT;
        }
        // A CDA token resolved its own tenant in ShApiTokenFilter (SH22 Part D).
        Object tokenTenant = request.getAttribute(TENANT_REQUEST_ATTR);
        if (tokenTenant instanceof String tenant && !tenant.isBlank()) {
            return tenant;
        }
        // An explicit tenant switch (SH22/C) is held on the session.
        String sessionTenant = sessionTenant(request);
        if (sessionTenant != null) {
            return sessionTenant;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ShTenantContext.DEFAULT_TENANT;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            Object workspace = oidcUser.getClaims().get(WORKSPACE_ID_CLAIM);
            if (workspace != null) {
                return workspace.toString();
            }
        }
        if (principal instanceof OAuth2User oAuth2User) {
            Object workspace = oAuth2User.getAttributes().get(WORKSPACE_ID_CLAIM);
            if (workspace != null) {
                return workspace.toString();
            }
        }
        String name = authentication.getName();
        return (name == null || name.isBlank()) ? ShTenantContext.DEFAULT_TENANT : name;
    }

    private static String sessionTenant(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(TENANT_SESSION_ATTR);
        return value instanceof String tenant && !tenant.isBlank() ? tenant : null;
    }
}
