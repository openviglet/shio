/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useRef, useState, type RefObject } from "react"
import { fullWindow, visibleWindow, WINDOW_THRESHOLD, type RowWindow } from "./browser-view"

/** The nearest ancestor that scrolls, which is what moves the rows: the shell's, or the page's. */
function scrollParent(node: HTMLElement | null): HTMLElement {
  for (let current = node?.parentElement ?? null; current; current = current.parentElement) {
    const overflow = getComputedStyle(current).overflowY
    if ((overflow === "auto" || overflow === "scroll") && current.scrollHeight > current.clientHeight) return current
  }
  return (document.scrollingElement as HTMLElement | null) ?? document.documentElement
}

/** One row's height with its gap, and how many options a row holds, read off the rendered list. */
function geometry(list: HTMLElement): { rowHeight: number; columns: number } {
  const style = getComputedStyle(list)
  const tracks = style.display === "grid" ? style.gridTemplateColumns.split(" ").filter(Boolean).length : 1
  // SH1054 — a grid row now, not a listbox option.
  const option = list.querySelector<HTMLElement>('[role="row"]')
  const gap = Number.parseFloat(style.rowGap) || 0
  return { rowHeight: option ? option.offsetHeight + gap : 0, columns: Math.max(tracks, 1) }
}

/**
 * SH1026 — the list's and the grid's visible window, above {@link WINDOW_THRESHOLD} rows.
 *
 * Measured from the rendered list rather than declared, because a row's height is the view's and
 * the grid's column count is its CSS's; declaring either would be a second copy that drifts. The
 * state is only ever set from a scroll, a resize or an animation frame — never synchronously in an
 * effect's body, which is the render cascade SH1075 paid off.
 *
 * `reveal(index)` scrolls a row into the window, so the keyboard can reach a row that is not
 * mounted: the arrow moves the focus index, this brings the row into range, and the page focuses it
 * once it renders.
 */
export function useRowWindow(listRef: RefObject<HTMLElement | null>, count: number, view: string): {
  window: RowWindow
  reveal: (index: number) => void
} {
  const enabled = count > WINDOW_THRESHOLD
  const [measured, setMeasured] = useState<RowWindow | null>(null)
  const shape = useRef({ rowHeight: 0, columns: 1 })

  const measure = useCallback(() => {
    const list = listRef.current
    if (!list) return
    shape.current = geometry(list)
    setMeasured(visibleWindow({
      count, columns: shape.current.columns, rowHeight: shape.current.rowHeight,
      top: list.getBoundingClientRect().top, viewportHeight: globalThis.innerHeight,
    }))
  }, [listRef, count])

  useEffect(() => {
    if (!enabled) return
    let frame = 0
    const schedule = () => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(measure)
    }
    schedule()
    // Capturing, because the shell may scroll an element of its own rather than the document.
    document.addEventListener("scroll", schedule, true)
    globalThis.addEventListener("resize", schedule)
    return () => {
      cancelAnimationFrame(frame)
      document.removeEventListener("scroll", schedule, true)
      globalThis.removeEventListener("resize", schedule)
    }
  }, [enabled, measure, view])

  const reveal = useCallback((index: number) => {
    const list = listRef.current
    const { rowHeight, columns } = shape.current
    if (!enabled || !list || rowHeight <= 0) return
    const rowTop = list.getBoundingClientRect().top + Math.floor(index / columns) * rowHeight
    const margin = rowHeight * 2
    if (rowTop >= margin && rowTop + rowHeight <= globalThis.innerHeight - margin) return
    scrollParent(list).scrollTop += rowTop - globalThis.innerHeight / 2
  }, [listRef, enabled])

  // A window measured before rows were removed may reach past the end; the next frame corrects it.
  const current = enabled && measured ? { ...measured, end: Math.min(measured.end, count) } : fullWindow(count)
  return { window: current, reveal }
}
