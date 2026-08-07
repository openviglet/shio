---
name: shio-replicate
description: Copy a site that already exists into Shio — capture it, decide how much of it becomes editable content, publish it, and prove the copy is the site. Use when running shio clone, propose, convert, publish or diff, when choosing between fidelity and authorable mode, and for any question about whether a replicated page looks or behaves like its source.
---

# Replicating a site that already exists

The chain is four commands and each one writes a file the next one reads:

```
shio clone <url> --depth N --assets --markup [--render] [--scripts] --site X
shio propose [--accept all]      # accept nothing = fidelity, accept = authorable
shio convert --site X [--create-site]
shio publish --site X --all      # every post lands in DRAFT; publishing is explicit
```

Judge the result on the **public** route — `/sites/<site>/default/<locale>/` — never the
preview. The preview draws a banner that shifts every pixel, and it renders drafts, so an
unpublished asset looks fine there and 404s to a visitor.

## Read the tool's own notes. They are the cheapest thing in the run

`clone` teaches the next flag and it is usually right. *"almost no text with scripts present
… re-run with `--render`"* turns a blank capture of a client-rendered site into a real one.
`propose` says what it cut and why. `convert` says what will fail before the gate sees it.
Following a note costs one flag; ignoring one costs a round.

## Three modes, and a run must say which one it is

They fail differently and they are proved differently, so a number quoted without its mode
means nothing.

**Fidelity** — accept nothing. The source's own markup is written as the page's `content`
and Shio's renderer serves it. Proof: the public URL is 200 anonymously, every reference
resolves, and a pixel comparison reaches 0.00%. It has no editable fields, by definition —
verbatim markup carries no annotations, so the editor opens over a page with nothing to
edit. That is the documented property, not a defect.

**Authorable** — accept candidates. Blocks become sections a curator can edit, and the page
is presented by a Shio theme rather than the captured one. Judge it as a page somebody would
keep, not as a failed copy: does a hero read as a hero? An authorable page **drops the
source's chrome on purpose** — its nav, its favicon link, its stylesheet tag have no section
to live in. What it is held to is the content.

**Headless** — the front end over the CDA. `shio init <dir> --site <site>` scaffolds it, and
it refuses when the site's pages are sections and the template cannot render them. Take the
refusal at face value: a scaffold that installs is not a front end that draws.

**A round is not complete until you say which modes ran.** Fidelity is the pleasant one — it
reaches 0.00% and looks finished — which is exactly why it is not evidence about the other
two.

## What to check, in the order that finds things

1. **`shio diff --site X`.** The only instrument that holds an *expectation*: the capture's
   own counts against what the replica serves. It knows the mode, so it scores an authorable
   page on its content and a fidelity page on everything. Read the shortfalls it marks *by
   design* separately from the ones it does not.
2. **`shio verify --site X`.** Errors are broken now; warnings are things the CMS cannot
   prove from inside. A finding on an uploaded file is the one to distrust first — an asset
   has no friendly URL and rules written for content posts have been misapplied to it before.
3. **Every reference resolves, resolved against the URL the page is served at.** Not against
   the site root: a relative `./../../img/x` on a page served several segments deeper lands
   somewhere else entirely. Filtering for a leading `/` once reported zero broken references
   on a page with fifteen missing images.
4. **No link carries the source's own base.** A verbatim absolute href pointing at the
   source's origin sends the reader back to the site you copied, and it resolves fine, so
   nothing reports it.
5. **Both spellings of a directory path answer** — `/x` and `/x/`, one canonical and one
   redirecting, the way the source does. Get this backwards and every relative reference on
   that page resolves one level too high.
6. **Count, do not eyeball.** Compare the annotations a page emits against the fields each
   section type declares, **including every collection row**, and in **both** declaration
   forms — a value that lands in an attribute is declared beside the element's own field.
   Counting one form reads a complete page as nine of ten.
7. **Click something.** A replica can be a fraction of a percent from its source and
   completely inert: a toggle and a modal are handlers, and a bundle is not captured unless
   you asked for it. Pixels prove appearance and nothing else.

## The failure shape to distrust

**The report ships ahead of the behaviour it reports on.** A phase that *describes* an
omission has not necessarily closed it — a note was fixed and not the fetch, a list was added
and not the download, stranded bytes were made visible and delivery left unbuilt. When
something tells you what it dropped, check that the next command acts on it.

## Cleaning up

A replication round leaves a site per attempt. `site.delete` exists; use it. Say which sites
were experiments before somebody else has to guess.
