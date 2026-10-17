# SH14: Versioning UI (draft↔published diff + restore), implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §III.3
> Status: ⏳ partial · deps:,

## 1. Context: what already exists (do not rebuild)

- **Versioning is row-per-state, not a long history.** The unified model
  ([ShPostUnified](../../shio-app/src/main/java/com/viglet/shio/persistence/model/post/unified/ShPostUnified.java))
  has a composite key `(id, state)` where `state ∈ {DRAFT, PUBLISHED}`. A logical
  post holds **at most two rows**: one editable `DRAFT` and one immutable
  `PUBLISHED` snapshot taken at the last publish. There is no per-save history
  table.
- **The `/versions` endpoint already returns those rows.**
  [ShPostUnifiedAPI](../../shio-app/src/main/java/com/viglet/shio/api/post/unified/ShPostUnifiedAPI.java)
  `GET /api/v2/post-unified/{id}/versions` → `shPostUnifiedRepository.findById(id)`
  returns the `List<ShPostUnified>` for that id (0, 1, or 2 entries). The React
  service already wraps it: [`ShPostUnifiedService.versions(id)`](../../shio-react/src/services/post-unified/post-unified.service.ts).
- **Publish clones DRAFT → PUBLISHED.**
  [ShPostUnifiedWriteService.publish](../../shio-app/src/main/java/com/viglet/shio/migration/unified/ShPostUnifiedWriteService.java)
  deletes any existing `PUBLISHED` row and re-creates it from the current `DRAFT`
  (`data`, title, summary, furl). So immediately after a publish the two rows are
  identical; they **diverge** only as the editor keeps changing the draft.
- **`data` is a JSON document stored as a `String`** (`@Lob`). On the way **in**
  (`PUT`/`POST`) the controller binds it as a Jackson `JsonNode`; on the way
  **out** the entity exposes it via `getData()` as a `String`, so a `versions`
  response carries `data` as a **JSON string**, not a nested object. The diff UI
  must coerce defensively (see §5).
- **The post editor is the natural host.**
  [post.edit.page.tsx](../../shio-react/src/app/content/post.edit.page.tsx)
  already loads the `DRAFT` (falling back to `PUBLISHED`), keeps the live `data`
  in state, parses the post-type field schema (`parseSchema(shPostType.schemaJson)`),
  and renders a `StickySaveBar` whose `actions` slot already hosts Preview/Save/
  Save&Close/Cancel buttons (SH13). SH14 adds one more button there.

**What SH14 adds:** the console UI the roadmap calls for, a **History** dialog
that shows the two states side-by-side, computes a **field-level diff** of the
unpublished draft against the live published snapshot, and offers a **Restore**
action that reverts the draft to the published version (discards unpublished
edits). The single missing backend piece is the restore endpoint; everything else
is read-only over the existing `/versions` data.

## 2. Goal & scope

**In scope**
- Backend: `ShPostUnifiedWriteService.restoreDraftFromPublished(postId, modifier)`,
  copies the `PUBLISHED` row's `data`/title/summary/furl into the `DRAFT` row
  (creating the `DRAFT` row from the published one if it does not exist), stamps
  `modifiedDate`/`modifier`, and returns the updated draft. `POST
  /api/v2/post-unified/{id}/restore` exposes it (console-authenticated, like the
  rest of the controller). No Liquibase change, reuses existing columns.
- Frontend: `ShPostUnifiedService.restore(id)`.
- Frontend: a `PostVersionsDialog` component, fetches `/versions`, splits into
  draft/published, renders metadata for each state and a field-level diff table
  (driven by the post-type schema), with an "only changes" toggle and a
  two-step **Restore published version** button.
- A **History** action (`History` icon) in the post editor's sticky save bar;
  on restore it refreshes the editor's in-memory `data` so the form reflects the
  reverted draft. i18n in `en` + `pt`.

**Out of scope (deferred)**
- A true per-save / per-publish version *timeline* (a history table with N
  snapshots). The model is two-row by design; a richer history is a separate,
  larger task and is **not** implied by "the `/versions` endpoint exists".
- Restoring *into* `PUBLISHED` directly (that is `publish`, already shipped) or
  field-level cherry-pick restore. Restore is whole-document, draft←published.
- Inline rich diffing of HTML/relator internals, values are compared and shown
  as normalized text/JSON; structural diffing is not in scope.

## 3. The two "versions", precisely

Given the row-per-state model, the UI presents exactly the states that exist:

| State | Meaning | Key metadata |
|---|---|---|
| `DRAFT` | the editable working copy (what Save writes) | `modifiedDate`, `modifier`, `owner` |
| `PUBLISHED` | the live snapshot from the last publish | `publicationDate`, `publisher` |

- **Both present** → diff DRAFT vs PUBLISHED = "unpublished changes". Restore is
  offered.
- **PUBLISHED only** (published, no separate draft yet) → restore re-creates a
  draft from published; the diff shows published values with an empty draft side.
- **DRAFT only** (never published) → nothing to compare/restore; the dialog shows
  a "not yet published" state and disables Restore.

## 4. Backend: restore

