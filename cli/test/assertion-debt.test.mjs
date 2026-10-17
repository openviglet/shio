/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH527 — a defect closes with an assertion, and this is the reader that makes that checkable.
 *
 * ## The measurement
 *
 * Six times a correction shipped half of itself and the other half became a new task:
 * SH452→455, SH429→437, SH469→473, SH468→472, SH465→475, SH470→474. **Three of those shipped the
 * report and left the behaviour.** At 246 commits in seven days, with verification being a person
 * asking an agent to look afterwards, that is the system's expected output rather than bad luck.
 *
 * ## The rule, and why it needs a reader
 *
 * One line: **a defect closes with an assertion carrying its id.** Not a unit test of the fix's
 * internals — *the assertion that would have caught it*, at the boundary it crossed. SH448 already
 * proved the shape: run first, it was red by construction and found two defects seven tasks before
 * the queue would have.
 *
 * A rule nobody can check is a preference. The suite knows which ids it asserts and the ledger knows
 * which shipped, so the difference is **computable**: shipped tasks with no assertion, listed. That
 * number is the debt, and it only goes down when somebody pays it.
 *
 * ## Why a list and not a ceiling
 *
 * A ceiling ("no more than 61") lets one task pay the debt and the next spend it, which is how a
 * budget becomes a licence. The list names every id, so a ship with no assertion fails **by name**
 * and the failure says what to do about it — and a task that genuinely cannot carry one is added
 * here deliberately, in a diff somebody reviews, with the reason beside it. That is the contract
 * `ShSilentCatchInventoryTest` already uses for a population nobody can lint.
 *
 * ## And it stays cheap
 *
 * Reading 294 files and one ledger, no network and no browser: §IX.36's last paragraph is a
 * requirement, not an aspiration. A suite that grows a case per defect and never sheds one becomes
 * the suite people skip, which is the failure mode that produced this backlog.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

import { isProse } from "./support/source-text.mjs";

// @scans code — an id in a comment is a sentence about a task, not an assertion about it.
const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * Every tree that can hold an assertion, and the fact that this is a list is the point.
 *
 * <p>A glob over the whole repository would count an id mentioned in a source comment as an
 * assertion, which is exactly the thing this exists to distinguish: SH512's own docstring names
 * SH512, and a rule that accepted that would report zero debt for ever.</p>
 *
 * <p><b>An entry may narrow to a file pattern, and SH579 is why.</b> Every tree above holds
 * nothing but suites, so the directory was the whole selector — until `shio-site` grew its first
 * two, which sit in `scripts/` <i>beside the scripts they test</i>. Taking that directory whole
 * credits `prerender.mjs`, `markdown.mjs` and `token-budgets.mjs` for the ids their comments
 * name — three of them on the declared debt below, paid off by a source file mentioning them,
 * which is the exact false credit the paragraph above refuses. So the entry names the files.
 * (Measured: the first version of this note quoted those three ids in prose, outside the
 * declaration markers, and thereby credited all three itself. A reader that reads itself has to
 * be careful what it says out loud.)</p>
 *
 * <p>The blind spot cost eight commits. SH566 and SH571 each shipped two assertions, both in
 * `shio-site/scripts`, and this reader could not see either — so it reported them as debt and was
 * red at HEAD from the commit after it shipped. The alarm was wrong and nobody looked, which is
 * the case §IX.52 is actually about: an undeclared red suite teaches people that red means
 * nothing, and it does that whether or not the red is true.</p>
 */
const SUITES = [
  "claude-plugin/test",
  "cli/test",
  "cli/conformance",
  "create-shio-app/test",
  "shio-app/src/test",
  "shio-client/test",
  "shio-model/test",
  "shio-react/test",
  "shio-sections/test",
  { tree: "shio-site/scripts", only: /\.test\.mjs$/ },
];

const READABLE = new Set([".mjs", ".js", ".ts", ".tsx", ".java"]);

/**
 * The two markers around a declaration, whose ids must not count (SH527, SH843).
 *
 * <p>Found twice, in opposite directions, which is why the boundary is here and not at the file.
 * **First**: reading this file whole counted all forty declared ids as *asserted*, so the debt came
 * back as three — a declaration that something is unasserted is not an assertion of it. **Then**,
 * with the whole file excluded, SH527 reported *itself* as debt, because the assertion for SH527 is
 * this file and nothing else names it. So what is skipped is the region between these markers, and
 * the rest of the file counts like any other suite: a test whose name carries an id is exactly the
 * assertion this reader is looking for.</p>
 *
 * <h3>SH843 — the markers were general and only this file wrote them</h3>
 *
 * <p>`withoutDeclaration` has always run over <b>every</b> file in the walk; no other file had ever
 * carried the pair, so the mechanism read as private to this one. It is not. A named list's reason
 * is a string in an object literal — <em>code</em>, by the not-a-comment rule below — so any ledger
 * with a sentence per entry credits every id those sentences name. Measured across every suite file:
 * <b>832 ids credited anywhere, three whose every credit was a reason field</b> — SH253 in
 * `content-round-trip.test.mjs`, SH641 and SH842 in `handoffs.test.mjs`. The third is the argument:
 * SH842 shipped one iteration earlier, its own rule says a batch payment belongs in `DEBT`, and a
 * `why:` string paid it instead — silently, in the commit that wrote the sentence, which is SH731's
 * failure by a route SH731 does not cover.</p>
 *
 * <p>The cure is <b>not</b> a broader comment rule: a reason field is deliberately not a comment,
 * and blanking every string would stop an assertion message paying anything at all. Those two
 * ledgers carry the markers now, and `aDeclarationMarkerIsBalanced` refuses a `start` with no `end`
 * — which would swallow the rest of a file's assertions in silence, retiring debt rather than
 * inventing it, the same direction every failure of this reader has taken.</p>
 */
const MARK = "debt-declaration";
const DECLARATION = [`${MARK}:start`, `${MARK}:end`];

/* debt-declaration:start — everything to the end marker is a DECLARATION, not an assertion. */

/**
 * Shipped work whose deliverable is **not in this repository**, so no suite here can assert it.
 *
 * <p>Not debt and not an exemption from the rule — the rule is about the boundary a defect crossed,
 * and for these the boundary is somewhere else. Listed rather than pattern-matched, because "Block R
 * is docs" is a claim that stops being true the moment a Block R task ships code.</p>
 */
const ELSEWHERE = {
  // Block R writes end-user documentation into the `vigletdocs` repository
  // (D:\Git\viglet\vigletdocs\vigletdocs.github.io), which has no test harness and is not a
  // checkout this suite can see.
  //
  // SH499 has now shipped and it does reach those pages — `cli/conformance/docs-vocabulary` and
  // `docs-examples.conformance.mjs` read them out of `SHIO_DOCS_DIR` and hold their invocations
  // against this product. It does **not** empty this list, and the distinction is the point: what
  // those two instruments assert is the *vocabulary and the requests*, which is one property
  // shared by every page. Each id below shipped a page whose prose, structure and worked example
  // are its own deliverable, and nothing here names one. They stay ELSEWHERE for the reason they
  // arrived — the boundary is in another repository — rather than for want of a harness.
  //
  // Block T is the same boundary and arrives for the same reason: it writes the curator's half of
  // those pages (SH623 rewrote `administration-guide` against the React console, SH624–SH627 added
  // the lifecycle, console, webhook and i18n pages, SH629 the image epic's operator half under
  // Search & Caching). SH628 is deliberately **not** here — its
  // deliverable was an instrument rather than prose, so it is asserted like any other change, by
  // `cli/conformance/docs-stack.conformance.mjs`. That split is the useful thing this list records:
  // a docs task whose claim is checkable lands a check, and only the prose stays elsewhere.
  //
  // SH700 is the purest case this list has, and worth naming as such: its deliverable was a `git
  // push` of thirteen commits in that repository. The gate it turned green — `conformance:docs` —
  // already existed and already asserted the content; what was red was that CI read `origin` while
  // the fixes sat unpushed on a laptop. There is no assertion to write here, because the thing that
  // was wrong was not in any file this suite can reach. It is also the entry that says what the
  // conformance tier is for: it read a state nobody had looked at.
  //
  // SH715 is here for the same boundary and is worth distinguishing from SH628's split above. Its
  // deliverable was a Docusaurus plugin and a slug rename in that repository, and the assertion
  // that would have caught the defect is a **build of that site** — the old address exists only
  // because the redirect emits it, which was proved by removing the redirect and watching the page
  // vanish. That build is not a thing any suite here can run.
  docs: ["SH479", "SH480", "SH481", "SH482", "SH483", "SH484", "SH485", "SH486", "SH487", "SH488",
    "SH489", "SH490", "SH491", "SH492", "SH493", "SH494", "SH495", "SH496", "SH497", "SH498",
    "SH504", "SH623", "SH624", "SH625", "SH626", "SH627", "SH629", "SH700", "SH715"],
};

