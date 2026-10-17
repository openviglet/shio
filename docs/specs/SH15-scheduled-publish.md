# SH15: Scheduled publish/unpublish, implementable spec

> Roadmap: [ROADMAP.md](../ROADMAP.md) Block C · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §III.4
> Status: 📋 designed · deps: SH12 (shipped)

## 1. Context: what already exists (do not rebuild)

- **The unified write path is the single transition chokepoint.**
  [ShPostUnifiedWriteService](../../shio-app/src/main/java/com/viglet/shio/migration/unified/ShPostUnifiedWriteService.java)
  already owns `publish` / `unpublish`, and both already emit the SH12 lifecycle
  event (`ShPostLifecycleEvent`) **after commit**. SH15 schedules these existing
  transitions, it does **not** re-implement publish/unpublish or re-emit
  webhooks. Firing a scheduled publish reuses `publish(...)`, so the SH12
  dispatcher signs and POSTs exactly as it would for a manual publish.
- **The model is two-row-per-state.** A logical post holds at most a `DRAFT`
  (editable working copy) and a `PUBLISHED` (live snapshot). `publish` does **not**
  delete the draft, so once a post has ever been edited a `DRAFT` row persists.
  SH15 stores the schedule on the `DRAFT` row (the working copy the editor loads)
  and the sweep scans `DRAFT` rows.
- **No scheduling infrastructure exists yet.** There is no `@EnableScheduling`,
  `@Scheduled`, or `TaskScheduler` in the codebase. SH13 deferred its expired-token
  purge to "SH15's scheduler". SH15 introduces the single `@EnableScheduling`
  switch + one sweep.
- **Per-feature config follows `ShWebhookProperties` / `ShCdaProperties`**,
  `@ConfigurationProperties` bean, defaults shown commented in
  `application.properties`.

**What SH15 adds:** two nullable timestamps on a post (`publishScheduledDate`,
`unpublishScheduledDate`), an endpoint to set/clear them, and a periodic sweep
that fires the due transition (reusing `publish`/`unpublish`, hence SH12 webhooks)
and clears the fired field. Plus a console **Schedule** dialog on the post editor.

## 2. Goal & scope

**In scope**
- Two `Date` columns on `ShPostUnified`: `publishScheduledDate`,
  `unpublishScheduledDate` (Liquibase `addColumn TIMESTAMP`, MARK_RAN guarded).
- `ShPostUnifiedWriteService`:
  - `schedule(postId, publishAt, unpublishAt, modifier)`: set/clear both fields
    on the `DRAFT` row (created from `PUBLISHED` if absent, mirroring
    `restoreDraftFromPublished`). Returns the updated draft.
  - read finders + `publishScheduled(id)` / `unpublishScheduled(id)` that clear the
    fired schedule field and delegate to the existing `publish`/`unpublish`.
- `ShPostScheduler` (`@Component`) with one `@Scheduled(fixedDelayString=...)`
  sweep, guarded by `shio.schedule.enabled`. Each due post is processed in its own
  transaction (via the write-service proxy) so one failure can't roll back the rest
  and each fires its own AFTER_COMMIT webhook.
- `ShScheduleProperties` (`@ConfigurationProperties("shio.schedule")`): `enabled`,
  `sweep-interval-millis`. `@EnableScheduling` added on the `Shio` boot class.
- `POST /api/v2/post-unified/{id}/schedule` (console-authenticated).
- Frontend: model fields, `ShPostUnifiedService.schedule`, a `PostScheduleDialog`,
  a **Schedule** button in the editor's sticky save bar, i18n (en + pt).

**Out of scope (deferred)**
- A general cron/job framework or per-post recurring schedules. One fixed-delay
  sweep, two one-shot timestamps.
- Timezone pickers / per-site business calendars, timestamps are absolute
  instants; the UI uses the browser's local time for entry only.
- A schedule queue/audit UI or "scheduled" content listing. The editor dialog and
  the post's two timestamps are the surface.
- The SH13 expired-preview-token purge, SH15 only ships the `@EnableScheduling`
  switch it can later hang off; the purge itself stays opportunistic.

