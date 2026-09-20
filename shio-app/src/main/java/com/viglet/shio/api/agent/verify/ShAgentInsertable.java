/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.agent.verify;

import java.util.List;

/**
 * SH1039 — what may be inserted into a page, and where.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>SH1038 made a section a selectable object a curator can move, duplicate and remove, and an
 * editor that can do all three still cannot <em>add</em> one: {@code /agent/editable} answered the
 * fields drawn and the regions declared, with no insertable set, no per-container allow-list and no
 * catalogue. The shared section types reached the console as post-type names in a picker — a list
 * of names, never something to place — which left the composer half a feature.</p>
 *
 * <h2>The catalogue is the template's, and the template's alone</h2>
 *
 * <p>Only the template knows what a container accepts, which is the same argument that makes
 * {@code sh-field} the author's job rather than the engine's (SH185): a region renders against the
 * page's model and nothing in the rendered HTML records what the author meant a slot to hold. So
 * the allow-list is {@code sh-accepts} on the element composing the sections, and an <b>absent</b>
 * declaration means "nothing is known to fit here", carrying the line that would fix it rather than
 * being read as "anything". Reading it as "every section type this instance has" would need a
 * server-side list of which post-types are sections, and {@code ShVocabularyPostTypes} is explicit
 * that a third hand-written copy of the vocabulary is the drift SH245 bans.</p>
 *
 * <h2>It rides on a read that already exists</h2>
 *
 * <p>{@code include=insertable}, not a route of its own. The catalogue is per page and per
 * container, so the caller already holds the address {@code /agent/editable} takes; a second
 * endpoint would be a second resolve of the same page for the half of the answer the first one left
 * out (P2), and one more GET root for {@code ShAgentReadReachabilityLintTest} to classify. It is
 * opt-in because it is the expensive half: the fields of every accepted type are what a properties
 * panel needs and what a caller asking "which fields can I edit" did not ask for (P3).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentInsertable {

    private ShAgentInsertable() {
    }

    /**
     * One container, with the catalogue resolved for it.
     *
     * @param index    its position among the page's containers, document order (SH1038's handle)
     * @param region   the region it renders through, or {@code null} where the layout composes it
     * @param holds    how many sections it holds now
     * @param sections the types in it, in order
     * @param accepts  the catalogue: one entry per declared type, resolved against this instance
     * @param fix      what to do when the container declares nothing, else {@code null} — P6 in an
     *                 ordinary 200, because an empty list is the shape of both "nothing fits here"
     *                 and "nobody said", and those need different actions
     */
    public record Container(int index, String region, int holds, List<String> sections,
            List<Type> accepts, String fix) {
    }

    /**
     * One insertable type, as much of it as placing one needs.
     *
     * <p>Trimmed from {@code ShPostTypeDef} rather than returned whole: the full definition carries
     * widget settings, constraints, index hints and the Turing mapping, none of which decides what
     * to place or what the properties panel draws. P3 is a budget, and this rides on a read that a
     * caller may ask for on every page of a site.</p>
     *
     * @param type        the post-type name a write names
     * @param title       its own title, which is what a catalogue shows a person
     * @param description what it is for, from the type
     * @param provisioned whether this instance actually has the type; {@code false} is a real
     *                    answer and not an omission — the template names it, so silently dropping
     *                    it would report a shorter catalogue than the page declares (SH169)
     * @param fields      the fields a panel would draw, in declaration order
     */
    public record Type(String type, String title, String description, boolean provisioned,
            List<Field> fields) {
    }

    /**
     * One field of an insertable type.
     *
     * @param required which is the one flag that changes what an insert has to supply — SH582 was
     *                 a proposal deriving fields and never comparing them with the required set,
     *                 and the replica failed {@code verify} with its content complete
     */
    public record Field(String name, String label, String widget, boolean required) {
    }
}
