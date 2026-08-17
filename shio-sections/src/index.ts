/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */

// The content model half (SH95) — post-type definitions, authored with @viglet/shio-model.
export {
  MENU_ITEM_TYPE,
  MENU_TYPE,
  PAGE_TYPE,
  SECTION_TYPES,
  menu,
  menuItem,
  page,
  sectionCta,
  sectionFaq,
  sectionFeatureGrid,
  sectionHero,
  sectionLogoWall,
  sectionMarkup,
  sectionRichText,
  sectionTestimonial,
  sectionPostTypes,
} from "./post-types";

// The render half — one component per section, plus the dispatcher.
export {
  Cta,
  Faq,
  FeatureGrid,
  Hero,
  LogoWall,
  Markup,
  RichText,
  Testimonial,
  SECTION_COMPONENTS,
} from "./components";
export { Sections } from "./sections";
export type { SectionsProps } from "./sections";

// Shapes and the small helpers the components share.
export { defaultResolveAsset, rows, str } from "./types";
export type {
  CtaData,
  FaqData,
  FeatureGridData,
  HeroData,
  LogoWallData,
  MarkupData,
  RichTextData,
  SectionPost,
  SectionProps,
  TestimonialData,
} from "./types";
