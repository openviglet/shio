/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * The representative post-type used by both the byte-identical golden test and
 * the widget-by-widget codec tests. Exercises every settings-bearing widget plus
 * a relator and a tab. Kept in one place so the DSL value and the golden JSON
 * (`news.golden.json`) describe the same model.
 */
import {
  postType,
  text,
  textArea,
  htmlEditor,
  comboBox,
  file,
  contentSelect,
  multiSelect,
  date,
  aceJs,
  relator,
  tab,
  hidden,
} from "../dist/shio-model.js";

export const news = postType("News", {
  title: "News",
  namePlural: "News",
  description: "Press releases",
  fields: [
    text("title", { label: "Title", isTitle: true, required: true }),
    textArea("summary", { label: "Summary", isSummary: true }),
    htmlEditor("body", { label: "Body" }),
    comboBox("category", {
      label: "Category",
      choices: { tech: "Technology", biz: "Business" },
      allowNull: true,
    }),
    file("attachment", { label: "Attachment", folderId: "f-files" }),
    contentSelect("related", { label: "Related", folderId: "f-news" }),
    multiSelect("tags", { label: "Tags", postType: "Tag", folderId: "f-tags" }),
    date("publishedAt", { label: "Published at" }),
    aceJs("script", { label: "Script" }),
    relator("authors", {
      label: "Authors",
      title: "Authors",
      fields: [text("name", { isTitle: true }), text("email")],
    }),
    tab("meta", { label: "Metadata" }),
    hidden("source"),
  ],
});
