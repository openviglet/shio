/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

// Builders — the DSL surface (SH63)
export {
  postType,
  text,
  textArea,
  htmlEditor,
  checkBox,
  comboBox,
  date,
  hidden,
  multiSelect,
  aceJs,
  aceHtml,
  contentSelect,
  file,
  relator,
  tab,
  WIDGET,
} from "./builders";

// Canonical serialization — shared with the CLI (SH16/SH64)
export { normalizeDef, serializeDef } from "./serialize";

// widgetSettings codec — encode (build) + decode (scaffold-from-pull)
export {
  encodeChoices,
  decodeChoices,
  encodeFile,
  encodeContentSelect,
  encodeMultiSelect,
  encodeRelator,
} from "./widget-settings";

// Types
export type {
  ShPostTypeDef,
  ShPostTypeAttrDef,
  FieldOptions,
  ChoiceSettings,
  FolderSettings,
  MultiSelectSettings,
  RelatorSettings,
  PostTypeOptions,
} from "./types";
