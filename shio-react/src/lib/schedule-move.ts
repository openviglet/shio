/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShScheduleEntry } from "../services/schedule/schedule.service.ts"

/** The calendar's id for an entry: a post has at most one of each transition, so the pair is unique. */
export function entryKey(entry: Pick<ShScheduleEntry, "action" | "address">): string {
  return `${entry.action}|${entry.address}`
}

/**
 * SH1082 — what the schedule write is sent when an entry is dropped on another day.
 *
 * The moved side takes the new instant and the other keeps the one it had, since the endpoint
 * replaces both and null clears. The calendar has already kept the time of day.
 */
export function movedSchedule(entry: ShScheduleEntry, start: Date): { publishAt: string | null; unpublishAt: string | null } {
  const moved = start.toISOString()
  return entry.action === "publish"
    ? { publishAt: moved, unpublishAt: entry.unpublishAt ?? null }
    : { publishAt: entry.publishAt ?? null, unpublishAt: moved }
}
