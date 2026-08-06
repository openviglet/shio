/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH270 — a {@code catch} that neither records nor rethrows leaves no trace, and the number of them
 * is a decision rather than an accident.
 *
 * <h2>The gap SH269's lint could not see</h2>
 *
 * <p>SH269 put an SH190 frame on every {@code incidentLog.record} inside a {@code catch}, and
 * {@code ShAgentIncidentFramesTest} keeps it that way. Both can only see the recorders that
 * <em>exist</em>. The invisible case is a {@code catch} that records <b>nothing</b> — usually right
 * (a diagnostics write must never turn a webhook timeout into a 500) and occasionally the reason a
 * page came back empty with nobody able to say why.</p>
 *
 * <h2>What this asserts, and what it deliberately does not</h2>
 *
 * <p>Not "every swallowed exception must be recorded" — that would fill the diagnostics window with
 * handled conditions, which is exactly what SH103 refused for 4xx. What it asserts is that the
 * <b>count per package</b> is what this table says. Adding a silent {@code catch} is then a two-line
 * diff that says so, and the reviewer's question — "should this one have been an incident?" — gets
 * asked once, at the moment somebody is looking.</p>
 *
 * <p>Measured when SH270 landed: <b>65</b>, of which the two the task named were the content
 * degradations that render an empty page — an unparseable {@code postTypeLayout} and a post whose
 * {@code data} is not an object. Both record a {@code CONTENT} incident now, so the inventory is
 * <b>63</b> and the drop is the fix. The two that remain in {@code render} are legitimate and worth
 * naming so nobody re-files them: a bad {@code limit=} falls back to the default, and the template
 * validator <em>collects</em> the failure into a report rather than swallowing it.</p>
 *
 * @author Alexandre Oliveira
 */
class ShSilentCatchInventoryTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "com", "viglet", "shio");

    /**
     * Silent {@code catch} blocks per top-level package, measured.
     *
     * <p>Raising a number is allowed and is a deliberate, reviewable act — the same contract
     * {@code token-budgets.properties} has. Raising one without saying why in the commit message is
     * the mechanism defeated.</p>
     */
    private static final Map<String, Integer> DECLARED = Map.of(
            // 51 since SH329: `Declared.withSitesFrom` swallows an unparseable address, because the
            // op that owns it raises the teaching error when it runs — and throwing from the
            // pre-pass would kill the whole dry run before it planned anything, which is the one
            // thing SH168 says a plan must not do.
            // 52 since SH328: `ShSiteWriteService.layoutBindings` reads an unparseable
            // postTypeLayout as "no bindings", which is what the renderer already does with it
            // (and where SH270 records the incident, on the path that actually renders). Repairing
            // it from the write side would replace a value somebody set.
            // 53 since SH389: `ShAgentVerifyService.scriptFindings` catches an unparseable SCRIPTS
            // field and *adds a Finding* for it, which is reporting of the strongest kind this
            // codebase has — a rule with an address and a fix. The lint keys on "records an incident
            // or rethrows" and cannot see a finding, which is the right way round for a population
            // guard to be blind. Counted rather than exempted, so the population stays a diff.
            // 55 since SH460, and both are the same judgement: `ShAgentBuildIdentity` reads the
            // running build's timestamp off the code source at startup, and a code source it cannot
            // walk yields **no** `builtAt` rather than a guessed one. Not recorded, for a reason
            // stronger than "not worth reporting" — the degradation is *published on the wire*, and
            // the client SH460 exists for treats a missing `builtAt` as a stale instance and fails
            // its whole run naming it. An incident would report the same fact to an operator who is
            // not the one asking, at a moment (context refresh) when the diagnostics window is not
            // yet the place anybody reads. Same shape as SH389's finding: reported, in the medium
            // the caller is already reading. Counted rather than exempted, so it stays a diff.
            // 57 since SH516, and the two are the two shapes this list already holds, one of each.
            // `ShAgentVerifyService.onePage` catches a teaching exception from the renderer and
            // *adds a Finding* naming the page that does not render — SH389's shape, reporting of
            // the strongest kind here, and invisible to a lint that keys on incidents. And it
            // catches `ShPageRedirectException` to skip the page, which is SH385's shape one layer
            // along: a `Redirect` post is a working route with no page to read, so the catch IS the
            // handling. Counted rather than exempted, so the population stays a diff.
            "api", 57,
            "tenant", 4,
            "blueprint", 2,
            "exchange", 2,
            // 3 since SH384: a bad `page=` falls back to page one, beside the bad `limit=` that
            // falls back to the default. Deliberately not recorded — that value comes off a URL a
            // *reader* types, so a diagnostics entry per `?page=abc` would fill the window with
            // somebody else's typos, which is the trade SH103 refused for 4xx.
            //
            // 4 since SH385, and this one is not a degradation at all: `ShRenderAPI` catches
            // `ShPageRedirectException` and *answers with a 302*. The catch IS the handling, so there
            // is nothing to record — but the lint keys on "neither records nor rethrows" and cannot
            // tell a swallowed error from control flow, which is the right way round for a population
            // guard to be wrong. Counted rather than exempted, so the population stays a diff.
            // 6 since SH390, both in `{{#image}}` and both the same shape as `limit=`: a `WIDTH` that
            // is not a number means the same as an absent one (no ladder, plain url), and a signer
            // with no configured secret yields an empty `srcset` rather than a failed render — a page
            // that fell over because a transform could not be signed would take a site down for a
            // configuration mistake, and an image at its natural size is the correct degradation.
            // Neither is recorded: an unmeasurable image is not a degradation, and the signer's own
            // startup log already reports an enabled signer with no secret.
            // 7 since SH437, and it is the same kind as the SH385 redirect above rather than a
            // degradation: `ShRenderAPI` catches `UnknownAssetException` and rethrows the *page's*
            // 404, because a path that is neither is far more often a page somebody meant to write.
            // Nothing is lost — both are teaching 4xx problems that reach the caller in full, and
            // SH103 settled that 4xx does not belong in the diagnostics window. The lint keys on
            // "neither records nor rethrows the caught exception" and cannot see that this one
            // rethrows a better one, which is the right way round for a population guard to be wrong.
            // 9 since SH451, which adds *two* on the public delivery route and both are the ones
            // already argued above: `ShSitesAPI` catches `ShPageRedirectException` and answers with
            // a 302 (SH385's row, at a second address) and `UnknownAssetException` to fall back to
            // the page's 404 (SH437's). Two routes making the same two choices is one judgement,
            // not two — which is also why they read as duplicates here and are counted anyway.
            "render", 9,
            "utils", 2,
            "migration", 1,
            // 1 since SH84: `ShTuringDocumentMapper.readData` reads a post whose `data` is not JSON
            // as "no text", and does not record it. The render path already reports that exact post
            // as a CONTENT degradation, which is the branch of SH270's own rule this falls under —
            // recording it a second time would put one broken post in the diagnostics window once
            // per publish, and folding by subject would then hide whichever of the two arrived
            // second. Not indexing its body is also the correct outcome, not a degradation: there is
            // no prose in a malformed payload to index.
            "turing", 1,
            // 1 since SH459, and it is the SH385/SH437 kind rather than a degradation: the
            // authentication entry point asks every RequestMappingHandlerMapping whether any
            // controller claims the URI, so that a path none of them serves answers 404 instead of
            // 401. A mapping that cannot decide (HttpRequestMethodNotSupportedException is the
            // ordinary case - the path IS a controller's and only the verb is wrong) is counted as
            // claiming it, so the catch IS the handling and what it preserves is the refusal that
            // was already going to be sent. There is nothing to record: no caller is worse off than
            // before the check existed, and an incident per wrong-verb probe would fill the window
            // with what SH103 refused for 4xx. Counted rather than exempted, so the population stays
            // a diff.
            "spring", 1);

    private static final Pattern CATCH = Pattern.compile("catch\\s*\\(([^)]*)\\)\\s*\\{");

    private record SilentCatch(String area, String where, String first) {
    }

    private static List<SilentCatch> inventory() {
        List<SilentCatch> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                found.addAll(silentCatchesIn(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static List<SilentCatch> silentCatchesIn(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String relative = SOURCES.relativize(file).toString().replace('\\', '/');
        String area = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : "(root)";

        List<SilentCatch> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = CATCH.matcher(lines.get(i));
            if (!matcher.find()) {
                continue;
            }
            String body = bodyAfter(lines, i);
            boolean records = body.contains("incidentLog") || body.contains(".record(");
            boolean rethrows = Pattern.compile("\\bthrow\\b").matcher(body).find();
            if (!records && !rethrows) {
                found.add(new SilentCatch(area, relative + ":" + (i + 1), firstLineOf(body)));
            }
        }
        return found;
    }

    /**
     * The {@code catch} body, by indentation rather than by brace counting.
     *
     * <p>Deliberately the simpler rule: this measures a <em>population</em>, and a parser that got
     * one nested lambda wrong would move a number nobody could then explain. Indentation is how this
     * codebase is actually written, and the table below is the assertion — not the parser.</p>
     */
    private static String bodyAfter(List<String> lines, int catchLine) {
        String opener = lines.get(catchLine);
        int indent = opener.length() - opener.stripLeading().length();
        List<String> body = new ArrayList<>();
        for (int j = catchLine + 1; j < Math.min(catchLine + 40, lines.size()); j++) {
            String current = lines.get(j);
            if (current.strip().equals("}")
                    && current.length() - current.stripLeading().length() <= indent) {
                break;
            }
            body.add(current);
        }
        return String.join("\n", body);
    }

    private static String firstLineOf(String body) {
        return body.strip().isEmpty() ? "(empty)" : body.strip().split("\n")[0].strip();
    }

    @Test
    @DisplayName("SH270: the number of silent catch blocks is declared, per package")
    void theInventoryIsDeclared() {
        Map<String, Integer> measured = new TreeMap<>();
        inventory().forEach(silent -> measured.merge(silent.area(), 1, Integer::sum));

        assertThat(measured)
                .as("""
                        SH270: a catch that neither records nor rethrows leaves no trace, and SH269's \
                        lint can only see the recorders that exist. This is not "record everything" - \
                        that would fill the diagnostics window with handled conditions, which SH103 \
                        refused for 4xx. It is a population, declared, so that adding one is a diff \
                        that says so and somebody gets asked the question.

                        If you added a silent catch: decide whether the degradation is invisible to \
                        the operator (the content is wrong and the symptom is an empty page - record \
                        a CONTENT incident, as SH270 did for the two render paths) or genuinely not \
                        worth reporting (raise the number here, and say why in the commit message).""")
                .containsExactlyInAnyOrderEntriesOf(new TreeMap<>(DECLARED));
    }

    @Test
    @DisplayName("SH270: the two content degradations that render an empty page are recorded")
    void theTwoContentDegradationsAreRecorded() {
        List<String> renderSilent = inventory().stream()
                .filter(silent -> "render".equals(silent.area()))
                .map(SilentCatch::where)
                .toList();

        assertThat(renderSilent)
                .as("""
                        The two SH270 named are gone from this list because they record now: an \
                        unparseable postTypeLayout (every page of the site then refuses with a \
                        message about the *post-type*, which reads like a missing binding) and a post \
                        whose data is not a JSON object (the page renders with none of its fields). \
                        Both symptoms are silence, which is the one shape nobody investigates.

                        The three that remain are deliberate and are named here so they are not \
                        re-filed: a bad `limit=` falls back to the default, a bad `page=` falls back \
                        to page one (SH384), and the template validator collects the compile failure \
                        into a report.

                        `page=` is the one worth arguing about, and the argument is *where the value \
                        comes from*: a reader types `?page=` into a URL bar, so recording it would \
                        put one diagnostics entry per stranger's typo into a window folded by \
                        subject — which is exactly the trade SH103 refused for 4xx. A bad `limit=` \
                        is a template author's mistake and could be argued the other way; it is \
                        left as it was rather than changed in passing.

                        SH437 added the seventh and SH451 the eighth and ninth, and all three are \
                        control flow rather than degradations: a path with no page is tried as an \
                        asset, and a path that is neither gets the page's 404 because that is the \
                        more useful of the two sentences. Two of them are the public delivery \
                        route making the same two choices this one does, at a second address.""")
                .hasSize(9)
                .allSatisfy(where -> assertThat(where)
                        .matches(".*(ShRenderAPI|ShSitesAPI|ShRenderHelpers|ShTemplateValidator).*"));
    }
}