## 3. Data model

Two nullable columns on `sh_post_unified` (so both rows *can* carry them, but only
the `DRAFT` row is written/scanned):

```java
private Date publishScheduledDate;     // when set: publish the draft at/after this instant
private Date unpublishScheduledDate;   // when set: unpublish the post at/after this instant
```

`Date` (not `Instant`) for symmetry with the existing `modifiedDate` /
`publicationDate` columns. Liquibase type `TIMESTAMP` (DB-agnostic, the
`ShLiquibaseChangelogAgnosticTest` forbids `TIMESTAMP WITH TIME ZONE`).

## 4. Backend: schedule + sweep

`ShPostUnifiedWriteService`:

```java
@Transactional
public ShPostUnified schedule(String postId, Date publishAt, Date unpublishAt, String modifier) {
    ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
        .orElseGet(() -> draftFromPublished(postId));   // 404 if neither exists
    draft.setPublishScheduledDate(publishAt);
    draft.setUnpublishScheduledDate(unpublishAt);
    draft.setModifiedDate(new Date());
    if (modifier != null) draft.setModifier(modifier);
    return shPostUnifiedRepository.save(draft);
}

@Transactional(readOnly = true)
public List<String> findScheduledPublishDue(Date now) { ... DRAFT rows, publishScheduledDate <= now ... }

@Transactional(readOnly = true)
public List<String> findScheduledUnpublishDue(Date now) { ... DRAFT rows, unpublishScheduledDate <= now ... }

@Transactional
public void publishScheduled(String postId) {
    shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
        .ifPresent(d -> { d.setPublishScheduledDate(null); shPostUnifiedRepository.save(d); });
    publish(postId, SCHEDULER_ACTOR);          // reuses SH12 PUBLISH webhook
}

@Transactional
public void unpublishScheduled(String postId) {
    shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
        .ifPresent(d -> { d.setUnpublishScheduledDate(null); shPostUnifiedRepository.save(d); });
    unpublish(postId);                          // reuses SH12 UNPUBLISH webhook (no-op if not published)
}
```

The schedule field is cleared **first**, in the same transaction as the
transition, so a post never re-fires on the next sweep even if the webhook leg is
slow (the webhook is itself off-thread and after-commit).

`ShPostScheduler`:

```java
@Component
public class ShPostScheduler {
    @Scheduled(fixedDelayString = "${shio.schedule.sweep-interval-millis:60000}")
    public void sweep() {
        if (!properties.isEnabled()) return;
        Date now = new Date();
        for (String id : writeService.findScheduledPublishDue(now)) {
            try { writeService.publishScheduled(id); }
            catch (Exception e) { log.warn("Scheduled publish failed for {}: {}", id, e.getMessage()); }
        }
        for (String id : writeService.findScheduledUnpublishDue(now)) {
            try { writeService.unpublishScheduled(id); }
            catch (Exception e) { log.warn("Scheduled unpublish failed for {}: {}", id, e.getMessage()); }
        }
    }
}
```

Each `publishScheduled`/`unpublishScheduled` call goes through the Spring proxy
(separate bean), so each runs in its own transaction → one bad post is logged and
skipped, the rest still fire, and each commit triggers its own SH12 webhook.

## 5. Config (`shio.schedule.*`)

`ShScheduleProperties` (`@ConfigurationProperties("shio.schedule")`):
```
shio.schedule.enabled=true
shio.schedule.sweep-interval-millis=60000
```
`@EnableScheduling` on `com.viglet.shio.Shio`. The interval is read at startup via
the `fixedDelayString` placeholder; `enabled=false` makes the sweep a cheap no-op
(the timer still ticks but returns immediately).

## 6. API (`/api/v2/post-unified/{id}/schedule`)

