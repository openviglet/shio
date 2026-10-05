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
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;

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
@ShSourceScan(value = ShSourceScan.Reads.BOTH,
        reason = "The population is catch blocks in code; what keeps one out of the count is the comment"
                + " beside it, so stripping either half would make the inventory report the wrong number.")
class ShSilentCatchInventoryTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "com", "viglet", "shio");

    /**
     * Silent {@code catch} blocks per top-level package, measured.
     *
     * <p>Raising a number is allowed and is a deliberate, reviewable act — the same contract
     * {@code token-budgets.properties} has. Raising one without saying why in the commit message is
     * the mechanism defeated.</p>
     */
    private static final Map<String, Integer> DECLARED = Map.ofEntries(
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
            //
            // 58 since SH523: `ShModelProposalService.dataOf` treats an unparseable `data` column as
            // an empty object. Deliberately silent, and the third shape this list already knows —
            // the degradation is not invisible, it is *already reported by somebody else*. Malformed
            // `data` is `verify`'s own finding with its own address and fix, and recording a second
            // incident for one defect is two reports that can disagree, which is the argument this
            // codebase makes at every fork of this kind. What the catch buys is that the proposal
            // stays readable: a post whose data will not parse would otherwise count as violating
            // every required field, and drown the finding somebody actually asked for.
            //
            // 59 since SH706: `ShSetupAPI.setup` catches the `IllegalArgumentException`
            // `ShAdminPasswordService` throws for a password under the minimum, and answers 400 with
            // its message. The fourth shape, and the one this project has already ruled on — it is a
            // **4xx**, and SH103 refused recording those as incidents precisely so a handled
            // condition does not fill the diagnostics window. The catch is not silent to the person
            // it concerns: P6 says errors are instructions, and the instruction is in the response
            // body the caller is already reading. What it must not become is a `catch` that swallows
            // the message, which is why it is counted here rather than exempted.
            //
            // 60 since SH45: `ShFileStorageService.bytesOnDiskFor` cannot size one file while
            // recomputing a tenant's storage, logs it at WARN, and counts the rest as zero. A
            // reconcile is a CORRECTION run on request, and a partial correction beats refusing to
            // answer — the alternative is that one unreadable file leaves the meter at whatever it
            // had drifted to. Not an incident, for SH103's reason one surface over: the operator
            // asked for this and is reading its result, so the finding has a reader already. It
            // errs low, which is the safe direction for a meter that gates writes.
            // 63 since SH751, all three in `ShFileStorageService` and all three the same rule:
            // storage succeeding while accounting fails must not be reported to the caller as a
            // failed upload. Two guard the call that moves SH45's meter (`recordDelta`, and the
            // by-name zeroing in `deleteTenant`); the third is `sizeOf`, where a file that cannot
            // be sized contributes nothing to a delta instead of aborting the write.
            //
            // The degradation is real and it is exactly what `reconcile` exists for — the bytes
            // are on disk either way, and the recount is one subtree away. Not an incident, for
            // SH103's reason: nothing the tenant asked for failed, and a diagnostics window that
            // fills with accounting notes is one nobody reads. Two of the three log at WARN
            // naming SH751; `sizeOf` does not, because it is asked about files that are routinely
            // absent (the "before" of a first write) and that is not a condition.
            //
            // 65 since SH967, both in `ShEventStreamHub.pass` and both ending the stream rather than
            // swallowing it. An IOException is the client closing its tab between two passes, which
            // is how every stream ends. A RuntimeException is logged at WARN naming SH967 and the
            // stream is closed, so the client's reconnect is the retry. Not an incident, and for a
            // reason particular to this surface: the incident log is what the stream carries (SH144),
            // so a pass failing because the database is down would be delivered to every other open
            // stream as news, once per pass, by the mechanism that is failing.
            //
            // 66 since SH987, in `ShPostRevisionService.parse`: a stored revision whose JSON will
            // not parse is handed to the caller as a row with no body rather than taking the read
            // down with a 500. A revision is a record of what a page said, and a record that cannot
            // be read is still a record — the number, the date and the actor are the part a caller
            // is reading the list for. Not an incident, for SH103's reason: the only way to reach
            // this is a row written by an older or broken writer, and there is nothing an operator
            // could act on that the row itself does not already say.
            //
            // 67 since SH989, in `ShChangedFields.parse`, and it is the same judgement one table over:
            // a stored post document or a `changed_fields` value that will not parse yields no field
            // names. On the write path the alternative is an audit row refusing the publish it
            // describes, which is SH544's defect; on the feed it is one bad row taking down every
            // `include=fields` read. Both values are written by this codebase's own serializer, so the
            // only way here is an older or broken writer, and the row's action and date still stand.
            // 68 since SH999, in `ShAgentRunService.introduce`: the row that records what an MCP
            // client called itself. Not an incident, and this is the case the message below calls
            // "genuinely not worth reporting" rather than the invisible one. A run whose name could
            // not be stored is a run with no name, which is a state the product already has for
            // every session the REST surface was handed rather than minted and for every run older
            // than the table — both readers report the id instead, by design. The alternative is
            // worse in the direction that matters: `initialize` is the first call of a session, so
            // propagating would turn "your agent has no label in the Activity page" into "your
            // agent cannot start".
            // Back to 68 with SH1001: `approve` and `revert` each carried one of these, and both
            // became the one in `decide` when the two whole-run verbs became that call over every
            // change. The catch itself is unchanged in kind — a post that could not be acted on is
            // a `skipped` row with its reason, beside the ones that worked — and it is now written
            // once instead of twice, which is the same reason the verbs were folded.
            //
            // 69 since SH1000, in `ShReviewService.appearance`: a page that will not render is not
            // a reason to fail the read a reviewer opened. It is the rule `ShAgentProofService`
            // already follows one surface over, and here the absence is reported rather than
            // swallowed — `checked` is the denominator, so a page that could not be compared makes
            // the number smaller instead of making an empty `moved` look like a clean run.
            // SH1010 raised this from 68 to 71, all three in `ShFieldRules` and all three the same
            // decision: a constraint the server cannot read back is a defect in the POST-TYPE,
            // not in the content. An unparseable block, a pattern that does not compile and a
            // value that is not a number each read as "no rule" rather than as a failing one —
            // because refusing every publish of every post of that type puts the fix furthest
            // from the one person who can make it, and the post-type editor is where it belongs.
            // (The third is reported: the caller turns it into a `not-a-number` finding.)
            // And 71 to 72 for the second part: `ShFieldUniqueness.valueOf` cannot compare a
            // sibling whose data will not parse, and calling that a collision would refuse this
            // publish because of a defect in a different post. Verify reports the unparseable
            // row where it belongs — on that row.
            // SH1018 raises this 72 to 73, and it is the case the lint's own message calls
            // "not worth reporting" for a specific reason rather than a general one:
            // `ShPostTypeCheckService` cannot judge a row whose document will not parse, and
            // it says so IN THE REPORT — `unreadable` is a field of the answer. Recording an
            // incident as well would fire one every time somebody runs a read-only check,
            // over a defect the render path already records where it belongs. The lint keys
            // on "records or rethrows" and cannot see a count in a return value.
            // SH1018 raises it again, 73 to 74, for the migration's twin of the same skip:
            // a row whose document will not parse is the one row a migration must never
            // rewrite, and `check` already counts it as unreadable on the same content.
            // Recording here would report the same defect twice for one model change.
            // SH1029 LOWERS it, 74 to 72, which is the direction this number rarely moves: the
            // media listing parsed each asset's `data` once per key through five typed readers,
            // and four of them carried their own catch around the same parse. One `Data` value
            // parses once and every key is read off that tree, so the readers had no callers and
            // were deleted rather than kept — a silent catch nobody calls is a number the next
            // task has to explain.
            // SH1035 RAISES it, 72 to 80, and all eight are ShAssetRenditionStore keeping a replace
            // working when the disk will not cooperate. The judgement is the same one every time:
            // the curator asked for new bytes, and failing that write because the SAFETY NET could
            // not be written would make replace less reliable than the delete-and-re-upload it
            // exists to remove. Each logs through ShIoFailure.describe (SH756), and the listing is
            // the observable: a rendition the capture could not write is one the dialog does not
            // offer, so a revert is never promised over bytes that are not there.
            // SH1055 RAISES it, 80 to 81, for the one catch the accessibility pass needs: a page
            // that will not render is skipped there because `page` renders the same pages and has
            // already reported it. The defect is in the report — under the rule that owns it — and
            // a second finding under an a11y rule name would make one broken template read as two
            // defects. This is the narrowest form of the judgement above: not "not worth
            // reporting", but "reported by the check whose subject it is".
            // SH1084 — 82: ShReviewService.approveOne catches a gate refusal and returns it as the
            // post's `skipped` row, which the curator reads in the approval's answer. It is reported
            // to the person who can act on it, just not as the whole call's failure.
            // SH1112 — 83: ShConsoleCost answers a console it could not read as `unavailable` with the
            // reason, in the manifest section the caller asked for. Reported to the one reader who
            // asked, like 82's skipped row, and a build that cannot be read is not an incident.
            // SH1218 — 87: four catches around a site's home picture, all one judgement: the picture
            // is a convenience a grid card shows, and every failure has a visible fallback that is
            // already the answer. A meta file that will not read, a tenant directory that will not
            // list, or a delete that cannot finish leaves the card on the site's initials, which is
            // what "no picture" looks like; the last two log at WARN with the SH1218 tag. A home that
            // will not render is answered as "no published home" in `status`, the field a caller reads.
            Map.entry("api", 87),
            // 9 since SH45's API-call meter, and all five are the same judgement: metering is an
            // accounting side-effect of a call that has already been served, so nothing it fails at
            // may become the caller's problem or the operator's incident.
            //
            // `ShTenantApiCallFilter` catches around `meter.record` — letting a meter turn a 200
            // into a 500 would trade the product for the invoice. `ShTenantApiCallMeter.flush` and
            // `flushOnShutdown` catch a bucket that will not write and put the increments BACK, so
            // the loss is a minute of latency rather than a minute of calls; that is a retry, not a
            // swallow, and the lint cannot see the difference. `persist` catches the unique-key
            // violation that means another node inserted the same bucket first, and re-reads — the
            // catch IS the concurrency protocol, the third shape the `marketplace` entry below
            // describes. `ShBillingExportService` catches one exporter so the others still receive
            // the period, because one billing integration being down is not a reason for the rest
            // to lose a day.
            //
            // Not incidents, for SH103's reason: none of them is visible to the tenant as a broken
            // request, and a diagnostics window that fills with a flush retrying is a window that
            // gets ignored. Four log at WARN with the SH45 tag, which is where an operator
            // debugging a meter that reads low will already be looking; `persist` deliberately does
            // not, because losing the insert race is the normal path on more than one node and a
            // line per concurrent flush would be noise about a mechanism working.
            // SH1218 — 10: the teardown's site-picture purge is caught like the two byte purges beside
            // it, for their reason: removing a few leftover images must not abort deleting the tenant.
            Map.entry("tenant", 10),
            // 4 since SH217, and the two are different judgements filed under one number.
            //
            // The first is a catch that DOES record — the registry refuses one local package and
            // reports it as a BLUEPRINT incident — but it records through a helper, and this lint
            // keys on the text of the catch block. That is the same blindness the `api` and
            // `render` entries above are counted for, and the same answer: counted rather than
            // exempted, so the population stays a diff.
            //
            // The second is genuinely silent, and deliberately: `packageName` falls back to the
            // resource's description when the manifest's URI will not resolve. It runs while
            // building a report ABOUT a broken package, so an incident there would be a failure to
            // describe a failure — and the fallback still names the package well enough for an
            // operator to find it, which is the whole job.
            Map.entry("blueprint", 4),
            Map.entry("exchange", 2),
            // 1 since SH646, and it is the third shape this list has met: the catch is neither a
            // swallow nor control flow, it is a *conversion into the answer*. `ShMarketplaceCatalog`
            // catches an unreachable or malformed catalogue and returns it as
            // `source.state=unreachable` with the reason, so nothing is lost — the caller is told,
            // in the same payload, in a field that exists for it. An incident on top would report
            // to the operator what the console is already showing them.
            //
            // It is counted rather than exempted for the reason the `render` entries above are:
            // the lint keys on "neither records nor rethrows" and cannot see that the return value
            // IS the record, which is the right way round for a population guard to be wrong.
            //
            // 2 since SH675, and the second is the ordinary kind: `deleteQuietly` swallows an
            // IOException while removing the staging directory an install unpacked into. It runs in
            // a `finally`, after the answer is already decided, so reporting it would replace the
            // real outcome — installed, or refused with the reason — with the OS's opinion about a
            // temp directory. The bytes are in the system temp dir either way, which is somebody
            // else's cleanup by construction.
            Map.entry("marketplace", 2),
            // SH1108: a pushed pattern file that no longer parses reads as absent, so the rail and
            // `list --remote` report it as not pushed and the fix is the push that replaces it. A
            // diagnostics entry would name an operator file the operator already owns the fix for.
            Map.entry("pattern", 1),
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
            Map.entry("render", 9),
            // SH939 removed the `utils` row entirely, from 2 to none. Both catches were the two
            // arms of `ShUtils.isJSONValid` — try `JSONObject`, fall back to `JSONArray`, answer
            // false — and nothing called the method. So this is the one way the population is
            // allowed to shrink without an argument about whether a degradation is worth
            // reporting: the code that could degrade is gone.
            // SH719 raised this from 1 to 2. `ShPostUrlKeyBackfillRunner` swallows the failure of
            // its own startup pass, and the degradation is genuinely not worth an incident: the
            // only way it fails is a `url_key` column that is not there yet — a partial import, or
            // a changelog that has not run — and the honest consequence is that the address guard
            // is off until it has, not that content is wrong. Refusing to start over it would take
            // the instance down to protect a constraint that has nothing to protect yet.
            // SH1009 raised this from 2 to 3. `ShPostUnifiedWriteService.requiredEmpty`
            // reads the stored document to decide whether a publish is complete, and data
            // it cannot parse is not a required-field finding: it is a different defect
            // with a different remedy, already reported by verify's own rule and by the
            // read that surfaces it. Refusing here would answer one problem with a message
            // about another, sending whoever read it to fill in a value that is there.
            Map.entry("migration", 3),
            // 1 since SH1058: `ShDocumentDigest.of` hashes a document that does not parse as its
            // raw text instead of its canonical form. That is the correct digest, not a degraded one:
            // the only property the value has to keep is that it changes exactly when the content
            // does, and raw text still does. The malformed post is already reported by verify's own
            // rule and by the render path's CONTENT incident, so recording it here would put one
            // broken post in the diagnostics window once per translation measured against it.
            Map.entry("post", 1),
            // 1 since SH84: `ShTuringDocumentMapper.readData` reads a post whose `data` is not JSON
            // as "no text", and does not record it. The render path already reports that exact post
            // as a CONTENT degradation, which is the branch of SH270's own rule this falls under —
            // recording it a second time would put one broken post in the diagnostics window once
            // per publish, and folding by subject would then hide whichever of the two arrived
            // second. Not indexing its body is also the correct outcome, not a degradation: there is
            // no prose in a malformed payload to index.
            // 3 since SH912, and both are the "the lint cannot see it" kind rather than swallowed
            // failures. `ShTuringIndexListener.sendWithRetries` catches the send's RuntimeException
            // inside the retry loop and records *after* it — deliberately, because recording per
            // attempt coalesces into one incident carrying count 3, which reads to an operator as
            // three failed publishes rather than one push retried twice. That is the whole point of
            // the task, so the recording has to be outside the catch, and the lint keys on the
            // catch body. And `pause` catches `InterruptedException`, restores the flag and returns
            // false to stop retrying: a shutdown is not a diagnosis, and the caller then records
            // whatever the last real attempt actually said rather than inventing an outcome for the
            // attempts that never ran.
            // 7 since SH913, and all four are one decision rather than four. `ShTuringTextExtractor`
            // is contractually a method that <em>cannot</em> throw: its caller's next act is to index
            // the document, and a PDF findable by name is worth far more than a PDF not indexed at
            // all — so an exception escaping the extractor would cost the publish its document over
            // a failed text read. Each catch therefore converts to an `Extraction` outcome and the
            // *caller* records the incident, which is the one place that knows the post and the
            // site. The lint reads catch bodies and cannot follow a returned value to the recorder,
            // which is the right way round for a population guard to be wrong. (The four: a file
            // that cannot be stat'd, Turing exceeding Shio's own extract-timeout, the interrupt
            // that means shutdown, and the SDK throwing out of the OCR call.)
            // 9 since SH914, and both are the shape of the four above: a method whose contract is
            // to answer rather than throw. `ShTuringDocumentMapper.parsed` returns null for a value
            // that cannot be the declared type — a DOUBLE field holding "ask us" is omitted rather
            // than sent as 0, because a 0 would make that post answer an "under 5000" filter, and
            // the caller's `put` turns that null into an absent key, which is the grounding
            // contract this mapper exists to hold. `ShTuringFieldDeclaration` catches
            // IllegalAccessException while reading TurSNFieldName's constants reflectively: a
            // public static field this JVM refuses to read is not a thing that happens, and
            // skipping one could only narrow the collision guard, never widen it.
            //
            // It was ELEVEN on the first run — three near-identical parse blocks, one per numeric
            // type. They were collapsed into one generic `parsed` rather than declared, which is
            // the response this ledger exists to provoke: three copies of one decision read as
            // three decisions to whoever reviews the population next.
            //
            // 7 since SH917, and NOT because anything was removed: `ShTuringTextExtractor.ask`
            // carries the `one-decision:` marker, so its three catches — a timeout, an interrupt,
            // and the SDK throwing — count as the single answer they are. That is the whole of
            // SH917: this figure now counts decisions rather than catch blocks, so a refactor that
            // merges two identical handlers no longer looks like paying the debt down.
            Map.entry("turing", 7),
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
            Map.entry("spring", 1));

    private static final Pattern CATCH = Pattern.compile("catch\\s*\\(([^)]*)\\)\\s*\\{");

    /**
     * The marker that says a whole method is one decision, however many catches implement it
     * (SH917).
     *
     * <p><b>Why this exists.</b> This reader counted {@code catch} blocks, and that is not the unit
     * anybody reviews. Measured on the round that produced it: the {@code turing} figure went
     * 1 → 3 → 7 → 11 across three tasks, and <b>three of the ten added were one decision written
     * three times</b> — a numeric parse per type, each {@code try { … } catch (NumberFormatException)
     * { return null }}. Collapsing them into one generic helper took the figure to 9 and changed
     * nothing about the program. That is the right refactor and it is also the tell: the number
     * moved by two for a change in shape, so it was pricing duplication rather than risk.</p>
     *
     * <p>Placed in the comment or javadoc directly above a method signature, and it must name
     * <em>who records</em>. The recurring shape here is a method whose contract is to answer rather
     * than throw — every catch converts to a return value and the caller files the incident, which
     * this reader cannot follow. One entry per such method makes the count mean "how many places
     * swallow a failure", which is what the header above has always claimed it means.</p>
     */
    private static final String ONE_DECISION = "one-decision:";

    /** A line that opens a method rather than a block: has a paren, takes no keyword. */
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:@\\w+\\s+)*(?:public|protected|private|static|final|synchronized|abstract|<[^>]+>|\\s)*"
                    + "[\\w.<>\\[\\],?\\s]+\\s+(\\w+)\\s*\\([^;]*$");

    private static final Pattern BLOCK_KEYWORD =
            Pattern.compile("\\b(?:if|for|while|switch|catch|try|else|do|return|new)\\s*\\(");

    /**
     * @param method the enclosing method's name when the method declares itself one decision, else
     *               {@code null} — the grouping key, so three catches in one declared method are one
     */
    private record SilentCatch(String area, String where, String first, String method) {
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
                found.add(new SilentCatch(area, relative + ":" + (i + 1), firstLineOf(body),
                        oneDecisionMethod(lines, i, relative)));
            }
        }
        return found;
    }

    /**
     * The enclosing method's key when it declares itself one decision (SH917), else {@code null}.
     *
     * <p>Walks back to the nearest signature at a shallower indent than the {@code catch}, then
     * reads the comment block directly above it. Indentation again, for {@link #bodyAfter}'s reason:
     * a parser that got one nested lambda wrong would move a number nobody could explain, and the
     * declared table below is the assertion rather than the parser.</p>
     */
    private static String oneDecisionMethod(List<String> lines, int catchLine, String relative) {
        String opener = lines.get(catchLine);
        int catchIndent = opener.length() - opener.stripLeading().length();
        for (int j = catchLine - 1; j >= 0; j--) {
            String line = lines.get(j);
            if (line.isBlank()) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (indent >= catchIndent || BLOCK_KEYWORD.matcher(line).find()) {
                continue;
            }
            Matcher signature = METHOD.matcher(line);
            if (!signature.find()) {
                continue;
            }
            return declaresOneDecision(lines, j) ? relative + "#" + signature.group(1) : null;
        }
        return null;
    }

    /** Whether the comment block immediately above {@code signatureLine} carries the marker. */
    private static boolean declaresOneDecision(List<String> lines, int signatureLine) {
        for (int j = signatureLine - 1; j >= 0; j--) {
            String line = lines.get(j).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("*") && !line.startsWith("/*") && !line.startsWith("//")
                    && !line.startsWith("@")) {
                return false;
            }
            if (line.contains(ONE_DECISION)) {
                return true;
            }
        }
        return false;
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
        List<SilentCatch> all = inventory();
        // SH917 — one entry per DECISION. A method that declares itself `one-decision:` counts once
        // however many catches implement it; everything else counts per catch as it always did.
        Set<String> countedMethods = new java.util.HashSet<>();
        Map<String, Integer> measured = new TreeMap<>();
        for (SilentCatch silent : all) {
            if (silent.method() != null && !countedMethods.add(silent.method())) {
                continue;
            }
            measured.merge(silent.area(), 1, Integer::sum);
        }
        int grouped = all.size() - measured.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("SH270: %d silent catch(es) in %d decision(s) — %d folded by SH917's "
                + "one-decision marker%n", all.size(), all.size() - grouped, grouped);

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

    /**
     * SH917 — the marker folds a method's catches into one, and folds nothing else.
     *
     * <p>Both directions, because they are the two ways this can be wrong and only one of them is
     * loud. A marker that folds <b>nothing</b> leaves the figure counting catch blocks and the task
     * undone while every number still adds up. A marker that folds <b>everything</b> — a signature
     * matcher that walks past the method and reaches the class, say — would quietly collapse a whole
     * file into one entry, and the declared table would be adjusted down to meet it.</p>
     */
    @Test
    @DisplayName("SH917: a declared method counts once, and an undeclared one still counts per catch")
    void oneDecisionFoldsAMethodAndNothingElse() {
        List<SilentCatch> all = inventory();

        List<SilentCatch> folded = all.stream().filter(silent -> silent.method() != null).toList();
        assertThat(folded)
                .as("SH917: nothing carries the `one-decision:` marker, so this reader is still "
                        + "counting catch blocks and the task changed nothing")
                .isNotEmpty();
        assertThat(folded).allSatisfy(silent -> assertThat(silent.method())
                .as("a folded entry names the method it belongs to, which is the grouping key")
                .contains("#"));

        // The one that provoked SH917: three catches, one answer.
        assertThat(folded.stream().filter(silent -> silent.method().endsWith("#ask")).count())
                .as("SH917: ShTuringTextExtractor.ask is three catches for one decision — the case "
                        + "this marker exists for")
                .isEqualTo(3);

        // And the guard against a matcher that swallows a file: the overwhelming majority of silent
        // catches are ordinary and must still be counted one apiece.
        assertThat(all.stream().filter(silent -> silent.method() == null).count())
                .as("SH917: almost everything folded, which means the signature walk is reaching "
                        + "past its method — a marker on one method would then hide a whole file")
                .isGreaterThan(all.size() - 10L);
    }
}
