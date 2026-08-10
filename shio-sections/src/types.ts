/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The render-side contract of the section vocabulary (SH95).
 */

/** A section as the delivery API hands it over: its post-type name and its field values. */
export interface SectionPost {
  /** The post-type name — the key `SECTION_COMPONENTS` dispatches on. */
  type: string;
  /** The section's friendly URL, when the caller resolved it from one. */
  url?: string;
  /** Field values, keyed by attribute name. */
  data: Record<string, unknown>;
}

/**
 * Props every section component takes.
 *
 * `data` is loosely typed because it comes off the wire. Each component narrows what it
 * needs and renders nothing for what is absent — a section with an empty field is a page
 * the editor has not finished, not a crash.
 */
export interface SectionProps<T = Record<string, unknown>> {
  data: T;
  /**
   * Turns a `File`-field value into a URL.
   *
   * A File field stores the **post id** of the uploaded file, and the public bytes live
   * at `/file_source/<postId>/<fileName>` — a path that needs the file's name, which the
   * field value does not carry. Until the projection resolves assets by path (SH145) that
   * lookup belongs to the app, so this hook exists and the default is the id-keyed
   * streaming endpoint, which works but authenticates as the console.
   */
  resolveAsset?: (value: string) => string;
  className?: string;
  /**
   * The section post's id, which turns the rendered markup into something the editor can edit
   * (SH638).
   *
   * <h3>The host contract, which is the part that needed deciding</h3>
   *
   * The Java renderer emits `data-shio-post` / `data-shio-prop` itself, because it knows which post
   * it is rendering. A headless host does not get that for free: it fetched the section over the
   * CDA and React draws it, so **only the host knows the id**. Passing it here is that contract,
   * stated in one optional prop rather than in prose a host has to find.
   *
   * Optional on purpose. A public page has no reason to carry editing metadata, and a host that
   * omits it renders byte-identical markup to before — so the annotations cost nothing where they
   * are not wanted, and the editor is opt-in per render rather than per build.
   */
  postId?: string;
}

/**
 * The attributes that make one field editable in place (SH638).
 *
 * `data-shio-prop` names the field; `data-shio-prop-attr` names *which attribute* carries the value
 * when it is not the element's text — an `href` or a `src`. Both forms exist because `shio
 * annotations` counts both, and once a complete page read as 9 of 10 because only the first was
 * counted (SH503's near-miss regression).
 */
export function editable(field: string, attribute?: string): Record<string, string> {
  return attribute ? { "data-shio-prop": field, "data-shio-prop-attr": attribute }
    : { "data-shio-prop": field };
}

/** The section root's own annotation: which post this markup is, and of what type. */
export function editableSection(postId: string | undefined, type: string): Record<string, string> {
  return postId ? { "data-shio-post": postId, "data-shio-post-type": type } : {};
}

export interface HeroData {
  heading?: string;
  subheading?: string;
  body?: string;
  ctaLabel?: string;
  ctaHref?: string;
  image?: string;
  align?: string;
}

export interface FeatureGridData {
  heading?: string;
  intro?: string;
  columns?: string;
  features?: Array<{ title?: string; body?: string; icon?: string }>;
}

export interface CtaData {
  heading?: string;
  body?: string;
  ctaLabel?: string;
  ctaHref?: string;
  variant?: string;
}

export interface LogoWallData {
  heading?: string;
  logos?: Array<{ name?: string; image?: string; href?: string }>;
}

export interface TestimonialData {
  heading?: string;
  quotes?: Array<{ quote?: string; author?: string; role?: string; avatar?: string }>;
}

export interface FaqData {
  heading?: string;
  intro?: string;
  questions?: Array<{ question?: string; answer?: string }>;
}

export interface RichTextData {
  heading?: string;
  body?: string;
}

/** Default asset resolution: pass URLs and paths through, stream anything else by id. */
export const defaultResolveAsset = (value: string): string =>
  /^(https?:)?\/\//.test(value) || value.startsWith("/")
    ? value
    : `/api/v2/staticfile/${value}`;

/** A Relator field's rows — tolerant of an absent or non-array value. */
export function rows<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

/** Trimmed string, or undefined — so a blank field renders as nothing, not as an empty tag. */
export function str(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}
