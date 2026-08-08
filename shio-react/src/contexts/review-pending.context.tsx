import React from "react"

import { ShReviewService } from "@/services/review/review.service"

const service = new ShReviewService()

interface ReviewPendingContextValue {
  /** Agent runs still awaiting a decision. `0` means the badge is not drawn. */
  pending: number
  /** Re-read it — called by the review queue after an approve or a revert. */
  refresh: () => void
}

const ReviewPendingContext = React.createContext<ReviewPendingContextValue | null>(null)

/**
 * How much agent work is waiting, for whoever needs to show it (SH207).
 *
 * <h2>Why a context and not a fetch in the sidebar</h2>
 *
 * The number is read in one place (the navigation badge) and invalidated in another (the review
 * queue, when a run is approved or reverted). Fetching it inside the sidebar makes those two
 * unable to reach each other: the badge would say two while the page it opens showed nothing
 * pending, until the next navigation happened to remount it. A badge that disagrees with the page
 * it links to is worse than no badge — it is the one number a curator would learn to distrust.
 *
 * <h2>Not polled</h2>
 *
 * It refreshes on mount and when a review action changes it. An interval would ask an instance a
 * question on behalf of a person who is not looking, forever, to shorten a window that only
 * matters when somebody else's agent is writing while this curator watches the sidebar — which is
 * a real case and a rare one, and the page has its own refresh button for it.
 */
export function ReviewPendingProvider({ children }: { children: React.ReactNode }) {
  const [pending, setPending] = React.useState(0)

  const refresh = React.useCallback(() => {
    service
      .pending()
      .then((result) => setPending(result.sessions))
      // Silent: a badge is not worth a toast, and an instance that cannot answer this has
      // louder problems the rest of the console will already be reporting.
      .catch(() => setPending(0))
  }, [])

  React.useEffect(() => {
    refresh()
  }, [refresh])

  const value = React.useMemo(() => ({ pending, refresh }), [pending, refresh])
  return <ReviewPendingContext value={value}>{children}</ReviewPendingContext>
}

/**
 * The count, and a way to invalidate it.
 *
 * Returns a zero-and-no-op default outside the provider rather than throwing, unlike
 * {@link useTenant}: a missing tenant makes a page wrong, and a missing badge makes a page
 * plain. Nothing should fail to render because an indicator has nowhere to read from.
 */
export function useReviewPending(): ReviewPendingContextValue {
  return React.useContext(ReviewPendingContext) ?? { pending: 0, refresh: () => {} }
}
