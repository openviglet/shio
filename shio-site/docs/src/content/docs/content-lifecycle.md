---
title: Preview, history and scheduling
description: "The Viglet Shio curator's lifecycle tools: share a draft with a preview link, compare a draft against what is live, restore the published version, schedule a publish, and read the audit trail."
sidebar:
  label: Preview, history and scheduling
---

These are the tools that answer one question: **what happened to this page, and can I
undo it?**

They exist so a mistake is recoverable, but a person who does not know **Restore** exists
experiences a mistake as unrecoverable anyway. This page is the half of the safety model
that only helps if you have read it.

All of them live on the post's own edit screen, in the sticky bar at the top:
**Preview Draft · Translations · History · Schedule · Save**.

---

## Draft and published

Every post has two states, and it can be in **both at once**:

- **Draft**: the working copy. Saving writes here.
- **Published**: what visitors see.

Editing a published post changes only its draft; the live page keeps serving the
published version until something publishes the new one. That is why nothing you type in
the console reaches the site by accident, and it is the premise all five tools below rest
on.

Today publishing happens through the **Universal Editor**'s Publish button, or by
**approving an agent run** in Agent Review. See
[Letting an agent in](/shio/docs/agent-safety/) for how a run is approved.

---

## Preview a draft before anyone else sees it

**Preview Draft** on the post's edit screen opens the page as it will look, drafts
included, in a new tab.

What happens depends on where the site's front end is:

- **Shio renders the site itself**: the preview opens straight away. The console session
  you are already signed in with is the authorization, and no credential is minted.
- **The site has an external front end** (a Next.js app, say): Shio mints a **short-lived
  preview token**, confined to that one site, and opens the front end's preview route
  carrying it.

### Preview links expire, on purpose

A minted preview link is good for **15 minutes** by default, and **never more than an
hour**. That is the whole point of it: a link you paste into a chat should stop working
before it can be forwarded somewhere you did not intend.

**An expired link is not a broken product.** If a reviewer says the link no longer works,
click Preview Draft again and send a fresh one.

The token is **preview-scoped and site-scoped**: it can read that one site's drafts and
nothing else. It cannot write, and it cannot reach another site.

An operator can change the lifetime, or turn preview links off entirely, in
[configuration](/shio/docs/configuration-reference/):

```properties
shio.cda.preview.enabled=true
shio.cda.preview.ttl-seconds=900
shio.cda.preview.max-ttl-seconds=3600
```

If preview is switched off, the button is quietly unavailable rather than failing.

### Send a draft to someone without an account

When Shio renders the site itself, **Preview Draft** can also give you a **share link**: a link a
stakeholder with no login can open. The link is exchanged once for a cookie, so the credential
leaves the address bar, and the page still carries the banner saying it is a draft.

A share link opens **only the page it was made for**. A link to the pricing page shows the pricing
page, with its images and styles, and answers *not found* for every other page of the site and for
every other site. It expires like any preview link.

A link you sent is a link you may want to stop before it expires. List the live ones, then revoke
by id. The link is refused from the reader's next request:

```bash
shio share post:Acme/pricing        # prints a link to that one page
shio share --list                   # the live links: id, page, expiry, who made it
shio share --revoke <id>            # stop one
```

The same list and revoke are `GET /api/v2/preview-token` and `DELETE /api/v2/preview-token/{id}`.
The list shows links on sites you can read, by id, and never the link itself.

---

## History: compare any two versions

**History** on the post's edit screen opens the version dialog. On the left is the list of
versions, newest first: the **draft (working copy)**, the **published (live)** page, and every
revision kept since (see below), each showing who made it and when. A revision written by an
agent is marked as one, so you can tell a colleague's change from an agent's before you read
it.

Pick two and the dialog compares them. It opens on the question you usually have, what
changed between the live page and the draft:

- The header says *"N field(s) changed since last publish"*, *"No changes since last
  publish"*, or *"Not yet published, so there is nothing to compare"*.
- Each changed field is shown with the change inside it, word by word for text and block by
  block for rich text. A sentence that moved in a long body is marked where it moved, not left
  for you to find in two copies of the whole field.
