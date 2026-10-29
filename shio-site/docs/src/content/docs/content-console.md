---
title: The content console
description: "The surface a curator is in every day in Viglet Shio: the assistant dock, the content browser and its filters, the post form, copy and move, selecting more than one thing, and the trash."
sidebar:
  label: The content console
---

This is the screen you spend your day in. An agent builds the site through files and
API calls; you open the console to look at what it built, fix a sentence, move a page
into the right folder, and put things away.

The console is at `/bento/content` on your Shio instance. Everything below is
reachable from that first screen.

:::note[The console moved from `/console` to `/bento`]
Older bookmarks still work. Every `/console/**` address redirects to the same surface
under `/bento`, keeping any query string and anchor, so a saved search still arrives
searched. The redirect replaces the entry in your history rather than adding to it,
so **Back** goes where you expect instead of bouncing forward again.
:::

## Finding your way

Down the left is the **navigation rail**, with **Home** and the two areas: **Content**
and **Administration**, each an icon with its name under it. Choosing an area opens its
**hub**: every screen of that area as a tile, in small labelled groups (Content's
*Library*, *Publishing* and *Content model*; Administration's *Access*, *Agent and audit*,
*Integrations* and *This instance*), so the Trash, the Media Library or API Tokens are
one click from the rail. The rail carries the areas rather than every screen, because
the fastest route to a named screen is not a menu:

- **The command palette**: <kbd>Ctrl</kbd>/<kbd>Cmd</kbd>+<kbd>K</kbd> from anywhere,
  or just <kbd>/</kbd> when you are not typing in a field. It lists every surface in the
  console and searches their descriptions as well as their names, so *restorable* finds
  the Trash and *credentials* finds API Tokens.
- **Each page names itself** in a hero at the top, which carries the page's title and its
  way back. A folder or a post shows its whole location there instead, as a **trail** of
  links above the title (*Sites › VigR3 › Home*), and the title is the folder or the post
  itself. There is no breadcrumb bar across the console.
- **From the keyboard**, the first <kbd>Tab</kbd> on any page reaches **Skip to content**,
  which jumps past the rail and the header to the page itself. After you move to another
  page, focus lands on that page's title, so a screen reader announces where you arrived.
  A button that is working keeps its focus and is announced as busy until it finishes.

Across the top, the header starts with the Shio name, which takes you home, and the search
button that opens the command palette. At its right end is the theme button: choose
**Light**, **Dark** or **System**, which follows your operating system. The console
remembers your choice in this browser, and the login page uses it too.

---

## The assistant dock

In the bottom-right corner is the **assistant dock**: the Viglet mascot and, beside it, a
one-line caption. It is where the console tells you how things ended. You do not have to go
looking for the outcome on another screen.

- **Your own saves and actions.** A save, a publish or a delete moves the mascot and writes
  the result as the caption. The caption is announced to screen readers once, and the mascot
  itself is decorative.
- **A refused save.** When the server refuses something, the dock keeps a report with the
  server's own explanation and what to do about it. If the refusal is about one field, the
  report offers **Go to \<field\>**. If the server suggests a correction, it also offers
  **Use "\<suggestion\>"**, which puts that value into the field for you. Nothing is saved
  until you save.
- **Agent runs.** When an agent starts writing, the mascot shows it working, with the agent's
  name and the site in the caption. When the run goes quiet, the dock reports what it did:
  changes waiting for your review, or a summary when there were none. Two buttons follow:
  **Open in review**, for that run, and **See the activity**. If runs were already waiting
  for review when you signed in, the dock says so once.
- **Scheduled publishing.** A scheduled publish that fails becomes an error report with the
  reason, **Open the page** and **Retry now**. One that works names the page and when it went
  live. A failure that happened while you had no console open is reported the next time you
  open one, so a launch that did not happen is not left for a visitor to discover.
- **Form submissions.** A submission from one of your site's forms arrives as a report
  captioned with its title and summary, with **Open the submission** and **Move to trash**.
  Several that arrive together are one report, which opens the newest; nothing in the corner
  discards a whole batch in one click.

