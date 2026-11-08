/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code shio.sites.*} — public delivery's settings (SH1354).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.sites")
public class ShSitesProperties {

    /**
     * The service domain a site is served under as {@code <site-furl>.<host-domain>}. Empty turns
     * site hosts off, which is the default. The wildcard DNS, certificate and proxy are the operator's.
     */
    private String hostDomain = "";

    /** The scheme a site host's canonical URL is written with. */
    private String hostScheme = "https";
}