```java
@PostMapping("/{id}/schedule")
public ResponseEntity<ShPostUnified> schedule(@PathVariable String id,
                                              @RequestBody SchedulePayload payload,
                                              Principal principal) {
    try {
        return ResponseEntity.ok(shPostUnifiedWriteService.schedule(
            id, payload.publishAt(), payload.unpublishAt(),
            principal != null ? principal.getName() : null));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.notFound().build();
    }
}

public record SchedulePayload(Date publishAt, Date unpublishAt) {}
```

Both fields are optional/nullable; sending `null` for one clears it. Falls under
the existing `anyRequest authenticated` rule (console-only).

## 7. Frontend: schedule dialog

`ShPostUnified` gains `publishScheduledDate?` / `unpublishScheduledDate?`.

`ShPostUnifiedService.schedule`:
```ts
async schedule(id: string, req: { publishAt: string | null; unpublishAt: string | null }) {
  const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/schedule`, req)
  return response.data
}
```

`PostScheduleDialog` (`src/components/post-schedule.dialog.tsx`), props
`{ postId, publishAt, unpublishAt, open, setOpen, onScheduled }`:
- Two `datetime-local` inputs (Publish at / Unpublish at), pre-filled from the
  current values (converted to local-input format), each with a clear button.
- Helper text noting the sweep granularity (the configured interval; copy says
  "within a minute").
- **Save schedule** posts both values (empty input → `null`), toasts, calls
  `onScheduled(updatedDraft)`, closes.
- Converts `datetime-local` (no timezone) to an ISO string on submit and back for
  display; empty stays `null`.

`post.edit.page.tsx`: a **Schedule** `GradientButton` (`variant="outline"`,
`Clock` icon) in the `actions` slot opens the dialog; `onScheduled` refreshes the
in-memory post so the badges reflect the new schedule.

## 8. i18n keys (`content.*`, en + pt)

`schedule`, `schedulePublishUnpublish`, `publishAt`, `unpublishAt`,
`scheduleHint`, `saveSchedule`, `scheduleSaved`, `scheduleError`, `clear`,
`scheduledFor` (`{{date}}`).

## 9. Acceptance criteria

1. `POST /api/v2/post-unified/{id}/schedule` (authenticated) with `publishAt` in
   the past on a draft → the next sweep publishes the post (a `PUBLISHED` row
   appears, `published=true`) and the draft's `publishScheduledDate` is cleared.
2. A scheduled publish fires exactly one SH12 `PUBLISH` webhook to each matching
   subscription, identical to a manual publish.
3. `unpublishAt` in the past on a published post → the next sweep removes the
   `PUBLISHED` row and fires the SH12 `UNPUBLISH` webhook; `unpublishScheduledDate`
   is cleared.
4. A schedule with a **future** timestamp is **not** fired by a sweep until that
   instant passes.
5. Sending `null` for a field clears that schedule; clearing both leaves the post
   unscheduled.
6. `shio.schedule.enabled=false` makes the sweep a no-op (nothing fires) even with
   due rows.
7. Scheduling a post with no `DRAFT` row but a `PUBLISHED` row creates the draft
   (cloned from published) carrying the schedule; scheduling an unknown id → 404.
8. One post that throws during its transition does not prevent other due posts in
   the same sweep from firing (per-post transaction).

## 10. Tests

- `ShPostUnifiedAPITest` (extend the staged flow, after restore/before unpublish):
  - schedule the post with `publishAt` in the past, invoke the sweep
    (`ShPostScheduler.sweep()` autowired, or call
    `writeService.findScheduledPublishDue`+`publishScheduled` directly), assert a
    `PUBLISHED` row now exists and `publishScheduledDate` is null. (crit 1)
  - schedule `unpublishAt` in the past, sweep, assert the `PUBLISHED` row is gone.
    (crit 3)
  - `POST /{id}/schedule` on an unknown id → `404`. (crit 7)
  - a `publishAt` in the **future** is not picked up by `findScheduledPublishDue(now)`.
    (crit 4)

## 11. Estimate

~0.75 day: entity columns + migration (1h), schedule/sweep service + scheduler +
properties + `@EnableScheduling` (2.5h), endpoint (0.5h), service + dialog + editor
button + i18n (2.5h), tests (1h).
