package com.viglet.shio.api.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.ShAPIBean;
import com.viglet.shio.property.ShAppProperties;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/discovery")
public class ShDiscoveryAPI {
    private final ShAPIBean shAPIBean;
    private final ShAppProperties shAppProperties;

    public ShDiscoveryAPI(ShAPIBean shAPIBean, ShAppProperties shAppProperties) {
        this.shAPIBean = shAPIBean;
        this.shAppProperties = shAppProperties;
    }

    @GetMapping
    public ShAPIBean info() {
        shAPIBean.setProduct("Viglet Shio");
        shAPIBean.setKeycloak(shAppProperties.isKeycloak());
        shAPIBean.setMultiTenant(shAppProperties.isMultiTenant());
        return shAPIBean;
    }
}
