import React, { createContext, useCallback, useContext, useEffect, useState } from "react"

const STORAGE_KEY = "sh-use-unified-editor"

interface UnifiedEditorContextValue {
  enabled: boolean
  setEnabled: (on: boolean) => void
}

const UnifiedEditorContext = createContext<UnifiedEditorContextValue | undefined>(undefined)

export function UnifiedEditorProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [enabled, setEnabledState] = useState(() => {
    if (typeof window === "undefined") return false
    return window.localStorage.getItem(STORAGE_KEY) === "1"
  })

  const setEnabled = useCallback((on: boolean) => {
    setEnabledState(on)
    try {
      window.localStorage.setItem(STORAGE_KEY, on ? "1" : "0")
    } catch {
      // localStorage unavailable — keep state in memory
    }
  }, [])

  useEffect(() => {
    function handleStorage(event: StorageEvent) {
      if (event.key === STORAGE_KEY) {
        setEnabledState(event.newValue === "1")
      }
    }
    window.addEventListener("storage", handleStorage)
    return () => window.removeEventListener("storage", handleStorage)
  }, [])

  return (
    <UnifiedEditorContext.Provider value={{ enabled, setEnabled }}>
      {children}
    </UnifiedEditorContext.Provider>
  )
}

export function useUnifiedEditor(): UnifiedEditorContextValue {
  const ctx = useContext(UnifiedEditorContext)
  if (!ctx) throw new Error("useUnifiedEditor must be used within UnifiedEditorProvider")
  return ctx
}
