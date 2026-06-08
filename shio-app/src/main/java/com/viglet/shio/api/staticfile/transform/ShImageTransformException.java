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
 * Thrown when a delivery-path image-transform query string is malformed (SH39):
 * a non-numeric or non-positive {@code w}/{@code h}, an unsupported {@code format},
 * or an unknown {@code crop} mode.
 *
 * <p>{@code @ResponseStatus(BAD_REQUEST)} so it maps to 400 whether it surfaces
 * from the {@code /api/v2/staticfile/{id}} controller or from the
 * {@code /file_source/**} resource transformer (both flow through Spring's
 * {@code ResponseStatusExceptionResolver}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShImageTransformException extends RuntimeException {

    public ShImageTransformException(String message) {
        super(message);
    }
}
