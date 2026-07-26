/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

/**
 * One entry of the agent manifest's endpoint index (SH76).
 *
 * <p>Every field exists to remove a round trip: {@code purpose} so the caller
 * doesn't fetch it to find out what it does, {@code auth} so it doesn't discover
 * the credential by getting a 401, and {@code example} — a complete, copy-able
 * call — so it doesn't guess the parameter names.</p>
 *
 * @param method  HTTP method
 * @param path    path template, with {@code {placeholders}}
 * @param purpose one line: what it returns / does
 * @param auth    the credential required — {@code token:READ}, {@code token:WRITE}
 *                or {@code console} (HTTP Basic / session)
 * @param example a complete example call, ready to run against this instance
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentEndpoint(
        String method,
        String path,
        String purpose,
        String auth,
        String example) {
}
