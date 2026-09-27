import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { useInvalidate } from "@/hooks/use-catalogue"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Bot } from "lucide-react"
import { useChangeStream } from "@/hooks/use-change-stream"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShWorking } from "@/models/history/history.model"

const service = new ShHistoryService()

/**
 * SH1008 — somebody else is on this page, said before anyone types.
 *
 * <p>SH985 makes a conflicting save fail loudly instead of silently. That is necessary and it
 * arrives late: the curator finds out after writing the paragraph. The second editor on a Shio page
 * is usually a machine, and what both parties need is not to type into one document — the non-goals
 * rule out a shared-document runtime — but to know the other is here.</p>
 *
 * <h2>Why it clears on a timer and not only on an event</h2>
 *
 * <p>The mark is derived from writes, so its end has no event: a run that stops writing sends
 * nothing, and a line waiting for news that has no reason to arrive is the stale-presence bug every
 * declared-presence system has. The server says <code>until</code>, and this clears itself then.
 * SH967's stream only ever makes it re-ask, which is the same rule the Activity page follows — the
 * server decides what "working" means, and a screen that computed it from stream rows would be a
 * second answer to one question.</p>
 *
 * <p><b>It is not a lock.</b> Nothing here disables a field, a Save or a Publish.</p>
 */
export function WorkingLine({ objectId }: Readonly<{ objectId: string }>) {
  const { t } = useTranslation()
  const [mark, setMark] = useState<ShWorking | null>(null)

  /*
   * SH1155 — one read per object, cancelled when it changes or the line unmounts. `ask` stays a
   * function because `useChangeStream` calls it when the server says this object moved; it is an
   * invalidation now rather than a second way to make the same request.
   */
  const read = useQuery<ShWorking | null>({
    queryKey: addressKey(`summary:working|${objectId}`),
    queryFn: ({ signal }) => service.working(objectId, { signal }),
    enabled: Boolean(objectId),
  })
  const invalidate = useInvalidate()
  const ask = useCallback(
    () => { void invalidate(`summary:working|${objectId}`) }, [invalidate, objectId])

  /*
   * `mark` is state rather than the query's data, and that is the point of the timer below: when the
   * claim expires this component clears it locally without asking the server again, so the line goes
   * away at the moment it stops being true. Seeded during render, once per answer.
   */
  const [seeded, setSeeded] = useState<ShWorking | null | undefined>(undefined)
  if (read.isSuccess && seeded !== read.data) {
    setSeeded(read.data)
    setMark(read.data)
  }

  useChangeStream(ask)

  useEffect(() => {
    if (!mark) return undefined
    const left = new Date(mark.until).getTime() - Date.now()
    // Already past: clear on the next tick rather than scheduling a timer for the past, and never
    // synchronously, which would be a setState during the render that read it.
    const timer = setTimeout(() => setMark(null), Math.max(0, left))
    return () => clearTimeout(timer)
  }, [mark])

  if (!mark) return null

  const who = mark.agentName ?? mark.session.slice(0, 8)
  return (
    <div
      className="flex items-center gap-2 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground"
      // The line appears without the curator doing anything, so it is announced rather than
      // arriving silently under their hands. Polite: it is context, not an interruption.
      aria-live="polite"
    >
      <Bot className="h-4 w-4 shrink-0" />
      <span>
        {mark.intent
          ? t("activity.workingFor", { who, intent: mark.intent })
          : t("activity.working", { who })}
      </span>
    </div>
  )
}
