/*
 * SH894 — a shim, because this was a stale copy and not a variant.
 *
 * The 91 lines that used to be here were the confirm-to-delete dialog, and the package's version
 * is the same dialog with two differences: it uses `GradientButton` where this used `Button`, and
 * it adds a `blockedBy` branch that names what is still referencing the thing you are trying to
 * delete. It reads **the same i18n keys** — `dialog.deleteFeature`, `dialog.permanentDelete`,
 * `dialog.typeToConfirm`, `dialog.toConfirm`, `common.cancel` — all of which this console already
 * ships in `en` and `pt`, so the swap changes no text. The `blockedBy` branch's own keys travel
 * with the package.
 *
 * This was found by wiring the package's duplicate gate into this repository's own suite, not by
 * anyone reading the file: it had drifted behind the shared one and eighteen pages were rendering
 * the older dialog, none of which could offer the "cannot delete, still in use" explanation the
 * shared one has.
 */
export { DialogDelete } from "@viglet/viglet-design-system/router"