Click the mascot to open the dock and read its reports. Each one can be dismissed, and the
count on the collapsed dock is what you have not read yet.

### Choosing what the dock reports

Open the dock and choose the **settings** control (the cog) in its panel. It opens **What
the dock reports**, with one switch per kind: your
own saves, agent runs, scheduled publishing, and form submissions. Turn off what you do not
need. The choice is kept on your account, so it follows you to another browser.

Failures are not a switch. A report about something that went wrong always reaches the dock,
whatever you turned off. Nothing here hides a record either: the Activity trail keeps every
event.

The last switch, **Animate the mascot**, keeps the dock's mascot moving. Turn it off when
you are presenting your screen or find the motion distracting. Only the motion stops: each
report still appears in the dock, all at once instead of being typed out, and a screen
reader still announces it. Like the other switches, the choice is kept on your account.

### Asking the connected agent

Viglet Shio does not run a model of its own. When an agent is connected to your instance (a
run has started in the last few hours) and you are on a page or a folder, the dock opens into
a chat. What you type there reaches that agent as a request about the page you are on, and
its answer appears in the dock. While you wait the dock shows it is waiting, and an answer
that arrives while the dock is closed counts as unread. With no agent connected, or no page
open, the dock stays a status light.

When the agent answers by changing the page, for example by drafting a new hero, the answer
comes with **Open the change**, which opens that run in the review queue, and **Approve and
publish**. Approving here is the same approval as in the review queue, with the same
permissions. To reject a change, open it in the review queue, where you can say why.

---

## The content browser

A site is a tree of **folders** holding **posts**. The browser walks it the way a file
browser does: click a folder to go in, use the path to come back out.

- **The path**: from the site down to where you are, in the browser's own toolbar.
  Every segment is clickable. This is the one trail the console still draws, because it
  is the one place you move up and down a tree all day.
- **New \[Post Type]**: creates a post of the type you used last, remembered per
  browser. The grid icon beside it opens the full post-type picker if you want a
  different one.
- **New Folder** and **Import** sit next to it.
- **Sort**, **Select**, **Action in batch** are on the right.

Each row shows the item's name, when it changed, its type, and, on hover, the
actions for that one item: view, edit, clone, copy, move, delete.

### Filtering the list

Above the list is the **filter bar**. Type in its search field to narrow the folder to items
whose text matches, or press <kbd>/</kbd> anywhere on the page to jump to it. Beside it:

- **Content type** and **State** (draft or published), as menus of what this instance has.
- **Language**, shown only when the folder holds more than one.
- **Changed**, a date range.

Each active filter shows as a chip under the bar; remove the chip to remove the filter.
**More filters** holds the rest: who created or last changed an item, and whether to include
the folders below this one.

Every filter is part of the page's address, so a filtered view is a link you can send.
Whoever opens it, or reloads the page, sees the same rows. The filters are the ones the
agent and the CLI use (`shio find` takes the same words), so the link is also the request
an agent would make.

**Search** has the same bar, with a **Site** menu when there is more than one site, and a
filter alone is a search: choosing *Draft* on a site with no text typed lists every draft
there. **The trash** has it too, narrowing what is in the trash by name and by post type.

### Three ways to look at a folder

A folder opens as a **table**, and the buttons at the right of the toolbar switch it to a
**list** or a **grid** of cards. The choice is part of the address, like the filters. On a
wide screen, the folders around the one you are in sit beside the listing: its ancestors
down to it, and its own sub-folders under it, each one a link. The table shows each item's
type, state (*Draft* or *Published*), language and when it last changed, and it
sorts by any column header, hides columns you do not need (remembered for you in this
browser), and only draws the rows on screen, so a long folder stays quick. A post's title
can be edited right in the table: the pencil opens it on the title as it is now, and if
somebody else changed the page while you typed, the save is refused and asks you to reload
rather than overwriting their change.