/**
 * Shipped work with no assertion naming it — **the debt**, as it stood when this reader shipped.
 *
 * <p>Every entry is a task that closed and left nothing behind that would catch its defect coming
 * back. The list goes down when somebody pays one off; it goes **up** only through a diff that says
 * why, which is the whole mechanism. Grouped by what each group would take, because the groups are
 * not equally cheap and a flat list of 40 ids reads as hopeless rather than as work.</p>
 *
 * <h2>SH529 — each group now carries a verdict, because a list is not a decision</h2>
 *
 * <p>SH527 established the count and held it as a named list. That made the debt visible and left
 * the question SH529 asks unanswered: <b>which of these earn an assertion?</b> Not all of them do —
 * a one-line fix to a message does not, and saying so is the work rather than a way out of it. So
 * every group below says EARNS or DOES NOT, with the reason and the shape the assertion would take.
 * The output SH529 wanted is a decision per task, not a test per task.</p>
 *
 * <p>The order is by <b>whether the defect could return</b>, which is not the same as by cost. A
 * removal is the cheapest thing here and also the one where nothing at all would notice a reversal;
 * a console page has a person looking at it every time somebody opens the console.</p>
 *
 * <p><b>The removals came off this list first, and the assertion was red.</b>
 * {@code ShRemovalsStayedRemovedTest} closes SH20, SH21 and SH60 — and writing it found that SH21
 * had never finished: four tracked files, about 456 KB of the legacy AngularJS bundle, still in
 * {@code src/main/resources/ui/} under a commit whose subject is "delete legacy AngularJS
 * resources/ui tree". Nothing referenced them, nothing served them, and they shipped in every jar
 * for two blocks. That is the argument for this whole exercise in one case: the entry said
 * "unasserted", and the assertion turned out to be a defect.</p>
 */
