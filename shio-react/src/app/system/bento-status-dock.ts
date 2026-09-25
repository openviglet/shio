/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createElement } from "react"
import { VigletAssistant, useAssistantNotifications } from "@viglet/viglet-design-system"

/**
 * SH964 — the Viglet assistant, mounted as a status surface in the shell's corner.
 *
 * <p>The console reports every outcome through the package's `toast`, about a hundred calls, and
 * none of them had to change: `useAssistantNotifications` reads the live toast list rather than
 * patching `toast`, so each one moves the mascot and becomes the dock's caption. There is no
 * `onSend`, so the dock has no composer and no chat. Shio hosts no model, and a chat box with
 * nothing behind it would be a promise the product cannot keep.</p>
 *
 * <p>The mascot is decorative and hidden from assistive technology, and the caption is a live
 * region, so each outcome is announced once. The shell renders this in its `dock` slot, in the
 * corner stack below back-to-top (VDS133), so neither covers the other.</p>
 *
 * <p>Plain `.ts` with `createElement` on purpose: node's type stripping does not read JSX, and this
 * way `bento-status-dock.test.mjs` mounts the component itself rather than a copy of it.</p>
 */
export function BentoStatusDock() {
  const { state, caption, activity } = useAssistantNotifications()
  return createElement(VigletAssistant, { state, caption, activity })
}