### The listing is paged, and folders are not

Posts arrive **50 at a time**. Folders do not: a folder's children folders are all
returned at once, so the folder half of the screen is always complete.

The counter above the list is the folder's **real** post count, not the number of rows
loaded. If it says 480 and you can see 50, both numbers are correct, and this is the
distinction that decides what "select all" means, below.

---

## The post form

Opening a post gives you a form **generated from its post type**. You are not editing
JSON; you are filling in the fields somebody modelled, in the order they modelled them.

- The header is the post itself: its title, the trail of folders it sits in, and one
  line saying its type, its language and whether you are editing the draft or the
  published row. **Actions** holds every verb that commits or leaves; Preview and the
  language menu sit beside it.
- Each field is its label, its control and a help line. Which widget draws it (text,
  rich text, date, a reference to another post, a repeating group, and so on) is shown
  when you hover the label. See [Content Modeling](/shio/docs/content-modeling/) for what each
  widget does.
- A field marked with `*` is required and the form will not save without it.
- A page's **Sections** are listed by title, in the order the page renders them. Move one
  with its arrows, remove it with its ×, and add one by typing part of its title (or pasting
  its friendly URL) in the box below the list.
- Fields can be grouped into **tabs** by whoever modelled the type.
- The **save bar is sticky** at the top: it stays visible however long the form is, and
  carries the post's title, its type, its field count, and whether you have permission to
  publish it.
- **Save** keeps you on the form. **Save & Close** returns you to the folder.

Every field in the form has a name a screen reader announces, says what it requires, and can
be reached from the keyboard. Clicking a field's label puts the cursor in it.

### Previewing before you save

The editor has two panes. The left one holds the fields and the post's history. The right one
holds what you read while you write: the live preview, the page's insights, and the notes on
the page. Each pane scrolls on its own, so the preview stays beside the field you are changing.
Drag the divider between them to change the width; the console remembers the width you choose,
in this browser. On a phone the panes become one, with a switch at the top between **Fields**
and **Live preview**, and switching keeps whatever you have typed.

The notes on one page sit beside that page. To see every open note across your sites, click the
**Open notes** tile on the home page, or open **Content › Open notes**. Each row names the page
and the field the note is about, says who left it and when, and links to the page. **Resolve**
closes a note from the list, without opening the page.

The **Preview** card renders the page from the values in the form, including ones you have not
saved, so you can see a change before you commit to it. Nothing in the preview is saved. It refreshes
when you open the post, when you save, and when you press **Refresh**. Turn on **Live** to have it
re-render after each pause in your typing. It is off by default, because each render composes the
whole page on the server. Buttons above the frame show it at phone, tablet and desktop widths.

Only a post type the site renders as a page can be previewed. That means one bound to a layout in the
site's post-type layouts (`postTypeLayout`). A menu, a section or a theme is content that other pages
use, so its preview card says the type has no page layout on this site and does not try to render.
To preview such a type, bind a layout to it on the site.

### Leaving with unsaved work

While the form holds changes you have not saved, the console asks before they are lost: closing
the tab, reloading or leaving the site gets the browser's own question, and moving anywhere inside
the console (the rail, the command palette, a link, Back, or **Cancel**) gets one from the console.
Changing a filter or a view on a list is not leaving, so it never asks. The Universal Editor does
the same, naming how many edits are pending.

### Asking the agent for a value