const DEBT = [
  // DOES NOT EARN ONE, and this is the first entry added for that reason rather than for cost.
  // SH821's deliverable is four ids moved into the assertions that would have caught their
  // defects — so it is asserted four times over, by SH52 in `ShCdaSearchTest`, SH76 in
  // `ShAgentManifestTest`, SH92 in `ShBlueprintRegistryTest` and SH93 in `ShBlueprintApplyTest`.
  // What it cannot have is an assertion about ITSELF, and three designs were built and measured
  // before saying so rather than after: (1) a prose lint refusing a count written beside the list
  // it describes fired on three legitimate sentences including a quotation of the defect it was
  // fixing; (2) "no pointer names an id several suite files name" flags 49 of 49, because this
  // file names every one of them; (3) restricted to header docstrings, 25 of 49 — and most of
  // those are two suites legitimately citing one task. The finding SH821 actually made is that
  // roughly a third of `CLOSE` is not the one-line move its name promises, which is a measurement
  // recorded in the REVEALED note above and is not a property any run can fail on.
  // SH826 is the same shape and is here for the same reason, which makes it the rule rather than
  // the exception: **a batch payment's deliverable is the assertions it creates, each naming a
  // different id, and the batch's own id has nothing left to assert.** SH826 moved SH39, SH40 and
  // SH43 into the `transform/` unit suites and SH61 into the listing test; what it discovered —
  // that three of four belonged in a file the ledger never named, putting the pointer-miss rate
  // nearer two in five than a third — is a measurement recorded in the REVEALED note above, and a
  // measurement is not a thing a run can fail on. A later batch belongs here without re-arguing it.
  // SH842 is the third, and it arrives by the route SH843 opened rather than by the rule above:
  // it never reached this list at all, because a `why:` string in `handoffs.test.mjs` credited it
  // as asserted. A ledger's reason is a string in an object literal — code, by the not-a-comment
  // rule — so the sentence explaining where SH113's assertion lives paid SH842's debt in the same
  // commit that wrote it. Marking that ledger as a declaration is what let the id fall to where the
  // rule had always sent it. SH641 was added here in the same edit and taken straight back out,
  // which is worth the line: it looked identical — its deliverable is an id deliberately NOT
  // written in `ShAgentReadTest` — and the run said it is asserted elsewhere. Two ids losing one
  // fake credit each does not make them the same kind of debt, and the reader is what settles it.
  "SH821", "SH826", "SH842",
  // EARNS, and it is the largest group by far. The console has a test tree (`shio-react/test`)
  // holding no reference to any of these; each is a React page or widget behaviour, so each is
  // assertable there. Ordered LAST despite the size, because these are the defects a human meets:
  // a broken editor page is seen the first time somebody opens it, so the window between a
  // regression and somebody noticing is short. The others are silent.
  // SH50 came off in SH823: `console-english.test.mjs` is the assertion that would have caught
  // it — a sweep of hardcoded English with nothing watching for the next one.
  "SH7", "SH8", "SH14", "SH48", "SH49", "SH56", "SH57", "SH59", "SH73",
  "SH179", "SH180", "SH182", "SH237",
  // SH716 arrives by a route none of the groups above describes: **its assertion was deleted with
  // the surface it guarded.** `breadcrumb-key.test.mjs` asserted that a breadcrumb trail's React
  // keys are unique, and SH910 deleted `lib/breadcrumb-key.ts` along with the console page header
  // — the only thing that ever called `crumbKey`. Nothing renders a breadcrumb now: the bento shell
  // says so in as many words ("the breadcrumb bar is console chrome, and the bento era puts a
  // page's identity in its own hero").
  // SH933 finished the retirement rather than reversing it: the twenty-one pages that still fed
  // `BreadcrumbProvider` stopped, the provider is unmounted and the context is deleted. So this is
  // **permanent debt of a particular kind** — not "nobody has written the assertion yet" but "the
  // surface it would assert against no longer exists". A trail cannot have duplicate React keys
  // when nothing renders a trail.
  // Kept rather than deleted because the ledger still records SH716 as shipped, and a shipped id
  // with neither an assertion nor a line here is exactly what this reader is for. If the bento era
  // ever grows a trail, `breadcrumb-key.test.mjs` comes back from git history with it.
  "SH716",
  // PAID (SH529). Removals and port-or-deprecate decisions: `ShRemovalsStayedRemovedTest` asserts
  // the legacy AngularJS bundles, the Thymeleaf starter and the four deprecated widget names are
  // absent. They ranked first for return-risk rather than for cost — an absence has NOBODY looking
  // at it, so a reversal is the one kind of regression with no human in its path. Writing it found
  // SH21 unfinished; see the class note.
  // PARTLY PAID (SH567). The public site in `shio-site/`: when this reader shipped, the note read
  // "no test tree at all", and the three the note named as assertable — the token budgets, the
  // prerender's route table and the Markdown twin — now are, in `scripts/site-claims.test.mjs`
  // (SH533, SH532, SH537). What is left is the COPY, which is the harder half and the reason this
  // group ranked second by return-risk: a marketing claim that has gone false is invisible until
  // somebody reads the page against the product, and the site's own rule (every claim is about a
  // real surface) is the one rule a marketing site cannot self-check.
  //
  // SH530 and SH537 both came off on the first attempt and both were FALSE payments — the reader
  // counts an id a suite names, and the first draft named SH530 in prose and put SH537 on a test
  // that asserted route parity (which is SH532's claim). It is the same shape as the review-queue
  // entry two tasks ago, whose id an assertion message mentioned without asserting it — and naming
  // THAT id here would pay it off a third time, which is why this sentence does not.
  "SH530", "SH531", "SH534", "SH535", "SH536", "SH538", "SH539",
  // EARNS, all five, and they are the same shape as the removals: a lint over the repository rather
  // than a test of a behaviour, and nothing human-facing to notice a reversal. A mutating test
  // forcing its precondition (SH296), `noRollbackFor` on the class (SH352), the plugin's version
  // floor (SH355), the surefire fork count (SH414), `skipITs` defaulting true (SH415).
  // Third by return-risk, and each is a few lines.
  //
  // The sixth was paid, and by the sweep rather than by a task aimed at it. SH669's licence check
  // in `docs-stack.conformance.mjs` asserts the SPDX id in `package.json` against the text of
  // `LICENSE` — which is SH541's defect exactly, one file carrying a claim the other 194 had left
  // — so the id is now named in an assertion message at the boundary it crossed, and this reader
  // finds it there. Removed rather than left: an entry that says "unasserted" about something a
  // suite holds is the stale half this list's own second test fails on.
  "SH296", "SH355", "SH414", "SH415",
  // EARNS NOTHING YET, and that is the entry rather than an excuse. SH778's shipped half is
  // EVIDENCE: three causes of an intermittent file-level red ruled out by measurement, and a third
  // instrument that prints the exit code and the open handles when the fourth sighting comes. There
  // is no assertion that "would have caught" it, because nothing is fixed — the defect has not
  // named itself yet, and it did not reproduce in twelve runs after the instrument landed (six
  // ordinary, six at `--test-concurrency=32`; SH698 saw eighteen). Writing a test that the three
  // reporters are registered would assert the fix's internals, which is the one thing SH527's rule
  // says an entry may not be. It comes off with the cure, which is SH780.
  "SH778",
  // EARNS, and it is the cheapest closure on the list rather than new coverage. The locale run
  // (SH361 is its own decision entry, SH366-SH371 the six steps): four of the six are already
  // asserted elsewhere BY BEHAVIOUR and not by id, so the work is naming the id in a test that
  // already passes. An assertion nobody can find from the id does not close the loop — which is a
  // real gap and a much smaller one than the group's size suggests.
  //
  // SH361 is paid (2026-08-09). It was the group's *decision* entry rather than one of the six
  // steps — an id names a step, not an entry somebody later rewrites — so what closes it is holding
  // that outcome: `cli/test/improvements-debt.test.mjs` fails if one id ever carries two lines in
  // the roadmap or two entries in the ledger, which is the duplicate `SH347` SH356 counted and
  // SH361 renumbered away. The six steps stay: each is a behaviour asserted under no id.
  "SH366", "SH367", "SH368", "SH369", "SH370", "SH371",
  // SH238 and SH659 were listed here, and the entry said the honest fix was to teach this reader
  // `shio-react/e2e` — where their assertion lives and needs a running instance. SH667 paid them
  // off from the other end instead, and the difference is worth keeping: the e2e assertion was
  // there the whole time and *skipping*, which kept `ok: true` and counted toward "N/N probes ok".
  // `ci-gates.test.mjs` asserts what makes it count — that CI passes `--strict` and that the script
  // acts on it — which is instance-free, in a tree this reader already walks, and is the boundary
  // the defect actually crossed. An unread assertion and an unrun one are not the same gap.
  //
  // SH699 was listed here for one commit, and SH701 paid it off in the next — which is the shortest
  // this list has ever held anything, and worth keeping as the worked example of what an entry is
  // FOR. The entry said: the defect is caught by `pnpm lint` exactly, eslint's
  // `react-hooks/set-state-in-effect` being the assertion at the boundary it crossed, but a rule
  // cannot name a task, so this reader could not find it. `lint-baseline.test.mjs` closed it from
  // the other end — it asserts the *baseline*, names SH699 in the message, and the thing it asserts
  // is the one eslint structurally cannot: that the suppression file did not absorb the next one.
  // An assertion that exists and an assertion this reader can find were the same gap here.
  //
  // DOES NOT EARN one, and this is the SH529 verdict rather than a shrug. SH703 replaced six
  // hand-maintained flags with derived ones. Every user-visible difference it makes is **one frame
  // wide** — the spinner arriving a render late, a selection count clearing a render late, a
  // `Connected` badge outliving its destination by a render — because the effects it deleted did
  // run, just after the paint. SH702 was assertable precisely because its stale state lasted the
  // whole read; this one is not, and an e2e that cannot reliably distinguish a frame is a flaky
  // gate, which SH579 says is worse than a red one. What IS asserted is the shape:
  // `lint-baseline.test.mjs` holds all six files as paid off, so re-suppressing one fails a gate.
  // The one behavioural thing SH703 fixed that is not a frame — a slow resolve for a file the
  // curator had already replaced landing on the new one — would earn a probe, and needs a post with
  // a File field and two selections raced against a route delay.
  "SH703",
  // DOES NOT EARN one, and it is the rarest reason on this list: the assertion that would have
  // caught SH797 already existed, already worked, and is what found it. SH747's ratchet went red on
  // the twelfth unnamed id exactly as designed. Nothing about the check was wrong — the gate holding
  // it needs another repository checked out and had not been run for twelve commits, which is a fact
  // about when instruments are executed rather than about what they assert.
  //
  // So an assertion naming SH797 would have to assert that somebody ran a gate, and the honest
  // versions of that are filed rather than faked: SH798 (the docs gate leaves no stamp, so running
  // it is invisible to the comparators) and SH799 (the local queue is unbounded, so one unnamed id
  // and twelve read the same). When either ships, this entry comes off — and if it does not, that
  // is the reader saying the process finding was never closed.
  "SH797",
  // DOES NOT EARN one, and it is SH797's reason a second time — which is what makes it a shape
  // rather than an accident. SH1072's deliverable is *rows of data in another repository*: six
  // shipped ids named in `docs-shio/COVERAGE.md`, plus the mirror this repository keeps of it. The
  // property "every shipped id is named in the matrix" is already asserted, by the same SH758
  // reader that went red and produced the task — and by SH799's ceiling, which is what turned a
  // printed queue into a failing build on the sixth id.
  //
  // An assertion naming SH1072 would have to assert that somebody edited a file this repository
  // cannot see except through the mirror the task itself refreshes, which is circular: it would go
  // green on the mirror being written and say nothing about the matrix. The honest open half is
  // filed as the line's own criterion instead — that a *fresh clone* of vigletdocs reads a matrix
  // naming the six, which is untrue until that edit is committed there. This entry comes off when
  // it is.
  "SH1072",
  // DOES NOT EARN one, for SH1072's reason pointed at GitHub instead of a sibling repository.
  // SH1241's deliverable is repository metadata on github.com (description, homepage, topics of
  // openviglet/shio and shio-ce), set with `gh repo edit`. No file here holds it, and a suite that
  // called the GitHub API would be a network test asserting someone else's state. Its check was the
  // line's own criterion, `gh repo view`, run at ship.
  "SH1241",
  // DOES NOT EARN one, for the reason a measurement never does. What SH805 shipped is a *number*
  // and the elimination it bought: the drain is 50-140ms per file alone, 45ms for the whole suite
  // alone, and ~5.5s only inside `pnpm test` under workspace concurrency — so it is not a lingering
  // handle in any file, which was the obvious guess and is now ruled out.
  //
  // The assertion that would fit is a timing one, and a timing assertion over a load-dependent
  // effect is a flake by construction — this very round spent two tasks on a flake nobody could
  // read. There is no cure half to carry one instead: the drain SH805 was filed about does not
  // exist. `beforeExit` and `exit` fire a millisecond apart with only stdio alive, and paired runs
  // put the flag's cost at nothing, so what the task delivered is the elimination itself.
  "SH805",
].sort(byId);

/**
 * Debt that was always there and was being paid by prose — revealed by SH630, triaged by SH642.
 *
 * <h2>What the triage found, and it changes what the number means</h2>
 *
 * <p>These ids were on nobody's list because a comment somewhere named them, and the walk could
 * not tell a comment from an assertion. SH642 asked SH529's question of each — <b>which of these
 * earn an assertion?</b> — and the answer was not the expected one. <b>Every one of the 88 is
 * already named by a suite file, and 63 of them within twelve lines of an assertion.</b> None is
 * silent; none is named only in source.</p>
 *
 * <p>So the verdict for this whole population is neither EARNS nor DOES NOT in the SH529 sense.
 * It is: <b>the test exists and does not carry the id where this reader can see it.</b> That is a
 * different kind of debt from the original 35, which are tasks with nothing standing behind
 * them, and folding the two would misprice both.</p>
 *
 * <h2>Each entry names its evidence, which is also the payment instruction</h2>
 *
 * <p>The file recorded against an id is one that names it today. Paying the entry off means
 * moving the id out of that file's prose and into the message of the assertion it sits beside —
 * <b>if</b> that assertion is genuinely the one that would have caught the defect, which is the
 * judgement no measurement can make. Where it is not, the entry earns a real assertion and the
 * pointer is where to start looking rather than the answer.</p>
 *
 * <p><b>How often that happens, measured on the first fifteen paid (SH820, SH821, SH826).</b>
 * <b>Six</b> had their real home in a different file: the recorded one mentioned the id in
 * passing — as a <i>contrast</i>, in one case (`search: true` beside `searchIndexing: false`) —
 * while the assertion that would have caught the defect sat in the suite named after it. One more
 * had no local candidate at all, its guard being in another module entirely. So the pointer is a
 * starting point in the literal sense, and reading the ledger entry for the id in `CHANGELOG.md`
 * is what settles it: it says what shipped, which is what an assertion has to be able to fail
 * about.</p>
 *
 * <p>The rate is worth stating because it is <b>not</b> settling where it first looked. Eleven paid
 * put it near a third; SH826's four moved three more, and the figure is closer to <b>two in five</b>.
 * The pattern behind it is legible: an id whose work spans a subsystem gets mentioned by the API
 * test that exercises the surface, while the assertion that would have caught it lives in the unit
 * suite named after the component — SH39, SH40 and SH43 were all recorded against
 * `ShStaticFileAPITest` and all three belong in `transform/`. Budget for a search, not a move.</p>
 *
 * <p>{@code CLOSE} is those whose mention sits beside an assertion; {@code HEADER} is the ones
 * named in a class docstring, where the file is about the task and no one assertion carries it.
 * The second group is the more expensive half and it is marked so nobody starts with it by
 * accident. Neither carries a size — the two sentences that did said 63 and 62 of the same list
 * (SH820), and the run prints both figures on every pass anyway.</p>
 *
 * <p>Still a named list rather than a number, so it can only shrink through a diff that says
 * which and why.</p>
 */
