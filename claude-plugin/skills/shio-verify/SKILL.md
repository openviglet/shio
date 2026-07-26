---
name: shio-verify
description: Check Shio content before handing work over — the content lint, route resolution, a page's structural digest, and recent asynchronous failures. Use when content has just been written or published, when a page looks wrong, when something failed silently, or before reporting that a Shio task is done.
---

# Checking Shio work without asking a human

Verification is the cheapest thing in the system and it replaces the most expensive one: a
human round trip. Run it before you say you are done.

## The content lint

`shio_verify` (or `GET /api/v2/agent/verify`, or `shio verify`) reports required fields left
empty, references to posts that do not exist, `File` fields with no bytes, two pages on one
URL, empty folders, links that resolve to nothing, and anything that failed asynchronously.
Every finding carries an SH79 address and a `fix`.

**Read the severities correctly:**

- **`ok:false` means at least one *error*** — something is broken now.
- **A warning is something the CMS cannot prove from the inside**: an empty folder, a link
  that may be a front-end route. Warnings do not make `ok` false, and a gate that failed on
  them is a gate people learn to bypass. Report them; do not treat them as failures unless
  the user asked for `--strict`.
- **`found` vs `findings`**: if they differ, the list was cut. Raise `limit` or narrow with
  `checks`/`site` before concluding anything about what is not in it.

## A page's structure

`shio_digest` (`GET /api/v2/agent/render?address=…`) returns a page's **structure**, not its
pixels: the heading outline, landmark counts, the link inventory, images with their alt text,
a word count, and a `digest` hash over the structure alone. A typo fix does not move the
hash; a lost `<h1>` does. Use it to prove a page came out as intended, and to compare before
and after a change.

`?url=` digests the delivered page instead of the stored markup, but it is **off** unless the
operator set `shio.agent.render.allow-fetch=true`. Do not report a page as verified from the
visitor's side unless that call actually succeeded.

## What failed while you were not looking

`shio_changes` — content transitions since a cursor, including a human's console edits. Read
it at the start of a follow-up session before trusting a cached context digest.

`GET /api/v2/agent/diagnostics` — webhook deliveries, image transforms, scheduled publishes
and 5xx. The window is **in memory and bounded**, and it says so: an empty report means
"nothing since it started collecting", which is not the same claim as "nothing ever failed".

## Reporting

Say what you verified, and say what you could not. "Content lint clean, 2 warnings about
empty folders; routes not checked because the front end is not running" is a useful report.
"Done" is not.
