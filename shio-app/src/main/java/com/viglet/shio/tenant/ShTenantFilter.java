package com.viglet.shio.tenant;

import com.viglet.shio.property.ShAppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * Request attribute carrying the tenant of an authenticated CDA token (SH22
     * Part D). {@code ShApiTokenFilter} runs earlier in the chain and sets it; this
     * filter prefers it over the principal-derived tenant, so token delivery is
     * scoped to the content owner's tenant rather than the token owner's username.
     */
    public static final String TENANT_REQUEST_ATTR = "sh.tenantId";

    private final ShAppProperties shAppProperties;

    public ShTenantFilter(ShAppProperties shAppProperties) {
        this.shAppProperties = shAppProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            ShTenantContext.setTenantId(resolveTenant(request));
            filterChain.doFilter(request, response);
        } finally {
            ShTenantContext.clear();
        }
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
}
