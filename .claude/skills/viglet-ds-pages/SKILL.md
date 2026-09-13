---
name: viglet-ds-pages
description: Building or changing a page with @viglet/viglet-design-system/bento -- the shell, the three page shapes, the panel, and which tokens a product claims. Load before the first screen, and before adding a region to one.
vds-version: 2026.3.10
---

# Pages in the Viglet design system

This is vendored from `@viglet/viglet-design-system@2026.3.10` and refreshed by
`viglet-ds-page-reference`. Do not edit these files here: the next run rewrites them, and
a correction belongs in the package so every consumer gets it.

## Read first

- **[authoring.md](authoring.md)** -- the contract. The shell and who owns each region, the
  three page shapes, the panel, colour and the tokens a product claims, i18n, accessibility,
  responsive, tests. Read it before the first screen, not after the fifth.
- **[boundary.md](boundary.md)** -- which components are the shared layer and which stay in
  a product, and why.

## And look at

`docs/design/vds-*.dc.html` -- 9 artboards, the contract drawn: every region of a page
with an ownership key, the reading column owned two ways, what `--primary` reaches, the page
shapes, the panel, and both grounds with their ratios. They are laid out by
`docs/design/canvas.json`, whose `vds-` pages and artboards belong to the package; everything
else in that file is this repository's own and is preserved.

An artboard opens in a browser straight from the file tree.

## Two rules that are easy to get wrong

- A page sets no max width, no gutters and no vertical rhythm. The shell sets them once.
- A product claims `--primary` through the four `--vg-primary-*-base` inputs at `:root`,
  never by setting `--vg-primary` itself, which would key the dark ground to the light value.

## Checking it is current

```bash
viglet-ds-page-reference --check
```
