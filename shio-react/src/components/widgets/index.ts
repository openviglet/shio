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

export interface WidgetProps {
  attr: ShPostAttr
  onChange: (attr: ShPostAttr) => void
  postId?: string
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