- Fields that did not change are folded behind a count, so a post type with forty fields and
  two edits shows two.

The comparison comes from the server, so the console and every other caller see the same
answer. On the REST API it is
`GET /api/v2/post-unified/{id}/diff?from=published&to=draft`, where each side is `draft`,
`published` or `rev:<n>`.

### A page that was never published

A page that has never gone live has nothing to compare against, and the dialog says so. It
still shows the whole page as new, every field an addition, and above that a short summary of
what the page renders: its word count, its headings, its links and images, and its heading
outline. **Preview** opens it with a private preview link, so you can see it before anyone
else does. The review queue shows the same thing for a page an agent created.

### Restore a version, or one field

**Restore** puts the older of the two versions you picked back into the draft: **Restore
published version** when you are comparing against the live page, **Restore revision N**
when you picked a revision. It is the undo for "I edited this and it was worse."

When the newer side is the draft, each changed field also has its own **Restore \<field\>**
button, which puts just that field's older value back and leaves every other edit alone.

Every restore asks twice: the first click arms it, the second does it. None of them touches
the live page. A restore changes the working copy, and you publish it when you are ready. If
somebody saved the page after you opened the dialog, the restore is refused rather than
applied over their work (see
[When two people save the same page](#when-two-people-save-the-same-page)).

### Every version that went live is kept

Publishing replaces what visitors see, and the version it replaces is not lost: each time a
post goes live, Viglet Shio keeps a **revision** of it, numbered in order. A post keeps its
last fifty; `shio.versions.keep` changes that. Saving a draft is not captured unless
`shio.versions.capture-on-save=true`, because the question a history answers is usually
"what was live on Tuesday", not every keystroke.

From a terminal:

```bash
shio versions post:Blog/blog/hello-world                  # one line per revision
shio rollback post:Blog/blog/hello-world --to 3            # shows what would change
shio rollback post:Blog/blog/hello-world --to 3 --apply    # puts revision 3 back
```

A rollback puts the revision back **into the draft** and publishes nothing, so you can look
at it before anyone else does, then publish it as usual. It is itself recorded as a
revision, so going back to 3 and changing your mind leaves the version you left to come
forward to.

The same history is on the REST API as `GET /api/v2/post-unified/{id}/revisions`, one
revision as `GET /api/v2/post-unified/{id}/revisions/{version}`, and a rollback as
`POST /api/v2/post-unified/{id}/revisions/{version}/restore`.

### When two people save the same page

If somebody else saved the page after you opened it, including an agent working on the same
content, your save is **not** applied over theirs. The editor shows each field that differs,
their stored value beside yours, and keeps theirs unless you choose yours. Nothing is lost
silently in either direction.

A client doing the same over REST reads the `ETag` a post comes back with and sends it as
`If-Match` on the next `PUT`, `PATCH`, publish or delete. A stale one is refused with
`409 Conflict`, and the refusal says how to re-read.

---

## Keeping an old address answering

When a page's address changes (you renamed `/pricing` to `/plans`, or moved it to another
site), the old address stops answering. Every bookmark, search result and link from somebody
else's site then gets a 404, and nothing tells you. A **redirect** keeps the old address
answering by sending visitors to the new one.

- **When you publish the rename**, the publish dialog notices that the page's address is about
  to change and offers *Keep \<old address\> answering, as a permanent (301) redirect*, already
  ticked. Leave it ticked and the redirect is published at the old address with the page. If the redirect cannot be written
  (another page already answers there), you are told the page was published and the redirect
  was not.
- **An agent** writes one with `post.redirect`. The address is the old path, and `to` is where
  it sends people (an address, a site path or a full URL). It is a `302` unless
  `data.permanent: true` asks for a `301`. It is published at once, because a draft redirect
  routes nobody. It is refused where a page still answers at that path.
- **A move reports what visitors lost.** A move answers with the published addresses it
  retired and the ones it created, so you know which old addresses need a redirect without
  running anything else.
- **Finding the ones you missed.** `shio verify` reports `url-orphaned` for an address that
  used to be a page here, no longer answers, and is still linked from a published page. Each
  finding comes with the `post.redirect` that fixes it. `shio redirects` lists every redirect
  a site has, where each one sends and whether it is a 301 or a 302, and the orphans under it.
  It exits `1` when there is one, so a pipeline can stop on it.

---

## Schedule a publish or an unpublish

**Schedule** on the post's edit screen sets two optional instants:

| Field | Effect |
|---|---|
| **Publish at** | The draft goes live at this time. |
| **Unpublish at** | The live version comes down at this time. |

Either can be set without the other, and leaving a field empty means no schedule.
**Clear** removes one that is already set.

A scheduled post shows *"Scheduled for …"* so the state is visible without opening the
dialog.

To see everything that is scheduled at once, in the order it will happen, open
**Content → Scheduled**: each row is a post going live or coming down, linked to its editor.
The page and the schedule dialog both say which timezone their times are in, which is your
browser's. From a terminal, `shio schedule` lists the same (add a site name, or `--from` and
`--to` with ISO-8601 instants for a range), in UTC.

