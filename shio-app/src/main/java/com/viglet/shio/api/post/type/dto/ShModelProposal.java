/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

/**
 * What a model change would cost (SH523) — the model's half of SH379's content proposal.
 *
 * @param postType the type this is about
 * @param exists   whether it exists today; a proposal for a new type risks nothing
 * @param posts    published posts of this type, which is what every count below is out of
 * @param findings what the change would cost, empty when it costs nothing
 */
public record ShModelProposal(String postType, boolean exists, int posts,
                              List<Finding> findings) {

    /**
     * One cost, with the number that makes it a decision rather than a warning.
     *
     * <p>{@code count} and {@code sample} are separate because the two answer different questions:
     * "is this a cleanup or a mistake" is the count, and "which ones" is the sample. Listing every
     * id would make a proposal about a large type unreadable, and printing only a count would make
     * it unactionable — so the sample is bounded and the count never is.</p>
     *
     * @param rule   what kind of cost: {@code required-field-violated},
     *               {@code removed-field-still-read}, {@code declared-field-unread},
     *               {@code stored-key-undeclared}
     * @param field  the field it is about, or {@code null} when the finding is about a set of them
     * @param count  how many, in full
     * @param sample up to ten of them, so the reader can look at one
     * @param detail what it means and what to do, in a sentence (P6)
     */
    public record Finding(String rule, String field, int count, List<String> sample,
                          String detail) {
    }
}
