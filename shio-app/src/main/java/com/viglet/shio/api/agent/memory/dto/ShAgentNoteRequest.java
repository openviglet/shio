/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.memory.dto;

/**
 * The body of a memory write (SH114): {@code {scope, key, note}}.
 *
 * <p>Deliberately not the read shape. {@code author} and {@code updated} are the
 * server's to assign — a caller that could set them could also make its own
 * inference look like a human's instruction, which is the one thing the
 * attribution field exists to prevent.</p>
 *
 * @param scope site name, or omitted/blank for an instance-wide note
 * @param key   stable slug; re-using one rewrites that note rather than adding a
 *              near-duplicate the next session has to reconcile
 * @param note  the instruction; omit or blank it to delete
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentNoteRequest(String scope, String key, String note) {
}
