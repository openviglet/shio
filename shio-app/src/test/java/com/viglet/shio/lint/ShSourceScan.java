/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a source-scanning test reads: code, prose, or both (SH613).
 *
 * <h2>Why a declaration and not a rule</h2>
 *
 * <p>Some of these lints read comments <b>on purpose</b> — {@code ShSilentCatchInventoryTest} looks
 * for exemption markers, {@code ShFolderSiteInvariantTest} keys on {@code ShFolderSite-exempt}, and
 * {@code ShByteStabilityLintTest} waives a line by the comment beside it. Others read them <b>by
 * accident</b>, which is SH206's defect. <b>From outside, the two are indistinguishable</b>: both
 * are a scan over raw file text, and the only difference is an intention nothing records.</p>
 *
 * <p>So the answer is not "strip comments everywhere" — that would break three correct lints — but
 * a statement each scan makes about itself, which {@code ShSourceScanLintTest} then holds to. A
 * {@link Reads#CODE} scan must actually use {@link ShSourceText}; anything else must say why in one
 * sentence. The accidental case becomes visible at the moment it is written, instead of waiting for
 * somebody to describe a rule in the file it guards.</p>
 *
 * <p>{@code SOURCE} retention: the reader is a lint over the test sources, not a runtime. Nothing
 * should be able to branch on this at runtime, because it is a claim about a test's intent rather
 * than a fact about the program.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ShSourceScan {

    /** What this scan reads. */
    Reads value();

    /**
     * Why it reads prose, in one sentence.
     *
     * <p>Required for {@link Reads#PROSE} and {@link Reads#BOTH} and pointless for
     * {@link Reads#CODE}: an accident cannot explain itself, so demanding the explanation is what
     * separates the two shapes. Enforced by {@code ShSourceScanLintTest}.</p>
     */
    String reason() default "";

    /** The three honest answers. */
    enum Reads {

        /**
         * Symbols only. A mention in a javadoc must not be readable as a declaration, which is the
         * SH206 defect — so the scan runs over {@link ShSourceText#code} or
         * {@link ShSourceText#codeLine}.
         */
        CODE,

        /** Comments only: an exemption marker, a rationale pointer, a written reason. */
        PROSE,

        /** A symbol in the code, waived or classified by a marker in the prose beside it. */
        BOTH
    }
}
