import axios from "@/lib/axios"
import type { ShNote, ShNoteInput, ShOpenNote } from "@/models/note/note.model"
import type { ShRead } from "@/lib/read-options"

/**
 * Notes on content (SH1003).
 *
 * The console's half of a channel the agent reaches through `note.add` and `note.resolve` on
 * `/agent/batch` — one vocabulary, two callers (P10), not a second contract for the same rows.
 */
export class ShNoteService {
  /** One object's thread, oldest first. */
  async of(objectId: string, read: ShRead = {}): Promise<ShNote[]> {
    const response = await axios.get<ShNote[]>("/v2/note", { params: { object: objectId }, signal: read.signal })
    return response.data
  }

  /**
   * SH1214 — every note still open, newest first: the list an agent reads from the same endpoint
   * (`GET /note/open`), and the one home's Open notes tile counts.
   */
  async open(read: ShRead = {}): Promise<ShOpenNote[]> {
    const response = await axios.get<ShOpenNote[]>("/v2/note/open", { signal: read.signal })
    return response.data
  }

  /** SH1006 — whether an agent run started recently enough to answer the dock's chat. */
  async relay(read: ShRead = {}): Promise<{ agentActive: boolean; windowHours: number }> {
    const response = await axios.get<{ agentActive: boolean; windowHours: number }>("/v2/note/relay",
      { signal: read.signal })
    return response.data
  }

  async add(note: ShNoteInput): Promise<ShNote> {
    const response = await axios.post<ShNote>("/v2/note", note)
    return response.data
  }

  /** Close one, or re-open it. */
  async resolve(id: string, resolved = true): Promise<ShNote> {
    const response = await axios.post<ShNote>(
      `/v2/note/${encodeURIComponent(id)}/resolve`,
      undefined,
      { params: { resolved } },
    )
    return response.data
  }
}
