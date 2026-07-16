package com.viglet.shio.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShAppProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches OIDC users (Keycloak with scope=openid) with authorities
 * from the local database.
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShOidcUserService extends OidcUserService {

    private static final String PREFERRED_USERNAME = "preferred_username";

    private final ShAuthorityResolver shAuthorityResolver;
    private final ShAppProperties shAppProperties;

    public ShOidcUserService(ShAuthorityResolver shAuthorityResolver, ShAppProperties shAppProperties) {
        this.shAuthorityResolver = shAuthorityResolver;
        this.shAppProperties = shAppProperties;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        // Client Silos isolation (C051): reject a login whose viglet_client claim
        // doesn't match this instance's dedicated client (no-op if unrestricted).
        ShClientClaimGate.enforce(shAppProperties.getRequiredVigletClient(),
                oidcUser.getClaims().get(ShClientClaimGate.VIGLET_CLIENT_CLAIM));

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        shAuthorityResolver.resolve(oidcUser.getAttribute(PREFERRED_USERNAME), authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
