---
title: Letting an agent in
description: "What stops an agent from surprising you: draft-default writes, dry runs, confirm tokens, the review queue, attribution, the trash, and the gaps that are still open."
sidebar:
  label: Letting an agent in
---

An agent that can publish is an agent that can embarrass you in front of your customers.
So the first question about an agent-native CMS is not *what can it build*: it is *what
can it do to my live site without asking*.

The short answer: **nothing.** An agent's writes land as drafts, publishing is a
separate permission it may not have, a delete needs a token that describes exactly what
it is about to remove, and everything it did is grouped into a run you can read and
approve, or revert. The longer answer, including the parts that are **not** yet
covered, is this page.

---

![Curation: a draft, the review queue, the Visual Editor, publishing, the trash](/docs/img/diagrams/shio-curation-flow.svg)

## The invariant

**The agent path is never a privilege escalation.** An agent's credential does not
reach anything a curator's session cannot, and after the safety work it is not a
privilege *superset* either.

It also cuts the other way, which is the part worth trusting: where the two surfaces
disagree, **the stricter one is correct**. When the agent path turned out to enforce a
permission the console did not, the console was fixed: not loosened to match.

---

## The credential is not an administrator

An agent runs on an API key of **`AGENT` scope**, and that scope is deliberately narrow:

| It may | It may not |
|---|---|
| Write on `/api/v2/agent/**` and `/mcp` | Write anywhere else, on the Content Delivery API it is **read-only** |
| Read drafts (every agent read is draft-preferred) | Publish, unless `mayPublish` is granted on the token |
| Be scoped to named sites, carry an expiry and a rate limit | Outlive the expiry, or reach a site outside its allow-list |

An `AGENT` key is not an alias for a `WRITE` key: a `WRITE` key can patch published
content *outside* the agent protocol's guards, which is the whole reason the scope
exists. Give an agent a scoped, expiring credential: never a console password.

**A run has an identity.** Calls carry a session id (from `Mcp-Session-Id`, or MCP's own
`initialize`), and it is recorded on every history entry. That is what makes "show me
what that agent did" a query rather than an archaeology exercise, and it is never minted
per request: a session is a *run*, and one session per call would be the same as none.

---

## Writes land as drafts

Every write an agent makes creates or updates a **`DRAFT`** row. The published version
of the page is untouched until something publishes it.

Publishing is gated by **`mayPublish`** on the token, off unless you grant it. The check
runs **before the first write, and on a dry run too**, so an agent that cannot publish
learns it while *planning* rather than half way through a batch. Because a desired-state
document compiles to publish operations like anything else, the gate covers `apply` and
blueprints, not just an explicit publish call.

Grant it when you want an agent to run unattended and you have decided that is fine.
Withhold it and you have a system where a machine writes and a person ships.

---

## A write can be rehearsed

Any batch or apply takes `dryRun`:

```json
{ "dryRun": true, "ops": [ … ] }
```

It reports exactly what would change , creates, updates, moves, deletes, per address ,
and writes nothing. For an agent this is a cheap way to check its own understanding
before spending a write; for you it is what the CLI prints before it touches anything.

---

## A delete needs a token that names what it deletes

`post.delete`, `folder.delete` and a `prune` that would remove something take **two
calls**. The dry run returns a `confirm` token; the real call sends it back.

The token is a digest of **what the plan actually deletes**: not of the operations
requested. Three consequences, all of them the point:

- A delete of something already gone needs no token, because nothing would be removed.
  A document therefore stays re-runnable.
- Omitting the token is a **428** that hands you the token *and the list*.
- A token that no longer matches is a **409** naming what would go **now**. An agent
  cannot approve one deletion and use the receipt for a different one.

---

## Everything is attributed, and grouped into runs

Every write records who did it and which run it belonged to. The console's **review
queue** at `/bento/admin/review` turns that into the curator's view:

A run you started with `--intent "<sentence>"` is titled by that sentence; any other run is titled
by what it did, such as "2 new pages, 1 edited page in quilhara".

| Action | Effect |
|---|---|
| Open a run | Every page the run touched, once, marked new or edited, with a small picture of the draft and its language. A page the run created and then fixed is one new page, with its writes listed under it. **Preview** opens the draft in a new tab; **Diff** opens what changed under the page itself, including a page the run created |
| **Publish all** | Publish every draft the run wrote |
| **Publish** on a page | Publish that page alone |
| **Discard all** / **Discard** on a page | Undo what the agent did. A page it created goes to the trash, and a page it edited goes back to the version it had before the run. A dialog says which of those will happen before anything changes, and a page-level discard asks why, so the agent can read the reason |
| Tick pages | A bar appears with **Publish**, **Discard** and **Leave for later** for the ticked pages. Leave for later records that you read them and are not ready, and changes nothing |

You do not have to keep the queue open to know a run is waiting. When a run has gone ten minutes
without a write and still has pages to review, every site webhook subscribed to `review_requested`
receives one notice for that run, such as "4 new pages in quilhara wait for review", with a
`reviewUrl` that opens the run in the queue. A run that writes again after its notice is announced
again. With `shio.review.email` on, the person who started the run also gets that sentence and link
by e-mail; turn it off for yourself in the dock's preferences.

Two design decisions worth knowing. It is a **view over the audit history grouped by
session**, not a separate proposal table: the draft row *is* the proposal, which is why
there is nothing to sync and nothing to expire. And it is **console-authenticated and
deliberately not on the agent namespace**: the curator's veto is not something the agent
can reach.

Each entry that still exists can show its change inline, compared the way the version
dialog compares, so a long run is read top to bottom without opening a window per page. A page
the run created has no earlier version, so it shows as new, with a summary of what it renders
and a preview link. Once an entry is approved there is no draft left to compare, so the entry
lists the fields the change touched instead.

An edit to a page that was never published goes back to the draft the run wrote over: the
first time a run changes a draft somebody else wrote, Viglet Shio keeps that draft as a revision, so
the page's version history holds it too. Discarding reports what it **cannot** honestly undo
rather than guessing: an edit to a page the run itself created has nothing earlier to go back
to, so it is left as it is and named, and discarding the creation removes the page. If an entry names a deleted folder, discarding it restores the whole section,
which is what a person means by "undo that". Publishing skips folder entries, because a
section has no draft to publish.

---

## A delete is a trash, not a hole

`folder.delete` and the console's folder delete move the subtree to the **trash**
instead of erasing it, so the operation the confirm token gates is no longer a *harder*
delete than removing each post by hand would have been. Individual posts behave the same
way.

---

## What is not covered yet

A reader who discovers a gap after trusting this page loses more than one who was told
about it. Three, as of 2026.3:

- **The trash is console-only.** An agent can fill it and **cannot list or restore it**: 
  there is no agent read, MCP tool or CLI command for the trash. Undo is a curator at a
  keyboard.
- **`site.delete` purges.** Deleting a whole site removes it and everything in it
  outright: it takes a confirm token like any destructive op, but unlike a folder delete
  there is **no undo**. Treat it as the one irreversible verb.
- **Scheduled publishing is not a review step.** A scheduled publish fires on its
  timestamp; nothing asks for approval at that moment.

---

## Related Pages

| Page | Description |
|---|---|
| [The Agent Surface](/docs/agent-surface/) | The endpoints these rules bound, and the op vocabulary |
| [MCP Server](/docs/mcp/) | Connecting an agent in the first place |
| [Administration Guide](/docs/administration-guide/) | Users, groups, roles and permissions |
| [Security](/docs/security/) | Authentication, token scopes, CSRF and CORS |
