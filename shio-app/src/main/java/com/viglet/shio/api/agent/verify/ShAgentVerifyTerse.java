/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.List;

import com.viglet.shio.api.agent.ShTerse;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;

/**
 * Terse renderings for the Block K payloads (SH78's encoding applied to SH99–SH103).
 *
 * <p>These are the reads an agent makes most often after a write, and a report is the
 * worst possible shape for JSON: every finding repeats seven key names. The terse form
 * states the shape once in the verb and then the values, which is roughly half the
 * tokens — and unlike a listing, a report is read in full by definition, so the saving
 * is never wasted.</p>
 *
 * <p>The {@code fix} travels on its own {@code fix} line rather than as an attribute,
 * because it is prose with spaces in it and the one field the agent must not have to
 * un-quote to read (law P6).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentVerifyTerse {

    private ShAgentVerifyTerse() {
        // utility
    }

    /** A verification report: a {@code report} header, then one {@code finding} + {@code fix}. */
    public static String report(ShAgentReport report) {
        StringBuilder out = new StringBuilder(ShTerse.line("report")
                .word(report.ok() ? "ok" : "failed")
                .attr("checks", String.join(",", report.checks()))
                .attr("checked", report.checked())
                .count("errors", report.errors())
                .count("warnings", report.warnings())
                .flag("truncated", report.truncated())
                // A scoped run's `ok` is not a statement about the instance, so the marker
                // travels on the header line an agent cannot skip (SH143).
                .flag("partial", Boolean.TRUE.equals(report.partial()))
                .attr("scope", report.scope())
                .build());
        for (ShAgentReport.Finding finding : report.findings()) {
            out.append(ShTerse.line("finding")
                    .word(finding.severity())
                    .word(finding.rule())
                    .attr("at", finding.address())
                    .attr("field", finding.field())
                    .attr("msg", finding.message())
                    .build());
            if (finding.fix() != null && !finding.fix().isBlank()) {
                out.append("fix ").append(finding.fix()).append('\n');
            }
        }
        if (report.truncated()) {
            out.append(ShTerse.line("truncated")
                    .word(report.findings().size())
                    .attr("of", report.found())
                    .attr("note", "raise limit or narrow with checks=/site=")
                    .build());
        }
        for (String note : report.notes() == null ? List.<String>of() : report.notes()) {
            out.append("note ").append(note).append('\n');
        }
        return out.toString();
    }

    /** A structural digest: outline, landmarks, links and images, one line each. */
    public static String digest(ShAgentDigest digest) {
        StringBuilder out = new StringBuilder(ShTerse.line("digest")
                .word(digest.digest())
                .attr("source", digest.source())
                .attr("at", digest.address())
                .attr("url", digest.url())
                .attr("status", digest.status())
                .attr("words", digest.words())
                .attr("fields", digest.fields() == null ? null : String.join(",", digest.fields()))
                .build());
        for (ShAgentDigest.Heading heading : digest.headings()) {
            out.append(ShTerse.line("h").word(heading.level()).word(heading.text()).build());
        }
        if (!digest.landmarks().isEmpty()) {
            ShTerse.Line line = ShTerse.line("landmarks");
            digest.landmarks().forEach(line::attr);
            out.append(line.build());
        }
        for (ShAgentDigest.Link link : digest.links()) {
            out.append(ShTerse.line("link")
                    .word(link.href())
                    .flag("internal", link.internal())
                    .attr("text", link.text())
                    .build());
        }
        for (ShAgentDigest.Image image : digest.images()) {
            // `noalt` is a marker word, not alt="": the absence of the attribute is the
            // defect, and a marker is both cheaper and impossible to misread as content.
            out.append(ShTerse.line("img")
                    .word(image.src())
                    .flag("noalt", image.alt() == null)
                    .attr("alt", image.alt())
                    .build());
        }
        // SH299 — only when the page is carrying one. A line of zeroes on every clean digest is
        // tokens spent to say nothing happened, and the absence is the same statement.
        if (digest.markers() != null && digest.markers().any()) {
            out.append(ShTerse.line("markers")
                    .count("missingRegions", digest.markers().missingRegions())
                    .count("unpublishedRegions", digest.markers().unpublishedRegions())
                    .count("missingThemes", digest.markers().missingThemes())
                    .build());
        }
        return out.toString();
    }

    /** Diagnostics: a header line plus one {@code incident} per folded failure. */
    /**
     * SH811 — the editable fields, in the same line vocabulary the digest uses.
     *
     * <p>An `edit` line per field and a `region` line per region, so an MCP client reading a digest
     * gets the authorable half in the shape it is already parsing. The address comes first because
     * it is the argument the next call takes.</p>
     */
    public static String editable(ShAgentEditable editable) {
        StringBuilder out = new StringBuilder(ShTerse.line("editable")
                .attr("at", editable.address())
                .attr("source", editable.source())
                .build());
        for (ShHtmlEditable.Field field : editable.fields()) {
            out.append(ShTerse.line("edit")
                    .word(field.address())
                    .word(field.field())
                    .attr("region", field.region())
                    .build());
        }
        for (String region : editable.regions()) {
            out.append(ShTerse.line("region").word(region).build());
        }
        // SH1039 — the container half, present only when the caller asked for it. A `slot` line per
        // container and a `fits` line per accepted type, so an insert is one call away from a
        // digest: the slot says where and how many, the fits line says what and what it needs.
        for (ShAgentInsertable.Container container
                : editable.containers() == null ? List.<ShAgentInsertable.Container>of()
                        : editable.containers()) {
            out.append(ShTerse.line("slot")
                    .word(container.index())
                    .attr("region", container.region())
                    .count("holds", container.holds())
                    .attr("has", String.join(",", container.sections()))
                    .build());
            for (ShAgentInsertable.Type type : container.accepts()) {
                out.append(ShTerse.line("fits")
                        .word(type.type())
                        .attr("title", type.title())
                        // Absent for a type this instance has: a flag on every row of a working
                        // catalogue is tokens spent to say nothing happened (the `markers` rule
                        // above, one answer along).
                        .flag("unprovisioned", !type.provisioned())
                        .attr("required", String.join(",", required(type)))
                        .build());
            }
            if (container.fix() != null) {
                out.append("fix ").append(container.fix()).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * The fields an insert of this type has to supply (SH1039).
     *
     * <p>Only the required ones in the terse form, and that is the whole trade: the JSON carries
     * every field because a properties panel draws them all, and a caller reading lines is deciding
     * what to place — the required set is what decides whether a placement can be completed, which
     * SH582 is the measured case of (a proposal derived fields, never compared them with the
     * required set, and left a replica failing {@code verify} with its content complete).</p>
     */
    private static List<String> required(ShAgentInsertable.Type type) {
        return type.fields().stream().filter(ShAgentInsertable.Field::required)
                .map(ShAgentInsertable.Field::name).toList();
    }

    public static String diagnostics(ShAgentDiagnostics diagnostics) {
        StringBuilder out = new StringBuilder(ShTerse.line("diagnostics")
                .word(diagnostics.incidents().size())
                .attr("since", diagnostics.since())
                .count("dropped", diagnostics.dropped())
                .build());
        for (ShAgentDiagnostics.Incident incident : diagnostics.incidents()) {
            out.append(ShTerse.line("incident")
                    .word(incident.kind())
                    .attr("subject", incident.subject())
                    .attr("msg", incident.message())
                    .attr("count", incident.count())
                    .attr("last", incident.lastSeen())
                    .build());
            if (incident.fix() != null && !incident.fix().isBlank()) {
                out.append("fix ").append(incident.fix()).append('\n');
            }
        }
        for (String note : notes(diagnostics)) {
            out.append("note ").append(note).append('\n');
        }
        return out.toString();
    }

    private static List<String> notes(ShAgentDiagnostics diagnostics) {
        return diagnostics.notes() == null ? List.of() : diagnostics.notes();
    }
}
