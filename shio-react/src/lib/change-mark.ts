/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { BentoChangeState } from "@viglet/viglet-design-system/bento"

/**
 * SH1178 — a change set's action, as the state `BentoChangeMark` draws (VDS170).
 *
 * The server's vocabulary (`ShAgentChangeSet.Change`) is wider than the mark's four states, so the
 * mapping is decided here: `create` is added, `delete` is removed, `noop` is unchanged (a plan
 * reports it rather than omitting it), and every other action changes an object that stays. A
 * "changed" row also names its action, since a move and a publish are both changes and read very
 * differently.
 */
export function changeStateOf(action: string): BentoChangeState {
  if (action === "create") return "added"
  if (action === "delete") return "removed"
  if (action === "noop") return "unchanged"
  return "changed"
}
