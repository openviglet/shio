package com.viglet.shio.spring.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Client Silos isolation gate (Viglet Cloud C051). Enforces, at login time, that
 * the authenticated user's {@code viglet_client} claim matches the client this
 * instance is dedicated to (see {@code shio.required-viglet-client}).
 *
 * <p>Closes the browser (OAuth2 authorization-code) hole the nginx bearer-token
 * gate cannot cover: browser navigation carries an app session cookie, not a JWT,
 * so a non-member could otherwise complete an interactive login at another
 * client's dedicated host. No-op when the required client is blank/unset.
 *
 * @author Alexandre Oliveira
 */
public final class ShClientClaimGate {

    public static final String VIGLET_CLIENT_CLAIM = "viglet_client";

    private ShClientClaimGate() {
    }

    public static void enforce(String requiredClient, Object actualClaim) {
        if (requiredClient == null || requiredClient.isBlank()) {
            return;
        }
        String actual = actualClaim == null ? null : actualClaim.toString();
        if (!requiredClient.equals(actual)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            OAuth2ErrorCodes.ACCESS_DENIED,
                            "This instance is restricted to the '" + requiredClient + "' client.",
                            null),
                    "viglet_client claim '" + actual + "' does not match required '" + requiredClient + "'");
        }
    }
}