const REVEALED = {
  // CLOSE — the mention is within a dozen lines of an assertion in this file.
  "SH3"    : "ShCdaRateLimiterTest.java",
  "SH4"    : "ShApiTokenFilterTest.java",
  "SH5"    : "ShApiTokenFilterTest.java",
  "SH9"    : "scaffold.test.mjs",
  "SH12"   : "ShFolderTrashTest.java",
  "SH26"   : "ShLiquibaseChangelogAgnosticTest.java",
  "SH51"   : "ShFolderTrashTest.java",
  "SH58"   : "ShEmailConfigServiceTest.java",
  "SH63"   : "emit.test.mjs",
  "SH71"   : "ShAgentBatchTest.java",
  "SH72"   : "impact.test.mjs",
  "SH78"   : "ShAgentResponseBudgetTest.java",
  "SH83"   : "ShMcpServerTest.java",
  "SH89"   : "content-assets.test.mjs",
  "SH94"   : "ShBlueprintRegistryTest.java",
  "SH96"   : "blueprints.test.mjs",
  "SH100"  : "ShAddressResolverTest.java",
  "SH104"  : "ShAgentP1ConformanceTest.java",
  "SH115"  : "remember.test.mjs",
  "SH122"  : "ShTenantJoinHazardTest.java",
  "SH123"  : "ShAgentReadTest.java",
  "SH128"  : "ShAgentMoveAndPruneTest.java",
  "SH151"  : "ShAgentBatchTest.java",
  "SH153"  : "ShBlueprintApplyTest.java",
  "SH154"  : "ShSectionCompositionTest.java",
  "SH155"  : "ShBlueprintApplyTest.java",
  "SH173"  : "content-assets.test.mjs",
  "SH175"  : "ShAgentOpsContractTest.java",
  "SH176"  : "ShAgentSourceAttributionTest.java",
  // SH196 was here, credited to ShAgentSafetyTest, and SH1005 paid it: the subject column has been
  // on every audit row since SH196 and no reader carried it, so a folder rename read as a post's.
  // `ShHistoryTrailTest` asks the trail for `subject=folder` and gets the folder row and only it —
  // an assertion at the boundary the defect crossed, which is a reader's, not the writer's.
  // SH203 was here, credited to ShObjectCopyNamingTest, and SH244 paid it: `folder.copy` drives a
  // destination inside the source's own subtree and requires `folder-copy-cycle`, so the guard
  // SH203 installed on `ShFolderWriteService.checkCopyable` — the one both surfaces share — now
  // fails a build if it is removed. A different entry point from the console's, and the same code.
  "SH236"  : "ShPostTypeRenameTest.java",
  "SH288"  : "ShAgentP1ConformanceTest.java",
  "SH300"  : "render.conformance.mjs",
  "SH306"  : "ShEditorBridgeTest.java",
  "SH324"  : "ShCdaWriteTest.java",
  "SH337"  : "ShHeadConventionsTest.java",
  "SH360"  : "ShTemplateFieldLintTest.java",
  "SH376"  : "ShAgentCommitOrderTest.java",
  "SH381"  : "ShConvertedLayoutRenderTest.java",
  "SH458"  : "render.conformance.mjs",
  "SH544"  : "clone.test.mjs",
  "SH569"  : "source-corpus.test.mjs",

  // HEADER — named in a class docstring: the file is about the task, and which assertion
  // carries it is the judgement this pointer cannot make.
  "SH53"   : "ShContentLocaleTest.java",  // header
  "SH64"   : "compile.test.mjs",  // header
  "SH65"   : "ShAgentDesiredStateTest.java",  // header
  "SH69"   : "ShSiteExportServiceTest.java",  // header
  "SH70"   : "ShImportRendersSourceTest.java",  // header
  "SH86"   : "content-files.test.mjs",  // header
  "SH87"   : "content-commands.test.mjs",  // header
  "SH97"   : "plugin.test.mjs",  // header
  "SH126"  : "context.test.mjs",  // header
  "SH129"  : "apply.test.mjs",  // header
  "SH163"  : "ShPostTypeRenameTest.java",  // header
  "SH164"  : "ShAgentSourceAttributionTest.java",  // header
  "SH201"  : "ShObjectMoveAuthorizationTest.java",  // header
  "SH246"  : "ShAgentRenderedDigestTest.java",  // header
  "SH276"  : "ShContextCacheHeadroomTest.java",  // header
  "SH284"  : "shio-editor-bridge.test.mjs",  // header
  "SH322"  : "ShTestcontainersEagerStartLintTest.java",  // header
  "SH333"  : "i18n-parity.test.mjs",  // header
  "SH358"  : "ShAgentDiagnosticsCeilingTest.java",  // header
  "SH383"  : "ShRenderLocaleTest.java",  // header
  "SH390"  : "ShRenderImageTest.java",  // header
  "SH529"  : "ShRemovalsStayedRemovedTest.java",  // header
  "SH567"  : "site-claims.test.mjs",  // header
  "SH620"  : "ShRemovalsStayedRemovedTest.java",  // header
};

/**
 * The ones named in a class docstring rather than beside an assertion (SH642).
 *
 * <p>The more expensive half, marked so nobody starts with it by accident: the file is about the
 * task and no one assertion carries it, so which assertion earns the id is a judgement no
 * measurement makes. The rest are closer to a one-line move — and only closer, which SH820 found
 * on doing seven of them: each still needs somebody to decide which assertion in the neighbourhood
 * is the one that would have caught the defect, and one of the seven had no local candidate at
 * all (SH198's leaked timer, whose real guard is SH807's watchdog a repository away).</p>
 */
const HEADER = new Set([
  "SH53", "SH64", "SH65", "SH69", "SH70", "SH86", "SH87", "SH97", "SH126", "SH129",
  "SH163", "SH164", "SH201", "SH246", "SH276", "SH284", "SH322", "SH333", "SH358",
  "SH383", "SH390", "SH529", "SH567", "SH620",
]);

/** The ids of the above, which is what the debt comparison needs. */
const REVEALED_IDS = Object.keys(REVEALED);

/**
 * The ones whose mention sits beside an assertion — the cheap half, and where to start.
 *
 * Derived from the file rather than declared twice: the `// header` marker is the one distinction
 * between the two groups, and a second list of ids would be a second thing to keep in step.
 *
 * **No count here (SH820).** It read "the 63" and then "the 62" four lines apart, which is what a
 * number written beside a list it is supposed to describe always comes to — and the moment the
 * list shrank, both were wrong in the same direction. The run prints the size on every pass; the
 * list is the thing that is asserted.
 */
const CLOSE = REVEALED_IDS.filter((id) => !HEADER.has(id));

/* debt-declaration:end */

function byId(a, b) {
  return Number(a.slice(2)) - Number(b.slice(2));
}

/** Every id the ledger records as shipped. `🗑` retirements are not shipments and are excluded. */
/**
 * Every id the roadmap still shows as open (SH745).
 *
 * <p>The other half of the disagreement this file computes. `shippedIds` finds tasks the ledger
 * says are done; this finds the ones it says are not, so the two can be asked whether the suite
 * agrees with either.</p>
 */
async function openIds() {
  const roadmap = await readFile(join(ROOT, "docs/ROADMAP.md"), "utf8");
  return new Set([...roadmap.matchAll(/\*\*(SH\d+)\*\*/g)].map(([, id]) => id));
}

