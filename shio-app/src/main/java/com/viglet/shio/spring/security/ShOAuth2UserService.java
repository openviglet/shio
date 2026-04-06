package com.viglet.shio.spring.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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

    public ShOAuth2UserService(ShAuthorityResolver shAuthorityResolver) {
        this.shAuthorityResolver = shAuthorityResolver;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        shAuthorityResolver.resolve(oAuth2User.getAttribute(PREFERRED_USERNAME), authorities);

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(),
                userRequest.getClientRegistration().getProviderDetails()
                        .getUserInfoEndpoint().getUserNameAttributeName());
    }
}
