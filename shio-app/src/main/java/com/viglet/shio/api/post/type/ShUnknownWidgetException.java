/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a pushed post-type definition (SH16) references a widget name that
 * is not in the installed widget catalog ({@code ShWidget}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ShUnknownWidgetException extends RuntimeException {

    public ShUnknownWidgetException(String widget) {
        super("Unknown widget: '" + widget + "'. It is not in the installed widget catalog.");
    }
}
