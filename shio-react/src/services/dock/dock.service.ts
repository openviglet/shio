import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** The report kinds a curator can mute (`ShDockPreferencesService.KINDS`). */
export const DOCK_KINDS = ["saves", "agent-runs", "schedules", "arrivals"] as const
export type DockKind = (typeof DOCK_KINDS)[number]

export interface ShDockPreferences {
  muted: DockKind[]
  /** SH971 — the mascot stays still; the caption and the announcement report as before. */
  still: boolean
}

/** SH971 — the signed-in curator's dock preferences, kept on their user record. */
export class ShDockService {
  async preferences(read: ShRead = {}): Promise<ShDockPreferences> {
    const response = await axios.get<ShDockPreferences>("/v2/user/current/dock", { signal: read.signal })
    return response.data
  }

  async savePreferences(preferences: ShDockPreferences): Promise<ShDockPreferences> {
    const response = await axios.put<ShDockPreferences>("/v2/user/current/dock", preferences)
    return response.data
  }
}
