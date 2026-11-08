/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.property;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code shio.device-auth.*} — signing a native app in through the instance's own login (SH1360).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.device-auth")
public class ShDeviceAuthProperties {

    /**
     * The redirect URIs an app may be sent back to, compared exactly. Empty, the default, means no
     * app can use the browser sign-in: an authorize request that names any redirect is refused rather
     * than sent somewhere nobody configured.
     */
    private List<String> redirectUris = new ArrayList<>();

    /** How long a one-time code is good for after the sign-in that produced it. */
    private Duration codeTtl = Duration.ofMinutes(2);
}
