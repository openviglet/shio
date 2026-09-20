import type { ShPostAttr } from "@/models/post/post"
import { TextWidget } from "./text.widget"
import { TextAreaWidget } from "./textarea.widget"
import { HtmlEditorWidget } from "./html-editor.widget"
import { CheckBoxWidget } from "./checkbox.widget"
import { ComboBoxWidget } from "./combobox.widget"
import { DateWidget } from "./date.widget"
import { HiddenWidget } from "./hidden.widget"
import { MultiSelectWidget } from "./multi-select.widget"
import { AceEditorLazyWidget } from "./ace-editor.lazy"
import { ContentSelectWidget } from "./content-select.widget"
import { FileWidget } from "./file.widget"
import { RelatorWidget } from "./relator.widget"
import { BlocksWidget } from "./blocks.widget"
import { TabWidget } from "./tab.widget"

/**
 * What a widget is given, and what SH1052 added to it.
 *
 * <p>The first three were the whole contract, and what that cost is the defect SH1052 closes: the
 * form drew a `<Label>` with no `htmlFor` and then a widget whose props carried no id, so clicking
 * a label focused nothing and a screen reader met <b>every field of every post type</b> as an
 * unlabelled edit box. The required marker was a bare asterisk — punctuation, announced as nothing
 * — and the description was an orphan paragraph no control pointed at.</p>
 *
 * <p>The four below are the fix, and they are on the <i>contract</i> rather than in each widget
 * because that is the only place the form can reach: a widget cannot invent an id the label already
 * used. `post-form-fields.tsx` mints them, deterministically, and hands them down.</p>
 */
export interface WidgetProps {
  attr: ShPostAttr
  onChange: (attr: ShPostAttr) => void
  postId?: string
  /**
   * The id the field's own `<label htmlFor>` points at.
   *
   * <p>Put it on the <b>control</b>, not on a wrapper: a label pointing at a div names nothing and
   * clicking it focuses nothing. A composite widget — one that renders several controls, or a
   * listbox trigger — takes {@link labelledBy} instead, because there is no single control for the
   * label to own.</p>
   */
  fieldId?: string
  /** The id of the paragraph(s) describing the field: its description, its hint, its refusal. */
  describedBy?: string
  /** Whether the model requires a value. `aria-required`, not a `*` a screen reader skips. */
  required?: boolean
  /** Whether the server refused this field. `aria-invalid`, so the control says so itself. */
  invalid?: boolean
  /**
   * For a widget with no single control to label: the id of the visible label, which the widget
   * puts on its own group or trigger with `aria-labelledby`.
   */
  labelledBy?: string
}

const WIDGET_MAP: Record<string, React.ComponentType<WidgetProps>> = {
  "Text": TextWidget,
  "Text Area": TextAreaWidget,
  "HTML Editor": HtmlEditorWidget,
  "Check Box": CheckBoxWidget,
  "Combo Box": ComboBoxWidget,
  "Date": DateWidget,
  "Hidden": HiddenWidget,
  "Multi Select": MultiSelectWidget,
  // SH1044 — lazy, and named here rather than imported: this map is drawn by every screen that
  // shows a post's fields, so naming the widget itself made every console route import
  // CodeMirror - and, through the manual-chunk rule, 4.8 MB of GraphiQL with it.
  "Ace Editor - Javascript": AceEditorLazyWidget,
  "Ace Editor - HTML": AceEditorLazyWidget,
  // SH252 — a stylesheet gets a stylesheet editor.
  "Ace Editor - CSS": AceEditorLazyWidget,
  "Content Select": ContentSelectWidget,
  "File": FileWidget,
  "Relator": RelatorWidget,
  // SH1017 — the body an agent can address a paragraph of, edited as blocks.
  "Blocks": BlocksWidget,
  "Tab": TabWidget,
}

export function getWidget(widgetName: string): React.ComponentType<WidgetProps> {
  return WIDGET_MAP[widgetName] || TextWidget
}

export {
  TextWidget,
  TextAreaWidget,
  HtmlEditorWidget,
  CheckBoxWidget,
  ComboBoxWidget,
  DateWidget,
  HiddenWidget,
  MultiSelectWidget,
  AceEditorLazyWidget,
  ContentSelectWidget,
  FileWidget,
  RelatorWidget,
  BlocksWidget,
  TabWidget,
}
