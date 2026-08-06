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
