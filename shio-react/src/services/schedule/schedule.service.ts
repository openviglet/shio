import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** One scheduled post as `/agent/find?scheduled=` returns it (SH991). */
export interface ShScheduledRow {
  address: string
  title?: string
  id?: string
  publishAt?: string
  unpublishAt?: string
}

/** One moment on the agenda: a post going live or coming down. */
export interface ShScheduleEntry {
  at: string
  action: "publish" | "unpublish"
  address: string
  title?: string
  postId?: string
  /**
   * SH1082 — the post's whole pair, because the schedule write replaces both: moving the go-live
   * must send the come-down it already has, or the move clears it.
   */
  publishAt?: string
  unpublishAt?: string
}

interface FindPage {
  results?: ShScheduledRow[]
  truncated?: boolean
  cursor?: string
}

/**
 * The console's agenda (SH991), read from the listing an agent reads.
 *
 * <p>Not an endpoint of its own: `/agent/find?scheduled=any` is the one answer to "what is
 * scheduled", and `shio schedule` and `shio_find` read the same one. What the console adds is the
 * order — find sorts by address, its cursor — so the rows are walked to the end and sorted here.</p>
 */
export class ShScheduleService {
  async upcoming(read: ShRead = {}): Promise<ShScheduleEntry[]> {
    const rows: ShScheduledRow[] = []
    let after: string | undefined
    // Bounded, so a server answering the same cursor forever is a short agenda rather than a hang.
    for (let page = 0; page < 50; page++) {
      const response = await axios.get<FindPage>("/v2/agent/find", {
        signal: read.signal,
        params: { scheduled: "any", ids: true, limit: 500, after },
      })
      rows.push(...(response.data.results ?? []))
      const next = response.data.cursor
      if (!response.data.truncated || !next || next === after) break
      after = next
    }
    return agendaOf(rows)
  }
}

/**
 * One entry per scheduled transition, earliest first.
 *
 * A post due to go live and to come down is two entries, because they are two moments. ISO-8601
 * instants in UTC compare as strings, and the address breaks a tie so two renders agree.
 */
export function agendaOf(rows: ShScheduledRow[]): ShScheduleEntry[] {
  const entries: ShScheduleEntry[] = []
  for (const row of rows) {
    const pair = { publishAt: row.publishAt, unpublishAt: row.unpublishAt }
    if (row.publishAt) {
      entries.push({ at: row.publishAt, action: "publish", address: row.address, title: row.title, postId: row.id, ...pair })
    }
    if (row.unpublishAt) {
      entries.push({ at: row.unpublishAt, action: "unpublish", address: row.address, title: row.title, postId: row.id, ...pair })
    }
  }
  return entries.sort((a, b) => a.at.localeCompare(b.at) || a.address.localeCompare(b.address))
}