async function shippedIds() {
  const ledger = await readFile(join(ROOT, "docs/CHANGELOG.md"), "utf8");
  return new Set([...ledger.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
}

/** Every id any suite in this repository names. */
async function assertedIds() {
  const found = new Set();
  // Which file credited each id (SH731). Only ever read on the failure paths, where "SH322 is not
  // in debt after all" is useless and "ShFooLintTest.java credits SH322" is the whole diagnosis —
  // that is the trip the SH722 mislabel actually cost, made in advance rather than by hand.
  const by = new Map();
  let files = 0;
  for (const suite of SUITES) {
    const { tree, only } = typeof suite === "string" ? { tree: suite, only: null } : suite;
    for (const path of walk(join(ROOT, tree))) {
      if (only && !only.test(path)) continue;
      files += 1;
      const body = withoutDeclaration(await readFile(path, "utf8"));
      for (const id of assertedIn(body, path.endsWith(".java"))) {
        found.add(id);
        by.set(id, [...(by.get(id) ?? []), path.split(/[/\\]/).pop()]);
      }
    }
  }
  return { found, files, by };
}

/**
 * The ids one file **asserts**, as opposed to the ids it mentions (SH630).
 *
 * <h2>The failure this replaces</h2>
 *
 * <p>The walk matched task ids over raw text, so a comment and an assertion message were the same
 * bytes. The direction of that failure is what made it worth fixing: it never invented debt, it
 * only *retired* it — silently, in a diff about something else, at the moment somebody was writing
 * carefully about why a task mattered. <b>The better the comment, the more debt it paid.</b> And
 * the workaround was to stop naming ids in prose, which degrades the writing to protect the
 * counter — the instrument shaping what may be written about the code it reads.</p>
 *
 * <h2>The count that chose the rule</h2>
 *
 * <p>SH630 named two candidate repairs and said a count decides between them. Measured over the
 * whole suite corpus:</p>
 *
 * <pre>
 *   raw text (the old rule)             524 ids
 *   not on a full-line comment          428 ids
 *   ...and inside a string literal      428 ids   &lt;- identical
 * </pre>
 *
 * <p><b>The two candidates give the same answer</b>, so the parse buys nothing: every id that
 * survives the comment rule is already inside an assertion message or a name. The simpler rule
 * wins, and the second candidate — an explicit marker convention — is not needed to distinguish
 * anything. <b>96 ids were being paid by prose.</b></p>
 *
 * <h2>Why this is per line and holds no state</h2>
 *
 * <p>The first attempt used a proper comment stripper and it <b>desynced</b>:
 * {@code replication.conformance.mjs} contains a regex literal with a double quote inside it, which
 * the scanner read as a string opening, and every line after it in that file was misclassified. It
 * reported a plausible number and was wrong — the exact hazard SH613 names, where a broken stripper
 * blinds the instrument rather than failing it. So this carries no cross-line state beyond a text-
 * block parity, and a regex literal cannot derail it.</p>
 *
 * <p>Conservative on purpose: an id in a trailing comment <i>after</i> code on the same line still
 * counts. That errs toward keeping a payment rather than inventing debt, which is the direction a
 * ledger should be wrong in.</p>
 *
 * <h2>A citation is not an assertion (SH731)</h2>
 *
 * <p>The rule above still cannot tell <i>this assertion is the one SH322 asked for</i> from <i>this
 * message mentions SH322 in passing</i>, and that mislabelled three tasks. The third is the one
 * worth reading: SH722's lint ended its failure message "…which is why this lint exists rather than
 * one more repair (SH322)" — a citation of the rule it follows, not a claim to close it. This
 * reader dropped SH322 from the debt list and the suite went red saying somebody had paid it off.
 * Nothing was wrong except the sentence, and the repair — move the citation into the javadoc — is a
 * rule nobody can follow reliably, because the message is exactly where a reader wants the
 * reference.</p>
 *
 * <p><b>SH731 named two candidate repairs and a count decided between them</b>, the same way SH630
 * chose this function's rule. Measured over all 405 suite files, 699 ids credited today:</p>
 *
 * <pre>
 *   the first id on a line is the subject   641 ids   58 lose ALL credit
 *   a citation spelling the reader skips    699 ids    0 lose ALL credit
 * </pre>
 *
 * <p>The first candidate <b>invents 58 units of debt</b> for tasks that are genuinely asserted —
 * wrong in the one direction the paragraph above says a ledger must not be wrong in, because a
 * message may legitimately assert two ids and taking only the first discards the second. The second
 * candidate costs nothing at all today, so the convention is free to adopt and the reader keeps
 * erring toward keeping a payment.</p>
 *
 * <p>So a reference is spelled <b>see SHnnn</b> (or "see also", or "cf.") and this skips it. That
 * cannot be enforced — the reader cannot know an intent that was never written down — but it does
 * not have to be remembered either: the two failures that fire when a citation is miscounted now
 * name the crediting file and the spelling, which is the moment the convention is learnable.</p>
 */
function assertedIn(body, java) {
  const found = new Set();
  let inTextBlock = false;
  for (const raw of body.split("\n")) {
    // SH731 — a reference, blanked before the id match so `see SH322` credits nothing. Deliberately
    // a short, explicit list of markers: a broad one ("per", "following", "as in") would start
    // swallowing subjects, and the measurement above says nothing is gained by reaching further.
    const line = raw.replace(CITATION, " ");
    const wasInBlock = inTextBlock;
    // Java only, and the reason is this file. A text-block fence is three double quotes, and
    // the FIRST version of this line contained one — inside its own regex — so `assertedIn`
    // flipped into block mode while reading itself, counted every comment below as code, and
    // reported two ids as asserted that nothing asserts. JavaScript has no text blocks, so
    // the toggle has no business running over a `.mjs` file; scoping it is both the fix and
    // the honest rule. Third time in this one task that a scanner was quietly wrong rather
    // than loudly broken, which is the whole argument for measuring before deciding.
    if (java && ((line.match(FENCE) ?? []).length) % 2 === 1) inTextBlock = !inTextBlock;
    if (!wasInBlock && isProse(line)) continue;
    for (const id of line.match(/\bSH\d+\b/g) ?? []) found.add(id);
  }
  return found;
}

/** The Java text-block fence, BUILT rather than written, so this source contains no fence. */
const FENCE = new RegExp(String.fromCharCode(34).repeat(3), "g");

/**
 * A reference to another task rather than a claim about this one (SH731).
 *
 * <p>Its own test proves it matches, because the failure mode of a rule like this is silence: a
 * regex that matched nothing would blank no citation, credit every id exactly as before, and pass
 * every test here that only checks the count did not move.</p>
 */
const CITATION = /\b(?:see also|see|cf\.?)\s+SH\d+\b/gi;

// SH740 — the private copy of this lived here, and three others lived elsewhere. It is now one
// function in support/source-text.mjs, which is what `@scans code` above promises.

/** A file with this reader's own declaration cut out, and every other file unchanged. */
function withoutDeclaration(body) {
  const start = body.indexOf(DECLARATION[0]);
  const end = body.indexOf(DECLARATION[1]);
  return start >= 0 && end > start ? body.slice(0, start) + body.slice(end) : body;
}

function walk(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    if (entry === "node_modules" || entry === "target") continue;
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...walk(path));
    else if (READABLE.has(entry.slice(entry.lastIndexOf(".")))) out.push(path);
  }
  return out;
}

