// viglet-ds-allow-duplicate UserProvider -- a typed bridge over the package's provider, not a
// second implementation of it: it mounts VigUserProvider, feeds it Shio's service, and republishes
// the result as ShCurrentUser so authorization.service.ts and HeaderUserMenu keep the required
// `privileges` that VigUser leaves optional. Both contexts resolve from ONE fetch (SH920).
// viglet-ds-allow-duplicate useCurrentUser -- the accessor for that bridge's context, returning
// Shio's value type. It cannot move while the type does not.
import type { ShCurrentUser } from "@/models/auth/current-user"
import { ShUserService } from "@/services/auth/user.service"
import {
  UserProvider as VigUserProvider,
  useCurrentUser as useVigCurrentUser,
} from "@viglet/viglet-design-system"
import React from "react"

/**
 * SH920 — one fetch, two contexts, and the seam every Block AR surface crosses.
 *
 * ## What was actually wrong
 *
 * SH894 exempted this provider from the duplicate gate on good grounds, and SH888 then found
 * what the exemption cost. `BentoUserMenu` — and every other shared component that knows who is
 * signed in — calls the **package's** `useCurrentUser`. A local variant of a context is a
 * *different context object*, so mounting this one around a shared component satisfies nothing:
 * the bento shell came up entirely blank behind the error boundary, and `tsc` had said nothing,
 * because both are named `UserProvider` and both take children.
 *
 * The bento shell worked around it by mounting the package's provider directly. That left the
 * console tree on one context and the bento tree on the other — fine exactly until a Block AR
 * task moved a page calling *this* hook, at which point it throws the same way on a surface a
 * curator uses. Fourteen surfaces, fourteen chances.
 *
 * ## Wrapping rather than replacing, and why not the other way round
 *
 * `ShCurrentUser` requires `privileges`; `VigUser` leaves it optional.
 * `authorization.service.ts` reads it to decide what a curator may do, so folding the types
 * would push a guard into a privilege check — the one place a wrong answer is worst. So this
 * stays the type Shio's own code reads, and becomes a **bridge**: the package's provider does
 * the fetching, this republishes what it produced.
 *
 * One fetch, not two. That is the whole point of the nesting: mounting both providers side by
 * side would satisfy both hooks and ask the backend the same question twice on every page load.
 *
 * ## What Shio gives up by not implementing it, which turns out to be the good part
 *
 * The 24 lines this replaced were `shUserService.get().then(setUser)` — no catch, so an expired
 * session or an unreachable API became an unhandled rejection and left `user` an empty object no
 * consumer could read a state off; and no request sequencing, so two overlapping `refreshUser`
 * calls let the slower one write over the newer. The package's provider handles both, and
 * carries `status` and `error`, which are passed straight through here because a caller that can
 * see "failed" can say so.
 */
interface UserContextValue {
  user: ShCurrentUser
  refreshUser: () => void
  /**
   * `loading` means "has never settled" — a background refresh keeps its last settled value.
   *
   * Derived from the hook rather than imported: the package declares `VigUserStatus` in its
   * context module and does not re-export it from the root barrel, and a hand-written copy of a
   * three-member union is a copy that goes stale silently.
   */
  status: ReturnType<typeof useVigCurrentUser>["status"]
  error: unknown
}

const UserContext = React.createContext<UserContextValue | null>(null)

const shUserService = new ShUserService()

/** Republishes the package's context as Shio's, so one fetch answers both hooks. */
function ShUserBridge({ children }: { children: React.ReactNode }) {
  const { user, refreshUser, status, error } = useVigCurrentUser()

  const value = React.useMemo(
    // The cast is the bridge's whole job and is safe in this direction: Shio's `fetchUser` below
    // returns a ShCurrentUser, so what the package holds is one — it simply describes it with a
    // wider type because it cannot know the product's model.
    () => ({ user: user as ShCurrentUser, refreshUser, status, error }),
    [user, refreshUser, status, error],
  )

  return <UserContext value={value}>{children}</UserContext>
}

export function UserProvider({ children }: { children: React.ReactNode }) {
  const fetchUser = React.useCallback(() => shUserService.get(), [])

  return (
    <VigUserProvider fetchUser={fetchUser}>
      <ShUserBridge>{children}</ShUserBridge>
    </VigUserProvider>
  )
}

export function useCurrentUser(): UserContextValue {
  const ctx = React.useContext(UserContext)
  if (!ctx) throw new Error("useCurrentUser must be used within UserProvider")
  return ctx
}