Switch the page from **List** to **Calendar** to see the same schedule as a month or a week.
To reschedule, drag an entry onto another day. It keeps its time of day, and a post's other
instant stays as it was. From the keyboard, move to the entry, press <kbd>Space</kbd> to pick
it up, use the arrow keys to choose a day, and press <kbd>Enter</kbd> to drop it. Press
<kbd>Enter</kbd> on an entry to open its post instead. A move is the same change as
editing the post's schedule, so it is recorded with your name.

An agent sets the same schedule with `shio_publish` and a `when`, and calls one off with
`cancel`. From a terminal:

```bash
shio publish --unpublish --when 2026-12-01T09:00:00Z post:Acme/promo   # comes down in December
shio publish --cancel post:Acme/launch                                  # calls off a scheduled launch
```

Setting one of the two instants leaves the other as it was, so dating a takedown does not
cancel the launch already scheduled for the same page.

When the scheduler publishes or unpublishes a post, the audit trail records it as the
scheduler's and names the person who set the schedule: *"Published "Launch" (scheduled by
ana)"*.

**The transition fires within a minute of the chosen time.** A background sweep runs
every 60 seconds and applies whatever has come due, so nobody has to be at a keyboard at
midnight, but do not schedule against a deadline finer than a minute.

**A scheduled publish checks the site's publish gates when it fires, not when you schedule
it.** If a gate stops the page at that moment, because the draft changed since or a gate was
added, the page stays a draft and keeps its schedule. The change feed and the dock report which
gate stopped it and why, with a button to try again. Fix what the gate names and the page goes
live on the next sweep, or publish it now past the gate with a reason. A scheduled release is
checked the same way for every page it holds, and it goes live whole or not at all.

---

## The audit trail

**Administration → Activity** answers "who did that": every create, update, publish,
unpublish, delete and restore, with the name of whoever made it and when.

It covers **every** surface (the console, the CLI, the delivery API and an agent), so a
change made by something that never opened a browser is attributed the same way as one
you typed yourself.

An update, a publish and a restore also name **which fields** they changed, under the row's
description: *"Fields: body, title"*. That is how you can still tell what an approved run did
after the approval, when the draft and the live version have become the same. From a
terminal, `shio changes --fields` prints the same names.

For an agent's work specifically, **Agent Review** is the better screen: it groups a run's
changes together so you can approve or revert the run rather than reading it one row at a
time.

See [Administration § Activity](/shio/docs/administration-guide/#activity).

---

## The trash

Deleting is reversible. A deleted post or folder is marked deleted rather than removed,
disappears from the console and the site, and can be restored from **Content → Trash**.
Nothing expires it on a timer; **purge** is the only permanent act.

The details (what one restore puts back, and why a folder delete is a single row in the
trash) are on
[The content console § The trash](/shio/docs/content-console/#the-trash).

---

## Related

- [The content console](/shio/docs/content-console/): the browser, the post form, the trash
- [Letting an agent in](/shio/docs/agent-safety/): draft-default, the review queue, reverting a run
- [Universal Editor](/shio/docs/universal-editor/): editing and publishing on the rendered page
- [Administration](/shio/docs/administration-guide/): activity, agent review, API tokens
- [Content Modeling](/shio/docs/content-modeling/): the post types the form is generated from
