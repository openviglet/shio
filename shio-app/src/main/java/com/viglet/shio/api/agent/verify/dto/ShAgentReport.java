/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The verification report (SH99 content lint + SH100 route checks).
 *
 * <h2>Why one report shape for several check groups</h2>
 *
 * <p>The question an agent asks is "is my work correct?", not "is my content
 * internally consistent and separately are my routes reachable". A single report with
 * a {@code check} on every finding answers the question that was asked and still lets
 * a caller narrow with {@code checks=}. It also means one exit code, which is the
 * whole point of making this the content-side {@code tsc}.</p>
 *
 * <h2>{@code ok} is the answer; the findings are the reason</h2>
 *
 * <p>{@code ok} is true when no <b>error</b>-severity finding survived — warnings do
 * not fail a build, because a warning here is something that is probably wrong (an
 * empty section, a link that may be a front-end route) rather than something that is
 * definitely broken. A caller that wants warnings to count reads {@code warnings} and
 * decides for itself; the field is right there so the decision needs no second call.</p>
 *
 * <h2>Truncation is never silent</h2>
 *
 * <p>A report is a list of defects, and a shortened list of defects reads exactly like
 * a shorter list of defects — the same hazard SH138 records for deletion plans. So
 * {@code found} counts everything the checks produced while {@code findings} carries
 * what fitted, and {@code truncated} says the difference out loud.</p>
 *
 * @param ok        no error-severity finding: the green light, and the CLI's exit code
 * @param checks    the check groups that actually ran, in wire form
 * @param checked   objects examined (posts + folders), so an empty report is
 *                  distinguishable from an empty instance
 * @param errors    error-severity findings produced (not returned — see {@code truncated})
 * @param warnings  warning-severity findings produced
 * @param found     total findings produced by the checks
 * @param truncated {@code findings} is shorter than {@code found}: raise {@code limit}
 *                  or narrow with {@code checks}/{@code site}
 * @param findings  the findings themselves, ordered deterministically (P5)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentReport(boolean ok, List<String> checks, int checked, int errors, int warnings,
                            int found, boolean truncated, List<Finding> findings) {

    /** Error: broken now. A published page a visitor can reach is wrong or unreachable. */
    public static final String ERROR = "error";

    /** Warning: probably wrong, and not provably so from inside the CMS. */
    public static final String WARNING = "warning";

    /**
     * One defect.
     *
     * <p>Carries a {@code fix} for the same reason a teaching error does (law P6): a
     * finding an agent cannot act on has spent tokens to produce a feeling. The
     * {@code address} is the SH79 address of the offending object, so the next call —
     * a read, a write, a publish — needs no id lookup.</p>
     *
     * @param check    the group this rule belongs to ({@code content}, {@code routes},
     *                 {@code diagnostics})
     * @param rule     stable slug, e.g. {@code required-field-empty}. Stable because
     *                 callers suppress and count by it
     * @param severity {@link #ERROR} or {@link #WARNING}
     * @param address  the object at fault, as an SH79 address; absent for an
     *                 instance-level finding
     * @param field    the post-type field at fault, when the rule is per-field
     * @param message  what is wrong, in one sentence
     * @param fix      what to do about it, naming the call or the file
     */
    @JsonInclude(Include.NON_NULL)
    public record Finding(String check, String rule, String severity, String address,
                          String field, String message, String fix) {

        public static Finding error(String check, String rule, String address, String field,
                                    String message, String fix) {
            return new Finding(check, rule, ERROR, address, field, message, fix);
        }

        public static Finding warning(String check, String rule, String address, String field,
                                      String message, String fix) {
            return new Finding(check, rule, WARNING, address, field, message, fix);
        }

        public boolean isError() {
            return ERROR.equals(severity);
        }
    }
}