test("SH527: every shipped task carries an assertion, or is on the declared debt", async () => {
  const shipped = await shippedIds();
  const { found, files } = await assertedIds();
  const elsewhere = new Set(Object.values(ELSEWHERE).flat());

  const debt = [...shipped].filter((id) => !found.has(id) && !elsewhere.has(id)).sort(byId);
  // Printed on every run, which is what makes the number a fact somebody sees rather than one this
  // file holds. §IX.36's own sentence: that number is the debt, and it only goes down when somebody
  // pays it.
  // SH642 — two numbers, because they are two different debts and one figure mispriced both.
  // The original list is work with NOTHING standing behind it. The revealed one is work whose
  // test exists and does not carry the id where this reader can see it: every one of those 88 is
  // named by a suite file, 63 of them within a dozen lines of an assertion. "124 in debt" read as
  // "124 tasks with no assertion", which was false of two thirds of it.
  const unnamed = debt.filter((id) => !Object.hasOwn(REVEALED, id)).length;
  console.log(
    `SH527: ${shipped.size} shipped, ${shipped.size - debt.length - elsewhere.size} asserted, `
      + `${debt.length} in debt, ${elsewhere.size} delivered in another repository `
      + `(${files} suite files read)`,
  );
  console.log(
    `SH642: of the debt, ${unnamed} have no suite naming them at all; `
      + `${debt.length - unnamed} are named by a suite and not by an assertion `
      + `(${CLOSE.length} of those beside one)`,
  );

  assert.deepEqual(
    debt,
    [...DEBT, ...REVEALED_IDS].sort(byId),
    "A defect closes with an assertion carrying its id — the assertion that would have CAUGHT it, "
      + "at the boundary it crossed, not a unit test of the fix's internals.\n"
      + "If a task you just shipped is in `+ actual`, that is this rule: add the assertion, and name "
      + "the id in it so this reader can find it.\n"
      + "If it genuinely cannot carry one, add it to DEBT with the reason — in a diff somebody "
      + "reviews. A ceiling would let one task pay the debt and the next spend it.\n"
      + "If it is in `- expected`, either somebody paid one off — delete the line — or a message "
      + "you just wrote merely CITES that id in passing. A reference is spelled `see SHnnn` and "
      + "this reader skips it (SH731); a bare `(SHnnn)` reads as a claim to have closed it.",
  );
});

test("SH527: the reader is not vacuous — it reads real suites and a real ledger", async () => {
  const shipped = await shippedIds();
  const { found, files, by } = await assertedIds();

  assert.ok(shipped.size > 400, `the ledger yielded ${shipped.size} shipped ids`);
  assert.ok(files > 200, `the suites yielded ${files} files`);
  // A reader that found no id at all would report every shipped task as debt and pass on a DEBT
  // list somebody had accepted. So two ids it must see, one per half of the walk, asserted apart so
  // a failure says which half went blind.
  assert.ok(found.has("SH516"), "the Java half of the walk found nothing: SH516 is asserted in "
    + "ShPageRenderTest and in no .mjs file");
  assert.ok(found.has("SH522"), "the JS half of the walk found nothing: SH522 is asserted in "
    + "cli/test/diff.test.mjs and in no .java file");
  assert.ok(found.has("SH527"), "and this file counts like any other suite outside its declaration "
    + "region — the assertion for SH527 is these very test names, so a reader that could not see "
    + "them would report itself as debt, which it did until the exclusion moved off the file");
  // SH731 — this is the assertion that fired for the SH322 mislabel, and it said only that a stale
  // entry existed. Naming the id and the file that credits it turns "somebody paid one off" into a
  // question with two readable answers, which is the whole difference between the two cases.
  const credited = [...DEBT, ...REVEALED_IDS].filter((id) => found.has(id))
    .map((id) => `${id} (credited by ${(by.get(id) ?? []).join(", ")})`);
  assert.deepEqual(
    credited,
    [],
    "and nothing on the declared debt is actually asserted: a stale entry here hides a real one.\n"
      + "Two ways this happens. Either the assertion was genuinely added — delete the debt entry — "
      + "or the file above merely CITES the id while asserting something else, the way SH722 did "
      + "(see SH322). A reference is spelled `see SHnnn` and this reader skips it (SH731).",
  );
});

/**
 * The range fixture ids live in, above anything this project will allocate.
 *
 * <p>SH787 — a synthetic id and a real one are the same characters, and this walk credits either.
 * So the separation cannot be detected; it has to be <b>reserved</b>, and then enforced from both
 * ends: nothing ships here, and a suite spelling an id the ledger does not know must be here.</p>
 */
const RESERVED_FROM = 9000;

/** Every id any governed file names — shipped, open, retired, or merely cited in a rationale. */
async function knownIds() {
  const ids = new Set();
  for (const file of ["docs/ROADMAP.md", "docs/CHANGELOG.md", "docs/IMPROVEMENTS.md"]) {
    const text = await readFile(join(ROOT, file), "utf8");
    for (const [, id] of text.matchAll(/\b(SH\d+)\b/g)) ids.add(id);
  }
  return ids;
}

/**
 * SH787 — the assertion that would have caught a fixture id being a real task's id.
 *
 * <h2>Why it is a reserved range and not a detector</h2>
 *
 * <p>`"SH3"` in a fixture array and `SH3` in an assertion message are the same eight characters on a
 * code line, which is all {@link assertedIn} can see. There is no reader that separates them, so the
 * rule has to be one a writer can follow and a lint can check: <b>a suite spells an id the ledger
 * knows, or an id at or above {@link RESERVED_FROM}.</b></p>
 *
 * <h2>What the measurement found, which is worse than the case that prompted it</h2>
 *
 * <p>SH786's fixture used `"SH3"` and this suite went red — that one was caught. The measurement
 * then found <b>six fixture ids in the SH9xx range</b>, across four files, chosen when they were
 * "far past anything real" and propagated by copying. The ledger's highest id was <b>SH788</b>. So
 * `SH900` was a hundred and twelve tasks away from shipping, and on the day it shipped it would have
 * arrived <b>pre-paid</b> by a fixture in `red-suites.test.mjs` — asserted, off the debt list, and
 * nothing anywhere would have said so. That is the silent case, it was scheduled, and no amount of
 * care at the moment of writing SH900 would have found it.</p>
 */
test("SH787: an id a suite spells is one the ledger knows, or is in the reserved fixture range", async () => {
  const known = await knownIds();
  const { by } = await assertedIds();
  const strays = [...by.keys()]
    .filter((id) => !known.has(id) && Number(id.slice(2)) < RESERVED_FROM)
    .sort(byId)
    .map((id) => `${id} (in ${[...new Set(by.get(id))].join(", ")})`);

  assert.deepEqual(
    strays,
    [],
    `an id below SH${RESERVED_FROM} that the ledger has never heard of is one of two things, and `
      + "both are defects.\n"
      + "A **fixture id**: move it into the reserved range. It is not safe where it is — it is only "
      + "safe UNTIL the counter reaches it, and then it silently pays that task's debt.\n"
      + "A **typo**: fix it. It credits nothing today, and credits the wrong task the moment the "
      + "digits happen to land on a real id.",
  );
});

/**
 * What to say when a governed file names a reserved id — which depends on *how* it names it.
 *
 * <p>SH790. There are two ways in and they need opposite fixes, and the first version of this guard
 * gave the reader only one of them: <i>move the range up before allocating here</i>. It fired on
 * the day SH787 shipped, and nobody had allocated anything. SH787's own changelog sentence spelled
 * the boundary in the obvious words, roadkeep reads an id-shaped token in a governed file as an id
 * that is spent, and the next task was derived past it — into the range that had just been
 * reserved. Following the message would have raised the reserve, left the sentence, and derived the
 * next task past the new boundary too.</p>
 *
 * <p>P6 turned inward: a 4xx here must carry `fix`/`allowed`, and an assertion message is the same
 * contract with the same reader. So the tell is computed rather than guessed — a roadmap or ledger
 * <b>line</b> carrying the id is allocation, and an id that appears only inside a sentence is a
 * wording fix.</p>
 *
 * @returns the message, or null where nothing was breached
 */
function reservedRangeBreach(allocated, spelledOnly) {
  if (allocated.length === 0 && spelledOnly.length === 0) return null;
  const parts = [
    `SH${RESERVED_FROM} and above is reserved for the ids fixtures are written with, so a governed `
      + "file naming one turns every such fixture into an assertion carrying a real id.",
  ];
  if (allocated.length > 0) {
    parts.push(
      `ALLOCATED — a line carries ${allocated.join(", ")}. The counter has genuinely reached the `
        + "reserve: raise RESERVED_FROM, and move the fixtures above it, before this range is "
        + "somebody's real work. `roadkeep renumber` moves a line, and it does NOT move the "
        + "section heading that repeats the id — that is `roadkeep section-amend --title`.",
    );
  }
  if (spelledOnly.length > 0) {
    parts.push(
      `SPELLED — no line carries ${spelledOnly.join(", ")}; a sentence merely says it. Do not touch `
        + "the range: reword the prose so the boundary is not an id-shaped token (\"above the "
        + "nine-thousandth\" rather than the number). Until it is reworded, roadkeep reads that "
        + "token as spent and derives the next task past it — which is how this fired the first "
        + "time, one command after the range was reserved.",
    );
  }
  return parts.join("\n");
}

