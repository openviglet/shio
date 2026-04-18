package com.viglet.shio.tenant;

import com.viglet.shio.property.ShAppProperties;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShTenantResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    private final ShAppProperties shAppProperties;

    public ShTenantResolver(ShAppProperties shAppProperties) {
        this.shAppProperties = shAppProperties;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = ShTenantContext.getTenantId();
        if (tenant != null && !tenant.isBlank()) {
            return tenant;
        }
        return shAppProperties.isMultiTenant() ? ShTenantContext.DEFAULT_TENANT : ShTenantContext.DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.tenant_identifier_resolver", this);
    }
}