`ShPostUnifiedWriteService`:
```java
@Transactional
public ShPostUnified restoreDraftFromPublished(String postId, String modifier) {
    ShPostUnified published = shPostUnifiedRepository
            .findByIdAndState(postId, ShPostState.PUBLISHED)
            .orElseThrow(() -> new IllegalArgumentException(
                    "No published version to restore from: " + postId));

    ShPostUnified draft = shPostUnifiedRepository
            .findByIdAndState(postId, ShPostState.DRAFT)
            .orElseGet(() -> {
                ShPostUnified d = new ShPostUnified();
                d.setId(postId);
                d.setState(ShPostState.DRAFT);
                d.setShFolder(published.getShFolder());
                d.setShSite(published.getShSite());
                d.setShPostType(published.getShPostType());
                d.setOwner(published.getOwner());
                d.setDate(published.getDate());
                return d;
            });

    draft.setData(published.getData());
    draft.setTitle(published.getTitle());
    draft.setSummary(published.getSummary());
    draft.setFurl(published.getFurl());
    draft.setModifiedDate(new Date());
    if (modifier != null) draft.setModifier(modifier);
    draft.setPublished(false);
    draft.setPublishStatus(ShObjectPublishStatus.DRAFT);
    return shPostUnifiedRepository.save(draft);
}
```
No webhook is fired, restore mutates only the draft, which is never the public
content (so SH12's publish/unpublish/delete contract is untouched).

`ShPostUnifiedAPI`:
```java
@PostMapping("/{id}/restore")
public ResponseEntity<ShPostUnified> restore(@PathVariable String id, Principal principal) {
    try {
        return ResponseEntity.ok(shPostUnifiedWriteService.restoreDraftFromPublished(
                id, principal != null ? principal.getName() : null));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.notFound().build();   // no published version
    }
}
```
Security is unchanged, `/api/v2/post-unified/**` falls under `anyRequest().authenticated()`.

## 5. Frontend: diff & dialog

`ShPostUnifiedService.restore`:
```ts
async restore(id: string): Promise<ShPostUnified> {
  const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/restore`)
  return response.data
}
```

`PostVersionsDialog` (`src/components/post-versions.dialog.tsx`), props
`{ postId, schema, open, setOpen, onRestored }`:
1. On open, `versions(postId)` → split into `draft`/`published` by `state`.
2. **Coerce `data` defensively**: it may arrive as a JSON string or an object:
   ```ts
   function asDataObject(d: unknown): Record<string, unknown> {
     if (typeof d === "string") { try { return JSON.parse(d) ?? {} } catch { return {} } }
     return d && typeof d === "object" ? (d as Record<string, unknown>) : {}
   }
   ```
3. Build the diff from the flattened schema (skip `Tab`/`Hidden` widgets): for
   each field compare `JSON.stringify(publishedData[name]) !== JSON.stringify(draftData[name])`;
   classify `unchanged | changed | added` (draft has, published empty) `| removed`
   (published has, draft empty). Render values via a `formatValue` helper
   (null/undefined → `—`, arrays/objects → compact JSON, otherwise the string).
4. Render: two metadata cards (Draft / Published) + a diff `Table`
   (Field | Published | Draft) with changed rows accented (orange, Shio theme)
   and an **"Only changes"** `Switch` (default on). Show the changed-field count.
5. **Restore**: a destructive-styled button visible only when `published` exists;
   first click flips to an inline confirm ("Discard unpublished draft changes…?"),
   second click calls `restore(postId)`, toasts, calls `onRestored(updatedDraft)`,
   and closes.

`post.edit.page.tsx`: a **History** `GradientButton` (`variant="outline"`,
`History` icon) in the `actions` slot opens the dialog; `onRestored` does
`setPost(d); setData(asDataObject(d.data))` so the form reflects the reverted draft.

## 6. i18n keys (`content.*`, en + pt)

`versionHistory`, `versions`, `draftWorkingCopy`, `publishedLive`, `lastModifiedBy`,
`publishedBy`, `noChangesSincePublish`, `changesSincePublish` (`{{count}}`),
`notYetPublished`, `noPublishedToCompare`, `field`, `onlyChanges`,
`restorePublished`, `restoreConfirm`, `restored`, `restoreError`, `statusChanged`,
`statusAdded`, `statusRemoved`, `noVersions`, `emptyValue`.

## 7. Acceptance criteria

1. `POST /api/v2/post-unified/{id}/restore` (authenticated) on a post with a
   `PUBLISHED` row copies that row's `data`/title/summary/furl into the `DRAFT`
   row and returns the updated draft (`state=DRAFT`, `published=false`).
2. Restoring a post that has a `PUBLISHED` but no `DRAFT` row **creates** the
   draft cloned from published.
3. Restoring a post with no `PUBLISHED` row returns `404`.
4. The History dialog lists the existing states with their metadata and shows a
   field-level diff; the changed-field count matches the fields whose draft value
   differs from published.
5. A post with only a draft (never published) shows the "not yet published" state
   and disables Restore.
6. After a successful restore the editor form shows the published values without
   a page reload.
7. SH12 publish/unpublish/delete webhooks are **not** affected by restore.

## 8. Tests

- `ShPostUnifiedAPITest` (extend the existing staged flow): after publish
  (stage06) and versions (stage07): update the draft to a new title, `POST
  /{id}/restore`, then `GET` the draft and assert its title reverted to the
  published value; assert `restore` on a deleted/unknown id returns `404`.
  (crit 1, 3.)

## 9. Estimate

~0.5 day: restore service + endpoint (1h), service method + diff dialog + editor
button + i18n (3h), test (1h).