test("SH787: and the reserved range stays reserved — the ledger never allocates into it", async () => {
  // The other end of the same rule, and the one that makes the first end mean something. Reserving a
  // range is a claim about the future; this is what holds the future to it. If a task ever ships as
  // SH9000+, every fixture in this repository becomes a payment on it at once.
  const reserved = (ids) => [...ids].filter((id) => Number(id.slice(2)) >= RESERVED_FROM).sort(byId);
  // "A line carries it" is what `openIds` and `shippedIds` already mean — a roadmap task line and a
  // ledger entry. Anything else `knownIds` sees is prose: a rationale sentence, a heading, a `why`.
  const carried = new Set([...reserved(await openIds()), ...reserved(await shippedIds())]);
  const named = reserved(await knownIds());

  assert.deepEqual(
    named,
    [],
    reservedRangeBreach(named.filter((id) => carried.has(id)), named.filter((id) => !carried.has(id)))
      ?? "unreachable: the message is only read when this list is not empty",
  );
});

test("SH790: the breach message names which of the two causes fired, and their fixes differ", () => {
  // The assertion that would have caught it. Not that a message exists — the old one existed — but
  // that the two causes produce different instructions, since the defect was one instruction given
  // for both. The prose case is the one that actually happened and the one the old text denied.
  const allocated = reservedRangeBreach(["SH9001"], []);
  const spelled = reservedRangeBreach([], ["SH9000"]);

  assert.match(allocated, /ALLOCATED/, "a line carrying a reserved id is allocation");
  assert.match(allocated, /raise RESERVED_FROM/, "and its fix is to move the range");
  assert.doesNotMatch(allocated, /reword/, "which is not a wording problem");

  assert.match(spelled, /SPELLED/, "an id only a sentence spells is not allocation");
  assert.match(spelled, /reword the prose/, "and its fix is the sentence");
  assert.doesNotMatch(spelled, /raise RESERVED_FROM/,
    "and telling this reader to move the range is what SH790 is: it raises the reserve, leaves the "
      + "sentence, and the next task is derived past the new boundary too");

  assert.equal(reservedRangeBreach([], []), null, "and nothing is said when nothing is breached");
});

test("SH630: a comment naming an id does not assert it, and an assertion message does", () => {
  // The assertion that would have caught it. SH356's ratchet shipped with a header explaining why
  // it holds one outcome and deliberately does not assert a cross-file rule, naming three other
  // ids to say so — and all three left the debt list on the next run. Nothing asserted them; a file
  // had said their numbers out loud.
  const prose = [
    "/**",
    " * Explains why SH9101 matters and why this does not assert it.",
    " */",
    "// SH9102 is mentioned here for context only",
    "const x = 1;",
  ].join("\n");
  assert.deepEqual([...assertedIn(prose, false)], [], "a file that only TALKS about a task asserts nothing "
    + "about it — and the old rule paid a debt for every sentence like that, so the better the "
    + "comment the more it paid");

  const asserted = [
    '  test("SH9103: the thing holds", () => {',
    '    assert.ok(value, "SH9104: and this is why");',
    "  });",
  ].join("\n");
  assert.deepEqual([...assertedIn(asserted, false)].sort(), ["SH9103", "SH9104"],
    "an id in a test name or an assertion message is exactly what this reader is looking for; a "
    + "rule that lost those would report the whole ledger as debt and read as a stricter one");
});

// SH630's own version of the rule above lived here: a hardcoded list of this file's fifteen
// illustrations, asserted not to be shipped. SH787 folded it in rather than leaving two readers of
// one idea (SH583) — the general lint covers every suite instead of this file, and both directions
// instead of one, so the list was a subset that also had to be maintained by hand. What it was
// protecting is worth keeping in words: the first version of these tests used SH333 as a
// plausible-looking example and paid that task's debt off, and this file asserts nothing whatever
// about SH333. SH731's illustrations make it sharper still — SH9109/SH9111/SH9113 are the ones the
// citation rule blanks, so had they become real this file would pay three debts by example and hide
// three more in the same breath.

test("SH630: a regex literal holding a quote cannot derail the scan", () => {
  // Not hypothetical, and the reason this rule holds no cross-line state. The first implementation
  // was a proper comment stripper, and `replication.conformance.mjs` contains a regex literal with
  // a double quote inside it: the scanner took it for a string opening and misclassified every line
  // after it in that file. It reported a plausible number and was wrong, which is the failure shape
  // a stripper has — it blinds the instrument instead of failing it.
  const source = [
    String.raw`    assert.ok(!/url\\("?/.test(css), "SH9105: the theme resolves");`,
    "    // SH9106 is only mentioned",
    String.raw`    assert.ok(true, "SH9107: still counted after the regex");`,
  ].join("\n");

  const found = assertedIn(source, false);
  assert.ok(found.has("SH9105"), "the line carrying the regex still asserts its own id");
  assert.ok(found.has("SH9107"), "and the scan is still correct AFTER it — the desync that made "
    + "this necessary swallowed everything downstream");
  assert.ok(!found.has("SH9106"), "while the comment between them asserts nothing");
});

test("SH731: a message that cites another task's id does not pay its debt", () => {
  // The assertion that would have caught it, at the boundary it crossed. SH722's lint ended its
  // failure message "(SH322)" as a citation of the rule it follows; this reader read it as a claim
  // to close SH322, dropped SH322 from the debt list, and the suite went red saying somebody had
  // paid it off. Written as the exact shape of that line, not a paraphrase.
  const citing = [
    '    assert.ok(ok, "SH9108: the guard holds — which is why this lint exists rather than one '
      + 'more repair (see SH9109)");',
  ].join("\n");
  const found = assertedIn(citing, false);
  assert.ok(found.has("SH9108"), "the subject of the message is still asserted — the citation rule "
    + "must not cost the assertion it is written beside");
  assert.ok(!found.has("SH9109"), "while the id it merely refers to is not: a citation is not an "
    + "assertion, and reading it as one retires a debt nobody paid");

  // Both spellings, because "see also" and "cf." are how the reference actually gets written half
  // the time and a convention that only covers one of them is a convention with a hole in it.
  assert.ok(!assertedIn('  ok(x, "SH9110: holds (see also SH9111)");', false).has("SH9111"));
  assert.ok(!assertedIn('  ok(x, "SH9112: holds (cf. SH9113)");', false).has("SH9113"));

  // And the direction that matters most: a bare mention is still a claim. Nothing about SH731
  // loosens the old rule, so a task that really is closed by a message keeps its credit.
  assert.ok(assertedIn('  ok(x, "SH9114: holds, as SH9115 required");', false).has("SH9115"),
    "a bare id in a message still counts — the citation spelling is opt-in, and making the "
      + "unmarked case mean 'reference' would invent debt for 58 ids that are genuinely asserted");
});

test("SH630: the revealed debt is held apart from the triaged debt", () => {
  // A named list, never a ceiling — and two lists rather than one, because every DEBT group carries
  // an SH529 verdict and none of REVEALED has been through that pass. Merging them would make
  // considered decisions indistinguishable from unexamined ones.
  const overlap = REVEALED_IDS.filter((id) => DEBT.includes(id));
  assert.deepEqual(overlap, [], "an id on both lists is counted twice and paid off once");
  assert.ok(REVEALED_IDS.length > 0, "and the list is not empty: SH630 revealed what prose was paying");
});

test("SH642: every revealed entry points at a file that still names it", async () => {
  // The pointer IS the triage: it says where the assertion probably already is, which turns paying
  // an entry off from a search into a one-line move. A pointer at a file that no longer mentions
  // the task is worse than none — it sends the next reader somewhere and wastes the trip.
  const { found } = await assertedIds();
  assert.ok(found.size > 0, "the walk read something");

  const named = new Map();
  for (const suite of SUITES) {
    // The same normalisation `assertedIds` does: an entry is a tree name, or a tree with a file
    // pattern (SH579's `shio-site/scripts`, whose suites sit beside the scripts they test).
    const { tree, only } = typeof suite === "string" ? { tree: suite, only: null } : suite;
    for (const path of walk(join(ROOT, tree))) {
      if (only && !only.test(path)) continue;
      const name = path.split(/[/\\]/).pop();
      named.set(name, (named.get(name) ?? "") + (await readFile(path, "utf8")));
    }
  }

  const rotted = [];
  for (const [id, file] of Object.entries(REVEALED)) {
    const body = named.get(file);
    if (!body) rotted.push(`${id} -> ${file} (no such suite file)`);
    else if (!new RegExp(`\\b${id}\\b`).test(body)) rotted.push(`${id} -> ${file} (no longer names it)`);
  }

  assert.deepEqual(rotted, [],
    "a revealed entry names the suite file that mentions the task, so paying it off is moving the "
      + "id into that file's assertion rather than hunting for one. A pointer nobody maintains is "
      + "a line the next reader trusts and should not (SH642)");
});

