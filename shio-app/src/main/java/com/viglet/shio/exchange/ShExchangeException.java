/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange;

/**
 * Unchecked failure while reading or writing a content exchange package (SH68):
 * a malformed manifest, an I/O error, or a corrupt / unsafe zip.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class ShExchangeException extends RuntimeException {

    public ShExchangeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ShExchangeException(String message) {
        super(message);
    }
}
