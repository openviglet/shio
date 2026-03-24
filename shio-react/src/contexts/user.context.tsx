import type { ShCurrentUser } from "@/models/auth/current-user"
import { ShUserService } from "@/services/auth/user.service"
import React from "react"

interface UserContextValue {
  user: ShCurrentUser
  refreshUser: () => void
}

const UserContext = React.createContext<UserContextValue | null>(null)

const shUserService = new ShUserService()

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = React.useState<ShCurrentUser>({} as ShCurrentUser)

  const refreshUser = React.useCallback(() => {
    shUserService.get().then(setUser)
  }, [])

  React.useEffect(() => {
    refreshUser()
  }, [refreshUser])

  const value = React.useMemo(() => ({ user, refreshUser }), [user, refreshUser])

  return <UserContext value={value}>{children}</UserContext>
}

export function useCurrentUser(): UserContextValue {
  const ctx = React.useContext(UserContext)
  if (!ctx) throw new Error("useCurrentUser must be used within UserProvider")
  return ctx
}
