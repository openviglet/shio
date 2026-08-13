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

    /** The local {@code admin} account's bootstrap settings (SH706). Never null, so a blank
     * configuration reads the same as an absent one. */
    private Admin admin = new Admin();

    /**
     * SH706 — headless bootstrap for the local {@code admin} account.
     */
    @Setter
    @Getter
    public static class Admin {
        /**
         * The password a fresh instance starts with, so a container or CI deploy is usable without
         * the first-access setup screen. Env: {@code SHIO_ADMIN_PASSWORD}.
         *
         * <p>Applied on startup <b>only while the password is still unset</b> — it never overwrites
         * one already configured, so restarts are a no-op. Minimum 6 characters, the same floor the
         * setup screen enforces; a shorter value is ignored with a warning and the screen is kept.
         * Blank or unset, the default, leaves the interactive setup exactly as it was.</p>
         */
        private String password;
    }
}