test("SH745: an open task the suite already asserts is printed, never failed", async () => {
  // The mirror of this file's own question, and the one nothing computed. SH47 was implemented,
  // proved by `ShStaticFileTenantIsolationIT`, and left open on the roadmap for blocks — found
  // only because the backlog shrank far enough that somebody read it.
  //
  // PRINTED, and that is the design rather than timidity. Failing here would fire on a practice
  // this project actively requires: SH448's shape is to write the assertion FIRST and red, and the
  // house rule is that a defect closes with the assertion that would have caught it. Both put an
  // open id in the suite legitimately, and nothing static can tell "asserted and passing, so it
  // shipped" from "asserted and failing, so it is being worked on". A list somebody reads is what
  // SH47 needed; a red build would have taught people to ignore it.
  const [open, { found, by }] = await Promise.all([openIds(), assertedIds()]);
  const already = [...open].filter((id) => found.has(id)).sort(byId);

  console.log(
    `SH745: ${open.size} open task(s), ${already.length} of them already asserted`
      + (already.length
        ? `\n  ${already.map((id) => `${id} <- ${(by.get(id) ?? []).join(", ")}`).join("\n  ")}`
          + "\n  Each is either shipped-and-unrecorded (roadkeep ship it) or an assertion written "
          + "first and still red (nothing to do)."
        : ""),
  );

  // What IS asserted is that both readers work. A report whose inputs came back empty would print
  // a reassuring zero for ever, which is the failure every instrument here is warned about — and
  // the reason this file already guards `shippedIds` the same way.
  // SH750 emptied the backlog, so zero open tasks is now a real state rather than a broken read.
  // What still has to fail is a reader that stopped parsing a roadmap that HAS work — checked
  // against the file's own task lines rather than against a number that can legitimately be zero.
  const taskLines = (await readFile(join(ROOT, "docs/ROADMAP.md"), "utf8"))
    // The task-line shape, not any bullet: the roadmap also holds the Non-goals list, and counting
    // those made "the backlog is empty" look like "ten lines this reader failed to parse".
    .split(/\r?\n/).filter((line) => /^-\s.*\*\*SH\d+\*\*/.test(line)).length;
  assert.ok(open.size > 0 || taskLines === 0,
    `the roadmap carries ${taskLines} task line(s) and this read yielded none, so the line shape `
    + "changed and this report cannot mean anything");
  assert.ok(found.size > 100, `the suites yielded ${found.size} asserted ids`);
});

test("SH642: the header group names ids the map actually holds", () => {
  // Two lists that describe one population drift, and this one would drift silently: an id in
  // HEADER and not in REVEALED just makes CLOSE one longer, which reads as progress.
  const stray = [...HEADER].filter((id) => !Object.hasOwn(REVEALED, id));
  assert.deepEqual(stray, [], "a header id the map does not hold inflates the cheap half by "
    + "subtraction, which is the one direction a debt count must not be wrong in");
  assert.equal(CLOSE.length + HEADER.size, REVEALED_IDS.length,
    "and the two groups partition the revealed list rather than overlapping it");
});


/**
 * SH820 — an entry that has been paid must leave the ledger, and only this notices.
 *
 * ## The drift a hand-kept list has and a computed one cannot
 *
 * `DEBT` and `REVEALED` are named lists, deliberately: a ceiling lets one task pay the debt and the
 * next spend it. The cost of a list is that it goes stale in a direction nothing above checks. The
 * main comparison asks *is every shipped id asserted or declared*, and an id that is **both** —
 * asserted by a suite and still sitting in the ledger — satisfies it twice over. It is silent, it
 * inflates the debt, and the inflation is what makes the number stop meaning anything.
 *
 * SH820 paid seven and removed seven by hand. Removing six would have gone green, and the seventh
 * would have been reported as debt for as long as anybody left it there — the same shape SH789
 * found one ledger along, where a declared red outlived its red for fifty green runs because the
 * only reader that could have said so excluded that gate.
 *
 * ## Why this is the assertion SH820 earns
 *
 * The rule (SH527) is that a defect closes with the assertion that would have caught it. What SH820
 * did was move ids into the assertions that would have caught their defects; what could go wrong
 * while doing that is paying an entry and forgetting the ledger, or the reverse — striking an entry
 * whose id no assertion actually carries, which is the more expensive mistake and is caught by the
 * main test the moment the ledger no longer excuses it.
 */
test("SH820: no ledger entry names an id a suite already asserts", async () => {
  const { found } = await assertedIds();
  const declared = [...new Set([...DEBT, ...REVEALED_IDS])].sort(byId);

  const paid = declared.filter((id) => found.has(id));

  assert.deepEqual(paid, [],
    "SH820: these ids are asserted by a suite AND declared as debt, so the ledger is charging for "
      + "work that is done. The main comparison cannot see it — an id that is both asserted and "
      + "declared passes on either count — so the entry sits there inflating the figure the whole "
      + "instrument exists to make meaningful.\nDelete the entry; the assertion naming the id is "
      + "the evidence it is paid.");

  // Non-vacuous in the one way that matters: this must be able to find a payment. `found` is the
  // real corpus, so a fabricated id proves nothing — an id the suite genuinely asserts does.
  assert.ok(found.has("SH820"),
    "SH820: the reader must credit this very test, or `paid` is empty because nothing is ever "
    + "found rather than because nothing is owed — which is the reading that made a debt of 124 "
    + "look like 124 unasserted tasks");
});


test("SH843: a declaration marker is balanced, so a stray start cannot swallow a file", async () => {
  // The failure this refuses is silent and in the retiring direction, like every other failure this
  // reader has had: `withoutDeclaration` cuts from `start` to `end`, so a `start` with no `end`
  // removes the rest of the file — every assertion below it stops counting and the debt figure
  // falls. Nothing else would notice, because a smaller debt is what everybody wants to see.
  const unbalanced = [];
  for (const suite of SUITES) {
    const { tree, only } = typeof suite === "string" ? { tree: suite, only: null } : suite;
    for (const path of walk(join(ROOT, tree))) {
      if (only && !only.test(path)) continue;
      const body = await readFile(path, "utf8");
      // The SAME reading `withoutDeclaration` does — first index of each — and not a count of
      // occurrences. A count is the obvious guard and is wrong here: this very file names the
      // start marker once more in the failure message below, so counting reports 2:1 on a file
      // whose region is perfectly closed. A guard that fires on its own prose is one somebody
      // silences.
      const start = body.indexOf(DECLARATION[0]);
      const end = body.indexOf(DECLARATION[1]);
      // No character class here on purpose: a `[/\\]` written through a shell heredoc loses its
      // escape and becomes an unterminated regex — twice in one session, which is SH782's finding
      // meeting the tool that writes the file rather than the template that builds the pattern.
      const name = path.split("/").pop().split("\\").pop();
      if (start >= 0 && !(end > start)) unbalanced.push(name);
    }
  }

  assert.deepEqual(unbalanced, [],
    "SH843: a `debt-declaration:start` with no `end` cuts everything after it out of the walk, so "
      + "the assertions below it stop paying and the debt silently falls. Close the region.");
});

test("SH843: a reason field in a named list credits nothing, and an assertion still does", async () => {
  // Derived, never a list of ids — and that is this task's own finding turned on it. The first
  // version named the three the measurement found (two in the handoff ledger, one in the
  // round-trip one) and so CREDITED all three from its own loop: a bare id on a code line, which
  // is exactly the payment being refused, committed in the act of refusing it. The debt figure did
  // not move, which is how it was caught — three ids lost a credit and the total stayed at 102.
  // A property cannot do that, and it cannot go stale either.
  const raw = await readFile(join(ROOT, "cli/test/handoffs.test.mjs"), "utf8");

  const whole = assertedIn(raw, false);
  const cut = assertedIn(withoutDeclaration(raw), false);

  assert.ok(whole.size > cut.size,
    "SH843: cutting the declaration must remove credits, or the markers are in the wrong place and "
      + "every id those reasons name still reads as asserted. A named list's reason is a string in "
      + "an object literal, so it is code by the not-a-comment rule above");
  assert.ok(cut.size > 0,
    "SH843: and it must not remove them all — the markers cut the LEDGER, not the file, so this "
      + "suite's own test names still pay. A rule that blanked the file would satisfy the line "
      + "above by deleting the evidence, which is the direction every failure of this reader takes");

  const { found } = await assertedIds();
  assert.ok(found.has("SH820"), "and an ordinary assertion message still pays, corpus-wide");
});
