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

    private final ShAppProperties shAppProperties;

    public ShTenantFilter(ShAppProperties shAppProperties) {
        this.shAppProperties = shAppProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            ShTenantContext.setTenantId(resolveTenant());
            filterChain.doFilter(request, response);
        } finally {
            ShTenantContext.clear();
        }
    }

    private String resolveTenant() {
        if (!shAppProperties.isMultiTenant()) {
            return ShTenantContext.DEFAULT_TENANT;
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
