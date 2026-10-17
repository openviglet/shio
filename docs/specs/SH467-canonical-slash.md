# SH467: the canonical slash: where the declared spelling is stored, and what has to agree

**Status: shipped.** SH583 landed first, then SH467. What the tree still cannot say is
filed as **SH585**, a post's file path in `shio/content/**` *is* its URL, so the
projection has no filename for the slash, which is why the declared spelling is honoured
on **create only**.

**Kept because the scoping was done twice and the second pass reached a different answer
than the first**, neither is obvious from the tree, and the two rejected options below
are the part a reader would otherwise re-propose.

## The defect, restated by measurement

`openviglet.github.io` serves `/model-catalog/` with a **200** and `/model-catalog` with a
**301** to it. The replica does the exact mirror: `/model-catalog` is 200 and
`/model-catalog/` 301s to it.

It was filed as a cost, every captured link, sitemap entry and bookmark pays a 301. It is
not. On that replica `./app/main.js` resolves to `/model-catalog/app/main.js` at the source
and to **`/app/main.js`** at the replica, because a document-relative reference resolves
against the URL the page is *served at*. `shio verify` reports it as
`page-reference-broken`, and it is the only reference error on an otherwise clean
authorable replica of that site. Every document-relative reference on a directory-style
page lands one level too high.

This is SH512's shape one phase along: the capture resolves relative references correctly,
and the delivery has the slash inverted.

## Where the slash actually dies

Two lines, not thirteen.

| path | line | what happens |
|---|---|---|
| `apply` (a desired-state document, this is what `convert` sends) | `ShAgentDesiredStateService:162` | `ShAddress.post(site, post.url())` → `normalizePath` collapses it |
| `post.upsert` (a batch op) | `ShAgentBatchService:1098` | `setFurl(created.getId(), address.path())`, the path is already collapsed |

`convert.mjs:814` does pass `url: page.path` with the slash intact. It reaches
`ShAgentDesiredState.Post.url()` intact. It dies at the `ShAddress.post` call and nowhere
else.

## The decision: the declared spelling is neither identity nor content

Two options were on the table and both are wrong.

### Not a fourth component on `ShAddress`

`ShAddress` is a `record`, value equality. A fourth component makes `post:S/shio` and
`post:S/shio/` **two unequal addresses for one post**. That is the P5 identity split
§XVII.44 already warns about, and it does not avoid the construction sites, it doubles
them: each one now has to decide what to put in the new slot.

`normalizePath`'s own javadoc gives the reason it collapses:

> Trailing slashes are dropped (but the root stays `/`), otherwise `/blog` and `/blog/`
> would hash differently and break the digest's stability.

The digest hashes the **address**. Leaving the address collapsed keeps that argument
intact, which is the argument *for* separating the stored `furl` from the address rather
than for teaching the address about slashes.

### Not `data.furl` on the post payload

The `data.furl` that exists is **`ShSite`'s**, `ShAgentBatchService:698`, inside
`siteUpsert`, writing `ShSite.setFurl`. There is no `data.furl` on a post write, and there
cannot casually be one: **SH168 refuses any `data` key the post-type does not declare**.
Using it would mean carving an exception into the one guard whose entire point is that it
has no exceptions.

### The third door: a write-shaped field, like `folder`

The declared spelling is a property of the **write**: it says how the source served this
page. That is the same kind of fact `folder`, `locale` and `translationOf` already are, and
`ShAgentBatch.Op` already carries five of them beside `address`:

```java
public record Op(String op, String address, String type, String folder, String to,
                 JsonNode data, String when, String locale, String translationOf) { … }
```

**SH348 is the precedent for the shape**: it added `translationOf` as a tenth component and
kept an eight-field compatibility constructor immediately below. Do the same.

`ShAgentDesiredState.Post` needs nothing new, it already has `url`, with the slash. What
changes is that `ShAgentDesiredStateService:162` stops throwing the spelling away when it
derives the address from it.

Two facts make the storage side nearly free:

- **`ShPostUnifiedWriteService.normalizeUrl` already preserves a trailing slash.** It is
  `furl.startsWith("/") ? furl : "/" + furl`: it guarantees the *leading* slash and
  nothing else. Hand `setFurl` a `/shio/` and `/shio/` is what is stored. **The storage
  layer needs no change at all.**
- `setFurl` itself needs one line: prefer the declared spelling over `address.path()`.

## The real work is the reads, and that is SH583

Storing a trailing slash is cheap. Being *found* afterwards is not, and this is the part
that was under-scoped.

The post-`furl` lookup surface, measured at `077b115`: **13 call sites across 7 classes**,
all comparing `furl` by exact equality through
`findByShSite_IdAndFurlAndState[AndLocale]`.

    ShPageRenderService      3      ShCdaService            2
    ShObjectWriteService     2      ShAgentBatchService      1 (+2 via furlCandidates)
    ShAgentVerifyService     2      ShAddressResolver        1 (+2 via furlCandidates)
    ShAgentRenderService     2

And the tolerance that is supposed to bridge the two spellings is **not one function**. It
is five places, four of them private and none shared:

| where | what it is |
|---|---|
| `ShAddress.normalizePath` | the collapser, the identity form |
| `ShAddressResolver:364` `furlCandidates` | private |
| `ShAgentBatchService:1946` `furlCandidates` | private, a duplicate of the above |
| `ShCdaService:472` `urlCandidates` | private, a third name for the job |
| `ShPageRenderService.canonicalSlashRedirect` | SH467's own shipped route half |

Today the duplication is harmless, because nothing is ever stored with a trailing slash so
all five agree by accident. **The moment SH467 stores one, they have to agree on purpose**,
and the failure mode is a page that renders in `/preview`, 404s on the CDA, and verifies
clean, because three different readers disagreed about which spellings match.

`normalizePath`'s javadoc predicted exactly this:

> a second normalization written next to either of them would eventually disagree with this
> one, and the symptom would be a lint that reports a defect that is not there.

So SH583 lands first (SH322/SH338: when the fix is "and whatever a grep turns up", the grep
is the deliverable and it ships as a lint before the fixes). One candidate generator, every
post-`furl` comparison routed through it, and a lint that fails on a new bypass.

Folder `furl`s (`findByParentFolderAndFurl`, `findByShSiteAndFurl`) and the site `furl`
(`findByFurl`, 5 sites) are a **different namespace** and out of scope here, name them in
the lint's exclusions rather than leaving a reader to wonder.

## Testing it

`-DskipITs=false`, and **start Docker first**, but the risk here is smaller than the rule
implies. SH467 changes no schema and no tenancy. The two ITs that silently skip without a
daemon are `ShPostgresSchemaIT` and `ShMariaDbSchemaIT`
(`@Testcontainers(disabledWithoutDocker = true)`); the CDA and delivery ITs that actually
cover this path are among the eight that do not need one. Run with Docker up anyway, since
a false green is cheap to avoid and expensive to discover.

The assertion that closes SH467, per SH527: a page whose source is canonically
`/x/` is served at `/x/`, `/x` 301s to it, and a `./y.js` on that page resolves to
`/x/y.js`. At the boundary it crossed, naming the id.

**What shipped:** `ShAgentCanonicalSlashTest`, declare `/model-catalog/` through `apply`
and the row carries it, the address stays collapsed, a second apply converges, and a
declared url that is not a spelling of its address is ignored. It pairs with
`ShRenderRedirectTest.theCanonicalFormIsWhicheverTheContentDeclares`, whose `/docs/`
fixture was hand-persisted with a comment saying it was what this half needed, the two
together are the claim above.
