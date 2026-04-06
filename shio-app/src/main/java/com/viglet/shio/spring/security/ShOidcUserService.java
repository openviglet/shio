package com.viglet.shio.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

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

    public ShOidcUserService(ShAuthorityResolver shAuthorityResolver) {
        this.shAuthorityResolver = shAuthorityResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        shAuthorityResolver.resolve(oidcUser.getAttribute(PREFERRED_USERNAME), authorities);

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
