package com.viglet.shio.api.tenant;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.tenant.ShTenantContext;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/tenant")
@Tag(name = "Tenant API", description = "Multi-tenant information")
public class ShTenantAPI {

    private final ShAppProperties shAppProperties;

    public ShTenantAPI(ShAppProperties shAppProperties) {
        this.shAppProperties = shAppProperties;
    }

    @GetMapping("/current")
    public Map<String, Object> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantId;
        if (!shAppProperties.isMultiTenant()) {
            tenantId = ShTenantContext.DEFAULT_TENANT;
        } else if (authentication != null && authentication.isAuthenticated()) {
            String resolved = ShTenantContext.getTenantId();
            tenantId = (resolved != null && !resolved.isBlank()) ? resolved : authentication.getName();
        } else {
            tenantId = ShTenantContext.DEFAULT_TENANT;
        }
        return Map.of(
                "tenantId", tenantId,
                "multiTenant", shAppProperties.isMultiTenant()
        );
    }
}
