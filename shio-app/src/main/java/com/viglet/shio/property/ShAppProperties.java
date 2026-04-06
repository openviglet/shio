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
    private String keycloakAdminId;
    private String allowedOrigins;
    private String url = "http://localhost:2710";
}
