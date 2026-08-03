# Turing indexing — published content becomes searchable (SH84)

Shio pushes what it publishes into [Turing](https://github.com/openviglet/turing) so a
Shio-built site has search. **Off by default**; an instance that has never been told about
Turing behaves exactly as one built before this existed — no thread, no dependency contacted,
no incident.

Package `com.viglet.shio.turing`. Four classes, and the split between them is the design:

| Class | Job |
|---|---|
| `ShTuringProperties` | `shio.turing.*` — the switches and the site routing |
| `ShTuringDocumentMapper` | one post → Turing's standard fields, enforcing "absent ≠ empty" |
| `ShTuringIndexService` | builds a `TurSNJobItem` (needs a session) and sends it (must not) |
| `ShTuringIndexListener` | the `AFTER_COMMIT` seam, and the incident when a push fails |
| `ShTuringConfig` | the one thread that talks to Turing |

## Compose, don't fork (P10) — this is Turing's contract, not a new one

The wire format is `TurSNJobItems` posted to `POST /api/sn/import`, built and sent by
**`turing-java-sdk`** (`TurSNJobUtils.importItems`) — the same artifact and the same call
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
  an error** — an instance that does not index is not a broken instance.
- **Routing is configuration, not a column.** Which SN site a Shio site indexes into is a
  *deployment* fact: staging and production hold the same content and feed different indexes.
  In the database it would be content, and an export/import would carry one environment's
  routing into another. Matched case-insensitively — the two names are typed by people into two
  different systems, and a mismatch would present as indexing that silently does nothing.
- **A site with no mapping and no `default-site` is not indexed.** Guessing an SN site name
  either creates documents in the wrong index or fails once per publish, forever.

## The invariant with no lint anywhere else: absent is not empty

`TurSNJobItem`'s own contract (Turing T381, Dumont's grounding rule D03) is that the attribute
map carries **only fields the source actually supplies** — an absent field is a *missing key*,
never a key mapped to `null`, `""` or an empty collection. Nothing refuses a document that
breaks it: not Shio, not Turing, not Solr. The symptom is a facet that counts documents having
no such value, and an `exists` filter that matches them.

Every field therefore goes through one private `put` in `ShTuringDocumentMapper`, so the rule
is enforced once instead of in eleven `if`s that each have to be right, and
`ShTuringIndexingTest.aFieldWithNoValueIsAMissingKeyAndNeverAnEmptyOne` asserts the property
over a fully-blank post rather than trusting the `put`s.

**The `"null"` trap applies here too.** The backend stores the literal string `"null"` for an
empty payload, and `readTree("null")` is a `NullNode` that passes every non-null check — the
same defect as the console's `widgetSettings` guard, one layer down. `readData` checks
`isNull()`.

## What is mapped, and why the mapping is fixed

Turing's standard field names (`TurSNFieldName`) carry the facts every post-type has: `id`,
`title`, `abstract` (the summary), `url`, `type` (post-type name), `site`,
`publication_date`, `modification_date`, `author`, `source_apps`. Everything else — the
user-defined fields of `data` — is **walked** and folded into `text`, HTML stripped with
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

`@TransactionalEventListener(AFTER_COMMIT)` on `ShPostLifecycleEvent` — the same event
`ShWebhookDispatcher` listens to, for the same reason: a rolled-back publish must not reach
the index, and a document indexed from an uncommitted transaction is a search result for a
page that does not exist. `PUBLISH` indexes the **`PUBLISHED`** row (the only state a search
index may serve); `DELETE` and — unless `de-index-on-unpublish=false` — `UNPUBLISH` de-index
by id.

Two things that look like style and are not:

- **`@Transactional(propagation = REQUIRES_NEW, readOnly = true)`.** Spring *refuses* a plain
  `@Transactional` on a `@TransactionalEventListener` ("must not be annotated with
  @Transactional unless REQUIRES_NEW or NOT_SUPPORTED") — the context fails to start, which is
  how it was found. And without a session the lazy `shSite`/`shPostType` reads throw from a
  thread nobody is watching.
- **The entity never crosses the thread boundary.** Building touches the entity, so it runs on
  the committing thread; sending must not, or a slow Turing is a slow publish. Hence
  `indexItem`/`deIndexItem` (build) and `send` (post) are separate methods — one method doing
  both has to pick, and the choice that compiles is the one that throws
  `LazyInitializationException` in production and never in a test that has a session open.

**`ShPostLifecycleEvent` gained `locale` (SH84)** for the reason the snapshot exists at all: a
de-index must name the locale the document was indexed under, and by the time a `DELETE`
listener runs the row is purged. A field the snapshot does not carry is a field no
`AFTER_COMMIT` listener can ever recover.

## One thread, bounded queue, and the incident

`ShTuringConfig` gives the sender a **single** worker. Not throughput management — *ordering*:
publish-then-unpublish must arrive in that order, and a pool would let the de-index overtake
the index and leave a withdrawn page searchable, with no error anywhere because both calls
succeeded. Turing's own import hands the job to a JMS queue and returns, so one thread is not
the bottleneck. The worker is a daemon and times out when idle, so an instance that never
indexes carries no thread.

The queue is **bounded** (1000): with Turing unreachable, an unbounded queue turns another
system's outage into this instance running out of memory. Bounded means a long outage *drops*
changes — the honest failure, because the two alternatives are worse (`CallerRunsPolicy`
blocks the editor's publish on a dead third party; discarding silently makes a stale index
indistinguishable from a current one).

Every failure lands as an **`ShAgentIncidentLog.Kind.INDEX`** incident, so
`GET /api/v2/agent/diagnostics` and the SH144 change feed report it (P9 — a log line is not a
surface an agent has). Its own kind rather than `WEBHOOK` because the remedy differs: a webhook
failure means a front end may serve a stale page, this one means search will not return the
page at all. Three distinct messages — the queue was full, Turing did not accept the job, the
call errored — and a `frames` value **only** on the last, because the other two completed and
there is no exception of ours to point at (SH190: absent beats empty).

## Not done here

- **No reindex-on-demand.** Turning indexing on for an instance with existing content indexes
  nothing until each post is next published. → **SH362**
- **No `TurSNJobAttributeSpec`**, so facets over post-type fields are not expressible. → the
  spec-per-post-type task, once somebody needs one.
