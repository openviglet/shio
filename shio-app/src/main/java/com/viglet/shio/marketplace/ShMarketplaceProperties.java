/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Where this instance may read a package catalogue from (SH646).
 *
 * <h2>Why this does not overturn SH96</h2>
 *
 * <p>{@link com.viglet.shio.blueprint.ShBlueprintProperties} refused URL fetching in as many
 * words, because it *"moves the decision from an operator to a network"*. That objection is
 * still correct and this does not answer it by disagreeing — it answers it by keeping the
 * operator in the loop at exactly the same point. <b>The URL is theirs to name, and turning
 * the catalogue off is one property.</b> Naming a URL is the same act as naming a directory:
 * whoever can do it can already put a jar on the classpath, so it grants no authority that
 * did not exist.</p>
 *
 * <p><b>On by default since SH1257, by the owner's decision.</b> A fresh instance reads this
 * project's published catalogue, so someone evaluating Shio finds the showcase in the
 * marketplace on first boot. The cost is an outbound request to a Viglet host whenever the
 * marketplace is read, and an air-gapped or multi-tenant operator sets
 * {@code shio.marketplace.enabled=false}. The rejected alternative (off, with only the showcase
 * listed as a known entry) was the narrower widening and more code.</p>
 *
 * <p>And what a catalogue produces is not trust, it is a <em>list</em>. Nothing here changes
 * what an installed package may do — that is SH643's bounds — nor how it is validated, which
 * is the registry's job and identical for a first-party package and a stranger's. The
 * catalogue is a distribution channel for something the trust model already accepts.</p>
 *
 * <h2>The failure modes, chosen deliberately</h2>
 *
 * <p><b>Disabled is a teaching 404</b>, not a bare one: a caller who asks for a feature an
 * operator has not enabled should be told which property turns it on (P6), because the
 * alternative is indistinguishable from the endpoint not existing.</p>
 *
 * <p><b>An unreachable catalogue does not empty the listing.</b> That is SH217's rule read
 * one level up: a package the operator did not write must not be able to take the catalogue
 * down, so neither may a URL that is slow, moved or wrong. The built-in packages are still
 * listed and the failure is reported beside them.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "shio.marketplace")
public class ShMarketplaceProperties {

    /** The catalogue this project publishes, read unless an operator names another or turns it off. */
    public static final String DEFAULT_URL = "https://openviglet.github.io/shio/marketplace/marketplace.json";

    /**
     * Whether this instance reads a remote catalogue at all.
     *
     * <p>True by default (SH1257). With it off, the listing still answers — with the packages
     * that shipped with the instance — so the console has something true to show rather than
     * an error, and nothing leaves the JVM.</p>
     */
    private boolean enabled = true;

    /** The catalogue JSON. Only read when {@link #enabled}. */
    private String url = DEFAULT_URL;

    /** How long to wait for the catalogue before falling back to the built-in packages. */
    private long timeoutMillis = 5_000;

    /** True when an operator has enabled the feature and left a URL to read. */
    public boolean hasRemote() {
        return enabled && url != null && !url.isBlank();
    }
}
