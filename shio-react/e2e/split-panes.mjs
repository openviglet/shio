/*
 * SH1094, SH1331 — what a BentoSplitPage shows: its own visible panes and its own switch.
 *
 * Self-contained on purpose: console-check.mjs hands it to `page.evaluate`, which serialises the
 * function and runs it in the page, so it may use nothing but the page's globals.
 * `split-panes.test.mjs` runs the same function over both of the split's DOM shapes.
 */
export function splitPanesOf() {
  const split = document.querySelector("[data-slot=bento-split-page]")
  const visible = (node) => node.getClientRects().length > 0 && getComputedStyle(node).visibility !== "hidden"
  // SH1331 — the split's own panes and switch, not what a pane holds: the editor's second pane
  // carries its Notes, History and Insights tabs, and counting those read 3 panes and 3 tabs at
  // 1440px for a layout that was two panes and no switch. A pane is one no other pane contains;
  // the switch is the tab list that is the split's own child.
  const PANE = "section[aria-label], [role=tabpanel]"
  const outermost = (node) => {
    const outer = node.parentElement?.closest(PANE)
    return !outer || !split.contains(outer)
  }
  return {
    split: Boolean(split),
    panes: split ? [...split.querySelectorAll(PANE)].filter(outermost).filter(visible).length : 0,
    tabs: split ? [...split.querySelectorAll(":scope > [role=tablist] [role=tab]")].filter(visible).length : 0,
  }
}
