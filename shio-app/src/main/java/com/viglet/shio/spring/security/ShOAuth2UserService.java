package com.viglet.shio.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShAppProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Enriches OAuth2 (non-OIDC) users with authorities from the local database.
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PREFERRED_USERNAME = "preferred_username";

    private final ShAuthorityResolver shAuthorityResolver;
    private final ShAppProperties shAppProperties;

    public ShOAuth2UserService(ShAuthorityResolver shAuthorityResolver, ShAppProperties shAppProperties) {
        this.shAuthorityResolver = shAuthorityResolver;
        this.shAppProperties = shAppProperties;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // Client Silos isolation (C051): reject a login whose viglet_client claim
        // doesn't match this instance's dedicated client (no-op if unrestricted).
        ShClientClaimGate.enforce(shAppProperties.getRequiredVigletClient(),
                oAuth2User.getAttribute(ShClientClaimGate.VIGLET_CLIENT_CLAIM));

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        shAuthorityResolver.resolve(oAuth2User.getAttribute(PREFERRED_USERNAME), authorities);

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(),
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName());
    }
}
