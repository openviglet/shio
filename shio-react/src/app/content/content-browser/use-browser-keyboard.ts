/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1020/SH1145 — the listbox: a roving focus ring, and every key that moves or acts on it.
 *
 * <p>A listbox puts exactly one row in the tab order, so Tab moves past the list rather than
 * through a hundred rows. Everything else is an arrow: that is the contract a screen reader
 * announces when it reads `role="listbox"`, and a list that claims the role and does not move on
 * arrows is worse than one that claims nothing.</p>
 *
 * <p>SH1145 — out of the page because its dependencies are now the whole of its inputs. In a
 * 1,300-line component they were not: SH1026's live check found the listing effect re-reading page
 * 0 after every render, from a value built in the component body, and nothing in a file that size
 * made it visible. Six pieces of state, three refs and five callbacks that only ever talk to each
 * other are exactly the shape that should be readable on its own.</p>
 */
import { useCallback, useEffect, useRef, useState } from "react"

import { stepFor, type BrowserRow, type BrowserView } from "./browser-view"
import { useRowWindow } from "./use-row-window"

/** What the page hands in: the rows, how they are laid out, and what a key ends up doing. */
export interface BrowserKeyboard {
  rows: readonly BrowserRow[]
  view: BrowserView
  /** Space toggles one row. */
  toggleSelect: (id: string) => void
  /** Shift with an arrow, or a Shift-click, adds a run of ids. */
  addToSelection: (ids: string[]) => void
  /** Enter, or a double click. */
  onOpen: (row: BrowserRow) => void
  /** Delete and Backspace — through the confirm flow, never straight to the server. */
  onDelete: (row: BrowserRow) => void
}

export function useBrowserKeyboard({
  rows, view, toggleSelect, addToSelection, onOpen, onDelete,
}: BrowserKeyboard) {
  const [focusedRow, setFocusedRow] = useState(0)
  const [anchorRow, setAnchorRow] = useState(0)
  const rowRefs = useRef<(HTMLDivElement | null)[]>([])
  const searchRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)

  /*
   * SH1026 — past a few pages the list and the grid mount only the rows a reader can see. The
   * keyboard can still reach any row: focusing one that is not mounted scrolls it into the window
   * first, and the effect below focuses it once it has rendered.
   */
  const { window: rowWindow, reveal } = useRowWindow(listRef, rows.length, view)
  const tabStop = focusedRow >= rowWindow.start && focusedRow < rowWindow.end
    ? focusedRow
    : rowWindow.start
  const pendingFocus = useRef<number | null>(null)
  useEffect(() => {
    const pending = pendingFocus.current
    if (pending !== null && rowRefs.current[pending]) {
      rowRefs.current[pending]?.focus()
      pendingFocus.current = null
    }
  })

  const focusRow = useCallback((index: number) => {
    const bounded = Math.max(0, Math.min(index, rows.length - 1))
    setFocusedRow(bounded)
    const row = rowRefs.current[bounded]
    if (row) {
      row.focus()
    } else {
      pendingFocus.current = bounded
      reveal(bounded)
    }
  }, [rows.length, reveal])

  /** Every id from the anchor to here — what makes a bulk verb a gesture rather than N clicks. */
  const selectRange = useCallback((from: number, to: number) => {
    const [low, high] = from <= to ? [from, to] : [to, from]
    addToSelection(rows.slice(low, high + 1).map((item) => item.id))
  }, [rows, addToSelection])

  const onListKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const index = focusedRow
    const item = rows[index]
    // SH1026 — a grid is read across as well as down, so there left and right step too.
    switch (event.key) {
      case "ArrowDown":
      case "ArrowUp":
      case "ArrowRight":
      case "ArrowLeft": {
        const step = stepFor(view, event.key)
        if (step === 0) break
        event.preventDefault()
        const next = index + step
        focusRow(next)
        // Shift extends from the anchor, which is where the selection last started — the same
        // rule every file manager uses, and the reason the anchor is state rather than the
        // previous row.
        if (event.shiftKey) selectRange(anchorRow, next)
        else setAnchorRow(Math.max(0, Math.min(next, rows.length - 1)))
        break
      }
      case "Home":
        event.preventDefault()
        focusRow(0)
        break
      case "End":
        event.preventDefault()
        focusRow(rows.length - 1)
        break
      case " ":
        event.preventDefault()
        if (item) {
          toggleSelect(item.id)
          setAnchorRow(index)
        }
        break
      case "Enter":
        event.preventDefault()
        if (item) onOpen(item)
        break
      case "Delete":
      case "Backspace":
        event.preventDefault()
        // Through the confirm flow, never straight to the server: Delete is one keystroke from
        // every other key on this list.
        if (item) onDelete(item)
        break
      default:
        break
    }
  }

  /** Shift-click extends, exactly as Shift with an arrow does. */
  const onRowClick = (event: React.MouseEvent<HTMLDivElement>, index: number) => {
    if (event.shiftKey) {
      event.preventDefault()
      selectRange(anchorRow, index)
      setFocusedRow(index)
      return
    }
    setAnchorRow(index)
  }

  /**
   * SH1020 — `/` focuses search, which is the one binding that is not about the list.
   *
   * <p>Ignored while a field has focus, or typing a slash into a name would jump the cursor out of
   * the box it was typed in.</p>
   */
  useEffect(() => {
    const onSlash = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey) return
      const active = document.activeElement
      const editing = active instanceof HTMLInputElement
        || active instanceof HTMLTextAreaElement
        || (active as HTMLElement | null)?.isContentEditable
      if (editing) return
      event.preventDefault()
      // SH1024 — the bar's own text field, which is always on screen now.
      searchRef.current?.querySelector("input")?.focus()
    }
    globalThis.addEventListener("keydown", onSlash)
    return () => globalThis.removeEventListener("keydown", onSlash)
  }, [])

  return {
    focusedRow, setFocusedRow, anchorRow, rowRefs, searchRef, listRef, rowWindow, tabStop,
    focusRow, onListKeyDown, onRowClick,
  }
}
