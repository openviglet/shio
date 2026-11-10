/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1421 — where this instance's MCP endpoint is, as the agent's machine will reach it.
 *
 * The API base is `${VITE_API_URL}/api`: empty in a built console, so the page's own origin, and the
 * backend's origin under the Vite dev server. `/mcp` sits beside `/api`, not under it.
 */
export function mcpEndpoint(apiBase: string | undefined, origin: string): string {
  const root = (apiBase ?? "/api").replace(/\/api\/?$/, "")
  return new URL(`${root}/mcp`, origin).href
}

/** SH1421 — the one line that registers this instance in Claude Code, with the key already in it. */
export function claudeCommand(endpoint: string, token: string): string {
  return `claude mcp add --transport http shio ${endpoint} --header "Key: ${token}"`
}
