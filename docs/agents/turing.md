# Turing indexing: published content becomes searchable (SH84)

Shio pushes what it publishes into [Turing](https://github.com/openviglet/turing) so a
Shio-built site has search. **Off by default**; an instance that has never been told about
Turing behaves exactly as one built before this existed, no thread, no dependency contacted,
no incident.

Package `com.viglet.shio.turing`. Four classes, and the split between them is the design:

| Class | Job |
|---|---|
| `ShTuringProperties` | `shio.turing.*`, the switches and the site routing |
| `ShTuringDocumentMapper` | one post → Turing's standard fields, enforcing "absent ≠ empty" |
| `ShTuringIndexService` | builds a `TurSNJobItem` (needs a session) and sends it (must not) |
| `ShTuringIndexListener` | the `AFTER_COMMIT` seam, and the incident when a push fails |
| `ShTuringConfig` | the one thread that talks to Turing |

## Compose, don't fork (P10): this is Turing's contract, not a new one

The wire format is `TurSNJobItems` posted to `POST /api/sn/import`, built and sent by
**`turing-java-sdk`** (`TurSNJobUtils.importItems`): the same artifact and the same call
Dumont's `DumTuringIndexingPlugin` indexes through. Shio therefore speaks the vocabulary the
ecosystem already has, and a change to it arrives by bumping `turing.version` in the parent
pom rather than by somebody remembering there is a second copy. Declaring the job shape here
would have been that second copy.

The dependency is `compile`, not `optional`: the job vocabulary is the contract. What is
optional is the *behaviour*, and that is a property, not a classpath.

## Configuration

```properties
shio.turing.enabled=true                 # default false — see below
shio.turing.url=http://localhost:2700
shio.turing.api-key=<key>
shio.turing.default-site=my-sn-site      # SN site for any Shio site not named below
shio.turing.sites.Cafeteria=cafeteria-sn # Shio site name → Turing SN site name
shio.turing.source-app=shio              # the `source_apps` stamp
shio.turing.de-index-on-unpublish=true
```

- **`enabled` defaults to `false`**, unlike the SH12 webhook switch, and the difference is
  deliberate: indexing sends content to *another system*, and an instance that starts pushing
  every publish to a URL nobody configured is P7's exact prohibition.
  `ShTuringIndexingTest.indexingIsOffUntilSomebodyTurnsItOn` pins it, because it is one word to
  change and no other test would fail.
- **Three independent off-switches**, each answering a different question: `enabled` (does this
  instance index), a configured `url` (does it know where), a resolved SN site (does *this*
  site route anywhere). All three are checked before anything is built, and **none of them is
  an error**, an instance that does not index is not a broken instance.
- **Routing is configuration, not a column.** Which SN site a Shio site indexes into is a
  *deployment* fact: staging and production hold the same content and feed different indexes.
  In the database it would be content, and an export/import would carry one environment's
  routing into another. Matched case-insensitively, the two names are typed by people into two
  different systems, and a mismatch would present as indexing that silently does nothing.
- **A site with no mapping and no `default-site` is not indexed.** Guessing an SN site name
  either creates documents in the wrong index or fails once per publish, forever.

## The invariant with no lint anywhere else: absent is not empty

`TurSNJobItem`'s own contract (Turing T381, Dumont's grounding rule D03) is that the attribute
map carries **only fields the source actually supplies**, an absent field is a *missing key*,
never a key mapped to `null`, `""` or an empty collection. Nothing refuses a document that
breaks it: not Shio, not Turing, not Solr. The symptom is a facet that counts documents having
no such value, and an `exists` filter that matches them.

Every field therefore goes through one private `put` in `ShTuringDocumentMapper`, so the rule
is enforced once instead of in eleven `if`s that each have to be right, and
`ShTuringIndexingTest.aFieldWithNoValueIsAMissingKeyAndNeverAnEmptyOne` asserts the property
over a fully-blank post rather than trusting the `put`s.

**The `"null"` trap applies here too.** The backend stores the literal string `"null"` for an
empty payload, and `readTree("null")` is a `NullNode` that passes every non-null check, the
same defect as the console's `widgetSettings` guard, one layer down. `readData` checks
`isNull()`.

## What is mapped, and why the mapping is fixed

