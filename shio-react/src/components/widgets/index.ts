import type { ShPostAttr } from "@/models/post/post"
import { TextWidget } from "./text.widget"
import { TextAreaWidget } from "./textarea.widget"
import { HtmlEditorWidget } from "./html-editor.widget"
import { CheckBoxWidget } from "./checkbox.widget"
import { ComboBoxWidget } from "./combobox.widget"
import { DateWidget } from "./date.widget"
import { HiddenWidget } from "./hidden.widget"
import { MultiSelectWidget } from "./multi-select.widget"
import { AceEditorWidget } from "./ace-editor.widget"
import { ContentSelectWidget } from "./content-select.widget"
import { FileWidget } from "./file.widget"
import { RelatorWidget } from "./relator.widget"
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
  "Ace Editor - Javascript": AceEditorWidget,
  "Ace Editor - HTML": AceEditorWidget,
  // SH252 — a stylesheet gets a stylesheet editor.
  "Ace Editor - CSS": AceEditorWidget,
  "Content Select": ContentSelectWidget,
  "File": FileWidget,
  "Relator": RelatorWidget,
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
  AceEditorWidget,
  ContentSelectWidget,
  FileWidget,
  RelatorWidget,
  TabWidget,
}