When an agent is connected, text fields in the post form (titles, summaries, body text) show
**Ask the agent**. It opens a short prompt, filled in from the field's name and yours to
edit, and sends it to the agent as a request about that field. The agent reads the field, what
it accepts and the page, and answers in the [assistant dock](#the-assistant-dock) with a
value. **Apply to \<field\>** puts the value into the form as an unsaved edit. You see it in
the field, the page counts as changed, and nothing is stored until you save, exactly as if you
had typed it.

### When a save is refused

A refused save says **why**, in the server's own words, with what to do about it: the reason,
then the fix, the name you probably meant, or the values that are allowed. Where the refusal is
about one field (a URL another live post already uses, say), that field is marked in red with
the message under it, and the mark clears on your next save. The same refusal an agent receives
over the API is what a curator reads here.

On the post form the refusal is kept in the [assistant dock](#the-assistant-dock) rather than
shown as a message that disappears, so it waits until you have read it. From there, **Go to
\<field\>** takes you to the field it is about, and **Use "\<suggestion\>"** puts in the
correction the server proposed.

Saving writes a **draft**. Publishing is a separate act with its own permission: see
[Letting an agent in](/shio/docs/agent-safety/) for the full draft-and-publish model.

---

## The Media Library

**Content → Media Library** is where every file lives: images, video, audio, documents,
archives. It is one screen, not two: the media browser and the static-file manager are
the same thing.

- **Upload** by dragging files onto the page or clicking **Upload**. Pick the site to
  upload into first. A file added through a **File** field on a post lands here too.
- **Browse** as a grid or a list, filter by category (Images, Videos, Audio, Documents,
  Archives, Other), or search by name.
- Each file shows its type, size, folder, upload date and, for images, its dimensions.
- **Copy URL** puts the file's public URL on the clipboard; **Download** fetches it.
- Select several files to **move**, **copy** or delete them in one go. A media delete is
  **not** the reversible trash: the confirmation says so.

Selecting a file opens its details, where you can set:

- **Alt text**: describes the image for accessibility and search engines.
- **Caption**: optional.
- **Tags**: type and press Enter.
- **Used in**: the content that references this file, or *"Not referenced by any
  content"*. Check it before deleting.

Those three values are declared fields on the **File** content type, alongside the path,
checksum, size and image dimensions Shio stamps on upload. So they are not only a dialog:
they appear when you open the file as a post, they are part of the type's schema for
anything reading it over the API, and `shio verify` treats them as known fields rather
than reporting a captioned image as carrying data its model does not describe.

---

## Copy and move

Both start the same way: pick one or more items, choose **Copy** or **Move**, and a
dialog walks *down* from the site root so you can choose a destination folder.

**Cloning** is the shortcut for the common case: copy an item into the folder it is
already in.

### What a copy does to names

A copy **takes names that are free**. Cloning a page called `Home` at `/home` gives you
`Home (2)` at `/home-2`. The title moves with the URL, so you never end up with two rows
you cannot tell apart. Copying a folder renames the folder the same way.

The two counters are independent, because they live in different scopes: a **title** is
unique within its folder, a **URL** is unique within the site. Copy `Home` into a
different folder of the same site and it keeps the title `Home`, and only the URL gets
a number.

### Copying a folder copies its files

A folder full of images copies as a folder full of images: each copied file gets its own
stored bytes, not a pointer at the original's. That matters when you later delete one of
them: removing an image from the original section leaves the copy's image working.

### What a move does to names

A move **relocates and changes nothing else**. The post keeps its URL, which is the
point, because that URL may be live and linked from elsewhere. A post that has no URL
yet gets one on the way.

If the destination site already has something at that URL, the move is **refused** with
an error telling you what is in the way. It is never silently renamed: a published page
quietly changing address is exactly the surprise this refuses to cause.

### What the dialog will not let you do

You cannot move or copy a folder into itself or into anything inside it. Those folders
appear in the picker **greyed out rather than hidden**, so you can see that the folder
you are looking for is the one you are dragging, and the confirm button says why it is
disabled.

### Permission is checked at both ends

- A **move** needs write permission on the destination *and* on every item you are
  moving. Moving something out of a folder is a removal from that folder's point of
  view, and it is authorized as one.
- A **copy** needs write on the destination and **read** on every source.

The check runs over the whole selection **before** anything moves. A batch you are only
partly allowed to move moves **nothing**: it does not relocate the items you happened
to list first and then stop.

The console and the agent surface enforce the identical rule. Where they ever
disagreed, the stricter one was the correct one.

---

## Selecting more than one thing

The **Select** menu is the part whose behaviour is genuinely not guessable from the
checkbox, so it is worth reading once.

| Choice | What it selects |
|---|---|
| **Folders** | Every folder here. Folders are never paged, so this really is all of them. |
| **Content** | Every post that matches, **not** just the 50 on screen. It pages the rest in first. |
| **Everything** | Both of the above. |
| **Invert** | The rows **currently loaded**, inverted. |
| **Nothing** | Clears the selection. |

Two consequences worth having in mind:

- **"Content" and "Everything" are about the whole folder.** Choosing one on a folder of
  480 posts selects 480 posts, and the delete dialog will say 480. That is deliberate: a
  dialog that said "50 item(s)" and left 430 behind is how a curator concludes a folder
  is empty when it is not.
- **"Invert" is about the screen.** Inverting a set that includes rows nobody has looked
  at is not a gesture anyone means, so it does not.

The menu works the same in every view, the table included: what you choose from it is what
the table's checkboxes show, and the table's own count and **Clear selection** act on that same
selection.

There is a ceiling of **1000 items**. Past it, the selection stops and tells you how many
of how many it took, rather than pulling an unbounded folder into your browser. The number
in the confirmation dialog is always the number that will actually be acted on.

**Action in batch** is enabled only while something is selected, and applies the action to
the selection.

Publishing, unpublishing or deleting a selection first shows the plan: one row per item, saying
what would happen before anything does. Each row carries the same mark a comparison uses:
**added**, **removed**, **changed** or **unchanged**. A changed row also names the change, such
as *published* or *moved*. An item the server would refuse is marked **refused**, with the reason
and the fix beside it. **Apply** runs the plan as shown.

A batch delete reports **what was deleted**, not what was selected. If the server refuses some
items, the message gives the count that went and lists the refusals with their reasons, and the
refused rows **stay selected** so you can deal with them and try again. The list stays on the
page you were on rather than jumping back to the top.

---

## Form submissions

**Forms**, in the rail, lists what arrived through one site's form, newest first, with a
**Move to trash** on each row for the ones dealt with. Above the list is where that site's form
sends things: the folder, the post types it invites, the browser origins it accepts, and the
switch that turns it on. How a site renders a form and issues its token is in
[website-development § Forms](/shio/docs/website-development/#forms).

## The trash

**Deleting is reversible.** Deleting a post or a folder marks it deleted; it does not
remove it.

- **Deleting a folder** trashes everything beneath it (every post, every sub-folder) as
  one act, stamped with one instant.
- **Trashed items disappear from the browser** everywhere, including from search and from
  the delivery API. To a reader the site behaves as though they are gone.
- **The trash page** lists what was deleted. A deleted folder appears as **one row for
  the deletion**, not one row per folder inside it, because the deletion is what you would
  undo.

Each row offers two things, **Restore** as its button and **Purge** in the row's **⋯**
menu:

- **Restore** puts back exactly what *that* deletion removed. Something that was already
  in the trash before that delete stays in the trash: restoring a folder does not
  resurrect things you threw away last month that happen to live inside it.
- **Purge** is the permanent one. This is where the content is really removed, where its
  uploaded files are cleaned up, and where any webhook subscribers are told it is gone.
  A reversible delete deliberately has none of those side effects.

**Nothing expires the trash on a timer.** Items stay until somebody purges them, so
"delete" is safe and "purge" is the one to be careful with.

Deleting a **site** is different: it purges. There is no undo at site level.

Every delete, restore and purge is attributed to whoever did it and recorded in the
activity history.

---

## Related

- [Content Modeling](/shio/docs/content-modeling/): the post types the form is generated from
- [Letting an agent in](/shio/docs/agent-safety/): drafts, publishing, the review queue
- [Universal Editor](/shio/docs/universal-editor/): editing on the rendered page itself
- [Administration](/shio/docs/administration-guide/): the console's admin and configuration screens