Turing's standard field names (`TurSNFieldName`) carry the facts every post-type has: `id`,
`title`, `abstract` (the summary), `url`, `type` (post-type name), `site`,
`publication_date`, `modification_date`, `author`, `source_apps`. Everything else, the
user-defined fields of `data`, is **walked** and folded into `text`, HTML stripped with
jsoup.

- **Walked, not named**: post-types are user-defined (SH16), so a post-type's fifth field has
  to become searchable the day somebody adds it, with no mapping edit.
- **Markup stripped**: indexing a rich-text field verbatim makes a search for `div` match
  every page on the site.
- **Numbers are skipped**: a number is a value, not prose, and as text it matches nothing
  anybody searches for. A post-type that needs a numeric facet needs a
  `TurSNJobAttributeSpec`, which is a decision about the *index* and a separate task.
- **A per-attribute mapping UI was rejected**: it would be a second schema language for what
  SH16's post-types already describe.

## The seam, and which thread does what

`@TransactionalEventListener(AFTER_COMMIT)` on `ShPostLifecycleEvent`, the same event
`ShWebhookDispatcher` listens to, for the same reason: a rolled-back publish must not reach
the index, and a document indexed from an uncommitted transaction is a search result for a
page that does not exist. `PUBLISH` indexes the **`PUBLISHED`** row (the only state a search
index may serve); `DELETE` and (unless `de-index-on-unpublish=false`) `UNPUBLISH` de-index
by id.

Two things that look like style and are not:

