/**
 * Shared widget color mappings used across post-type editor and post form rendering.
 */

/** Badge background + text colors per widget type */
export const WIDGET_BADGE_COLORS: Record<string, string> = {
  "Text": "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300 border-blue-300 dark:border-blue-800",
  "Text Area": "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300 border-indigo-300 dark:border-indigo-800",
  "HTML Editor": "bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 border-purple-300 dark:border-purple-800",
  "Check Box": "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300 border-green-300 dark:border-green-800",
  "Combo Box": "bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-300 border-teal-300 dark:border-teal-800",
  "Date": "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300 border-amber-300 dark:border-amber-800",
  "Hidden": "bg-gray-100 text-gray-600 dark:bg-gray-800/30 dark:text-gray-400 border-gray-300 dark:border-gray-700",
  "Multi Select": "bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-300 border-cyan-300 dark:border-cyan-800",
  "Ace Editor - Javascript": "bg-yellow-100 text-yellow-900 dark:bg-yellow-900/30 dark:text-yellow-300 border-yellow-300 dark:border-yellow-800",
  "Ace Editor - HTML": "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300 border-orange-300 dark:border-orange-800",
  // SH252 — its own colour, because the point of the palette is telling field kinds apart.
  "Ace Editor - CSS": "bg-sky-100 text-sky-800 dark:bg-sky-900/30 dark:text-sky-300 border-sky-300 dark:border-sky-800",
  "Content Select": "bg-rose-100 text-rose-800 dark:bg-rose-900/30 dark:text-rose-300 border-rose-300 dark:border-rose-800",
  "File": "bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-300 border-pink-300 dark:border-pink-800",
  "Relator": "bg-violet-100 text-violet-800 dark:bg-violet-900/30 dark:text-violet-300 border-violet-300 dark:border-violet-800",
  "Tab": "bg-slate-100 text-slate-800 dark:bg-slate-800/30 dark:text-slate-300 border-slate-300 dark:border-slate-700",
}

/** Left accent border color per widget type */
export const WIDGET_ACCENT_COLORS: Record<string, string> = {
  "Text": "border-l-blue-500",
  "Text Area": "border-l-indigo-500",
  "HTML Editor": "border-l-purple-500",
  "Check Box": "border-l-green-500",
  "Combo Box": "border-l-teal-500",
  "Date": "border-l-amber-500",
  "Hidden": "border-l-gray-400",
  "Multi Select": "border-l-cyan-500",
  "Ace Editor - Javascript": "border-l-yellow-500",
  "Ace Editor - HTML": "border-l-orange-500",
  "Ace Editor - CSS": "border-l-sky-500",
  "Content Select": "border-l-rose-500",
  "File": "border-l-pink-500",
  "Relator": "border-l-violet-500",
  "Tab": "border-l-slate-500",
}

export function getWidgetBadgeColor(widgetName: string): string {
  return WIDGET_BADGE_COLORS[widgetName] ?? "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300 border-gray-300 dark:border-gray-700"
}
