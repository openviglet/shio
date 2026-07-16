package com.viglet.shio.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio")
public class ShAppProperties {
    private boolean keycloak;
    private boolean multiTenant;
    private String keycloakAdminId;
    private String allowedOrigins;
    private String url = "http://localhost:2710";
    /**
     * Client Silos isolation (Viglet Cloud C051). When set (e.g. {@code vilt}),
     * this instance is a dedicated per-client silo and only admits logins/tokens
     * whose {@code viglet_client} claim equals this value; any other client (or no
     * claim) is rejected. Blank/unset (default) = shared instance, no restriction.
     * Env: {@code SHIO_REQUIRED_VIGLET_CLIENT}.
     */
    private String requiredVigletClient;
}