- **`@Transactional(propagation = REQUIRES_NEW, readOnly = true)`.** Spring *refuses* a plain
  `@Transactional` on a `@TransactionalEventListener` ("must not be annotated with
  @Transactional unless REQUIRES_NEW or NOT_SUPPORTED"), the context fails to start, which is
  how it was found. And without a session the lazy `shSite`/`shPostType` reads throw from a
  thread nobody is watching.
- **The entity never crosses the thread boundary.** Building touches the entity, so it runs on
  the committing thread; sending must not, or a slow Turing is a slow publish. Hence
  `indexItem`/`deIndexItem` (build) and `send` (post) are separate methods, one method doing
  both has to pick, and the choice that compiles is the one that throws
  `LazyInitializationException` in production and never in a test that has a session open.

**`ShPostLifecycleEvent` gained `locale` (SH84)** for the reason the snapshot exists at all: a
de-index must name the locale the document was indexed under, and by the time a `DELETE`
listener runs the row is purged. A field the snapshot does not carry is a field no
`AFTER_COMMIT` listener can ever recover.

## One thread, bounded queue, and the incident

`ShTuringConfig` gives the sender a **single** worker. Not throughput management, *ordering*:
publish-then-unpublish must arrive in that order, and a pool would let the de-index overtake
the index and leave a withdrawn page searchable, with no error anywhere because both calls
succeeded. Turing's own import hands the job to a JMS queue and returns, so one thread is not
the bottleneck. The worker is a daemon and times out when idle, so an instance that never
indexes carries no thread.

The queue is **bounded** (1000): with Turing unreachable, an unbounded queue turns another
system's outage into this instance running out of memory. Bounded means a long outage *drops*
changes, the honest failure, because the two alternatives are worse (`CallerRunsPolicy`
blocks the editor's publish on a dead third party; discarding silently makes a stale index
indistinguishable from a current one).

Every failure lands as an **`ShAgentIncidentLog.Kind.INDEX`** incident, so
`GET /api/v2/agent/diagnostics` and the SH144 change feed report it (P9, a log line is not a
surface an agent has). Its own kind rather than `WEBHOOK` because the remedy differs: a webhook
failure means a front end may serve a stale page, this one means search will not return the
page at all. Three distinct messages, the queue was full, Turing did not accept the job, the
call errored, and the middle one is the only one with **no** `frames`, because there the SDK
completed and Turing refused: nothing of ours threw, so there is no stack to point at
(SH190: absent beats empty). The other two each have a real exception.

## A failed push is retried before it is reported (SH912)

SH84 recorded the failure and stopped. Nothing acted on the record, and the remedy string told a
*person* to re-publish, so a thirty-second Turing restart during a publishing session became a
permanent hole in the index, closed only by whoever next read the diagnostics feed.

`ShTuringIndexListener.sendWithRetries` now attempts a push **`shio.turing.max-attempts` times**
(default 3), waiting `shio.turing.retry-backoff` (default 1s) before the second and doubling before
each one after it. Both a clean refusal and a thrown connection error are retried, the restart
this exists for surfaces as the latter, so retrying only the former would miss the whole case.

**The wait happens on the worker, and re-queueing would be wrong.** A retry sent to the back of the
queue lets a later de-index overtake the index it is retrying, which leaves a withdrawn page
searchable, the exact failure the single thread exists to prevent. Blocking the one worker keeps
the ordering by construction. It costs a publish nothing: the committing thread returned long
before, and what a long outage does is fill the bounded queue, which is already decided and already
reported.

**One incident, recorded at exhaustion, never per attempt.** That is what makes a succeeded retry
*silent*: there is no stale incident sitting in the diagnostics window contradicting an index that
is in fact current. It is also why an exhausted push leaves one incident rather than three;
recording inside the loop would coalesce into a single `Incident` with `count() == 3`, which reads
as three failed publishes rather than one push retried twice. The message carries `(3 attempts)` so
the bound is visible, and says nothing about attempts when retrying is off.

**A restart does not keep pending retries, deliberately.** The queue is in memory and the retries
are with it. Persisting them would be a new durability guarantee in the one subsystem that
purposely makes none (bounded queue, drops on overflow, daemon worker) and a durable store would
have to survive the same outage it exists for, drain in order, and reconcile against posts that
changed while it waited. That is a reindex, and a reindex is SH413. What makes the loss acceptable
is that the index is recoverable from content: the page is still published, the incident still says
to re-publish, and SH413 covers the general case. The retry absorbs a restart; it is not a queue.

## A post-type field can declare the Turing field it indexes into (SH914)

The fold into `text` answers "does this word appear". It cannot answer "tuition under 5000", and
`collect` skips numbers and booleans **on purpose**, a number indexed as prose matches nothing
useful, so a school filtering by level, campus or tuition had those values in *no field at all*.

**The declaration lives on the attribute**, as three nullable columns on `ShPostTypeAttr`
(`indexed`, `turing_field`, `turing_type`). The alternative was a list on `ShPostType` naming
attributes by name, which is what the acceptance's "a declaration naming a field the post-type
lacks fails at save" implies, and it was rejected. A second surface describing SH16's model is the
exact thing the mapper's javadoc refuses, and naming a field by string makes the broken state
**representable**, so it then has to be validated. On the attribute it cannot be written down.

What save-time validation is left is real and narrower, in `ShTuringFieldDeclaration`: an indexed
field with no type, an unknown type, two fields claiming one Turing name, a name colliding with one
of `TurSNFieldName`'s own (it would overwrite the title every document depends on), and a
`turingType` set with `indexed` unset, refused rather than ignored, because ignoring it means a
facet that never appears and nothing saying why. Judged at **save**, since a broken declaration is
free until somebody publishes and then costs a document quietly missing the field a facet counts.

**Both halves or neither.** A value in a field of its own *and* a type Turing knows: an untyped
number is a string that sorts "10" before "9". `ShTuringDocumentMapper.specs` sends a
`TurSNJobAttributeSpec` per declared field with the job, rather than the schema being configured by
hand in Turing's console, a schema kept in two places is a schema that disagrees. `ARRAY` and
`CURRENCY` are deliberately not offerable: multiplicity is already the widget's business, and a
currency carries a unit this model has nowhere to put.

**Refusing beats coercing.** A `DOUBLE` field holding "ask us" is omitted, not sent as `0`, a `0`
would make that post answer an "under 5000" filter, a wrong result presented as a right one. The
same rule keeps absent-is-not-empty intact: a declared field the post left blank is a *missing key*.
And a declared string still folds into `text` as well, because removing it would make a page stop
matching a full-text search the day somebody added a facet to it.

**Nothing changes for a post-type that declares nothing**: no spec is sent, the wire shape is
identical, and `cli/src/post-types-io.mjs` omits all three keys when unset (the one exception to its
materialize-every-key rule) so no existing `shio/post-types/*.json` churns on the next pull.

## An attachment's text comes from Turing's OCR, not from a parser here (SH913)

A published PDF used to be indexed by title, URL and whatever its `data` JSON held, its bytes were
never read. A school publishing its rules and its fee table as attachments had documents findable
by name that answered nothing, and a chat grounded on the site could not cite them.

**The fix that would have been a fork:** `tika-core` and `tika-parser-pdf-module` are already
declared in `shio-app/pom.xml` and, before this, used by nothing. Parsing locally was one import
away. **Turing already has an OCR API**, `POST /api/ocr/file`, reached by `TurOcr` in the same
`turing-java-sdk` this package already indexes through, and it is strictly more capable: the engine
behind it is Turing's configuration, so a scanned document Tika reads as an empty page comes back
with text. Shio sends the bytes and takes `TurFileAttributes.getContent()`. Same bargain
`ShTuringIndexService` already made for the wire format: a change to how Turing extracts reaches
Shio by bumping a version.

`ShTuringTextExtractor.enrich(item, file)` is the one call both index paths share, the
`AFTER_COMMIT` listener and **SH413's reindex**, which reaches `indexItem`/`send` directly and would
otherwise re-index an already-published library by name only, which is the defect itself.

**Where each step runs.** `attachmentOf(post)` resolves the path on the **committing thread**:
`ShFileStorageService.resolve` reads the tenant, and the index worker has none until
`ShTenantHandoff` restores it. Only a `Path` crosses the boundary, the entity still stops there,
as everything else does. Extraction then happens on the index worker, before the send.

**The timeout is Shio's, and it has to be.** `TurOcr` hardcodes a **five-minute** socket timeout and
offers no way to change it. Five minutes would hold the single index worker, the one that keeps
publish-then-unpublish ordering, and stall every queued change behind one large PDF. So the call is
made on a worker of its own and waited for with `shio.turing.extract-timeout` (default 60s). On
expiry the document is indexed with metadata and no text: late text is worth less than a blocked
queue. The abandoned call is left to TurOcr's own five-minute mark, because cancelling the `Future`
would not close the socket and waiting for it to is the stall being avoided.

**Every failure still indexes the metadata.** `TOO_LARGE`, `MISSING` and `FAILED` leave the item
exactly as the committing thread built it and let the send proceed, an attachment findable by name
beats one not indexed at all, which is what an exception one line before the send would cost.
`NOT_EXTRACTED` is a configuration answer, not a degradation, and is the one outcome that records
nothing: reporting it would put every image on the site in the diagnostics window.

Config: `extract-text` (default true, switching indexing on is the consent, and the file is already
public), `extract-types`, `extract-max-size` (32 MB, `0` for none), `extract-timeout`.

## What an agent can see: `searchIndexing` in the manifest

The one thing about this subsystem that is *not* a deployment detail is **whether it is on**,
and that is P1's question rather than an operator's: an agent that publishes a page and is
asked whether the site's search works has no call to make here, indexing has none, by
design, so the fact has to arrive in the manifest with the other instance facts.
`GET /api/v2/agent/manifest` reports `searchIndexing`, read from
`ShTuringIndexService.active()` and not from `shio.turing.enabled`, because "does this
instance index" is the property **and** a URL together; copying that rule into the manifest
would report `true` on an instance that indexes nothing.

**It is not the same claim as `search`.** That flag is SH52's in-DB `LIKE` predicate, which is
always compiled in, reading it as "search works" is exactly the wrong conclusion for a
Turing-backed site, and the two flags sitting next to each other is what stops an agent
drawing it. `ShAgentP1ConformanceTest` classifies the new key as an `INSTANCE_FACT`, so the
day somebody gives indexing an operation, that classification has to change with it.

## Not done here

- **No reindex-on-demand.** Turning indexing on for an instance with existing content indexes
  nothing until each post is next published. → **SH413**
- **No `TurSNJobAttributeSpec`**, so facets over post-type fields are not expressible. → the
  spec-per-post-type task, once somebody needs one.

## Indexing what was already published (SH413)

SH84 indexes `AFTER_COMMIT` on a publish. That is the right seam and it covers exactly one case:
content published *after* indexing was switched on. An operator switching it on is normally doing so
for a site that **already exists**, and for them the index stayed empty with no error anywhere, the
honest failure of SH84's own design, and the one that reads as "the integration does not work".

**`site.upsert` with `data.reindex:true`** indexes a site's already-published posts, through the
same `ShTuringIndexService.indexItem` the listener uses, one document mapping, not two that can
drift, because a backfilled post and a freshly-published one must be the same document or search
results depend on which path wrote them.

**Bounded, and the bound is the point.** `data.limit` (200, max 1000) pages one call and
`data.from` resumes. SH84's sender is single-threaded and already reports a queue-full incident when
jobs arrive in bulk, so ten thousand posts becoming ten thousand queued jobs is not a slow backfill,
it is an **outage of the publish path**, and the thing this exists to help would be broken by it
running. The reply says how many were sent, how many were not accepted, and how many are left with
the offset to resume at, because a silent truncation reads as "indexed everything" (P9).

**It is a data key and not a twelfth op name, and an instrument decided that.** It was written as
`search.reindex` first; `ShAgentOpsReachTest` refused it, the op vocabulary is priced at 600
estimated tokens and a twelfth name took it to 659, and that assertion says what to do in as many
words: *"a reason to re-open the surface choice, not to raise the number quietly."* SH326 had
already made the identical trade, folding a repair verb into `render.provision` as
`data.reconcile`, because a vocabulary is paid by every caller that reads it (P3). So the shape is
"make this site as declared, and make its search correct", asked of a new site and of an old one.

Three smaller decisions:

- **Reported, not refused, when the instance does not index.** An agent running a standard build
  sequence against an instance with no Turing should not have its batch fail; `searchIndexing` in
  the manifest is what tells it beforehand.
- **A reindex that sent nothing leaves the op a `noop`**, because nothing moved. One that *sent* is
  an `update` even if every send was refused, a caller told "noop" about a run that tried and
  failed would read it as "already indexed".
- **The key is read on the create branch too**, where it is almost always a no-op line. Refusing it
  there would make `data.reindex` mean two different things depending on whether the site existed.

`ShAgentSearchReindexTest` runs it for real against a **dead port**, so `send` genuinely fails and
the report has to be honest about it. That test also **costs no Spring context**: written first with
property overrides, it was priced at one of the two slots left under the cache ceiling, so it
mutates the `ShTuringProperties` bean and restores it instead (SH315 permits sharing for a test that
only reads its own fixtures).

## Is this page findable: the question that had no surface (SH1063)

`searchIndexing` is a live manifest feature whose only surfaces were a properties class, a listener
and an incident log entry: no controller, no console page, nothing in the CLI. On an instance with
indexing **on**, nobody could answer the question search exists for, and a push that failed after
commit was recorded only as an incident nobody was looking at.

**`/agent/read?include=index`** answers it for an address: `enabled`, the `searchSite` this page's
content belongs in, whether it `indexed` (published *and* routed *and* switched on), and the last
indexing failure recorded against it. The three are different answers to different failures,
switched off is a **decision**, routing nowhere is the **defect that raises no error**, and a
recorded failure is the push that happened and did not land. A draft reads as not indexed rather
than as broken, because only published content is sent.

**`verify` gains `index-unroutable`**, under `checks=routes` since it asks the same kind of question
(is this reachable) and needs no fetch: indexing is on, a site has published content, and it maps to
no search site, so every push it would make is never made and nothing says so. A **warning**: the
content is right and the configuration is not. Silent when indexing is off (then nothing routes
anywhere, and that is a decision) and silent for a site with nothing published.

**The routing table is one read, on four surfaces.** `GET /api/v2/agent/index` (`?format=terse`
for lines) answers whether indexing is on, where it points, and every site with the search site it
resolves to plus how many published pages it has, and `ShTuringIndexStatusService` is the only
place that computes it, because three readings of "which search site does this go to" would be
three chances to disagree with the listener that actually routes. On top of that one read:
`shio://index` for MCP, `shio index status` for a terminal (exit **1** when some site publishes
into nothing, so a script can act on it) and the console's card on the site page, which stays
**silent when indexing is off**: a card explaining a feature nobody turned on is noise on every
site page. `shio index reindex --site X [--dry-run]` rides SH413's `site.upsert data.reindex`
rather than adding a second way to do it.

**What is deliberately not here yet:** the *last indexed instant*. Nothing writes one, the listener
records failures and no success, so reporting it would mean a new column and a write per publish,
which is a cost to argue for with a measurement rather than to slip into a read. The read says what
is knowable today, and says nothing where nothing is known. `ShIndexStatusTest` drives both halves,
borrowing the property-mutation shape above so it costs no context.
