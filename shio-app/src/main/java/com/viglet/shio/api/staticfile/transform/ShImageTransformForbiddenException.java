/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a delivery-path image-transform request is forbidden by the SH43
 * signed-URL gate: signing is enabled
 * ({@code shio.image-transform.signing.enabled=true}) but the request carries no
 * {@code sig}, or an invalid one.
 *
 * <p>Distinct from {@link ShImageTransformException} (400, a <em>malformed or
 * out-of-policy</em> request) — this is 403 ({@code @ResponseStatus(FORBIDDEN)}), an
 * <em>unauthorised</em> one. Both surface identically from the
 * {@code /api/v2/staticfile/{id}} controller and the {@code /file_source/**} resource
 * transformer via Spring's {@code ResponseStatusExceptionResolver}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ShImageTransformForbiddenException extends RuntimeException {

    public ShImageTransformForbiddenException(String message) {
        super(message);
    }
}
