/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The section vocabulary (SH95) — the render half.
 *
 * Unstyled and semantic on purpose. These components ship the *structure* of each
 * section (which heading level, which landmark, what alt text goes where) and leave the
 * design to the site's own CSS via stable `shio-*` class names. Two reasons: a package
 * that shipped opinionated styles would be replaced on day one of any real project, and
 * SH101's render digest reads structure — headings, landmarks, links, images with alt —
 * so a shared vocabulary is only verifiable if the structure is the part that is shared.
 *
 * Every component renders nothing for a field the editor left empty. A section is a work
 * in progress far more often than it is finished, and a missing subheading must not become
 * an empty tag the digest reports as a defect.
 */
import type { JSX } from "react";

import {
  defaultResolveAsset,
  rows,
  str,
  type CtaData,
  type FaqData,
  type FeatureGridData,
  type HeroData,
  type LogoWallData,
  type RichTextData,
  type SectionProps,
  type TestimonialData,
} from "./types";
import { SECTION_TYPES } from "./post-types";

const cx = (...parts: Array<string | undefined>) => parts.filter(Boolean).join(" ");

/** Rich text arrives as HTML from the editor widget; rendered as such, deliberately. */
const html = (value: string) => ({ __html: value });

export function Hero({ data, resolveAsset = defaultResolveAsset, className }: SectionProps<HeroData>): JSX.Element {
  const image = str(data.image);
  const ctaHref = str(data.ctaHref);
  const ctaLabel = str(data.ctaLabel);
  return (
    <section className={cx("shio-section", "shio-hero", str(data.align) === "center" ? "shio-hero--center" : undefined, className)}>
      {str(data.heading) && <h1 className="shio-hero__heading">{data.heading}</h1>}
      {str(data.subheading) && <p className="shio-hero__subheading">{data.subheading}</p>}
      {str(data.body) && <div className="shio-hero__body" dangerouslySetInnerHTML={html(data.body as string)} />}
      {ctaHref && ctaLabel && (
        <a className="shio-hero__cta" href={ctaHref}>
          {ctaLabel}
        </a>
      )}
      {image && <img className="shio-hero__image" src={resolveAsset(image)} alt={str(data.heading) ?? ""} />}
    </section>
  );
}

export function FeatureGrid({ data, className }: SectionProps<FeatureGridData>): JSX.Element {
  const features = rows<{ title?: string; body?: string; icon?: string }>(data.features);
  return (
    <section
      className={cx("shio-section", "shio-features", className)}
      data-columns={str(data.columns) ?? "3"}
    >
      {str(data.heading) && <h2 className="shio-features__heading">{data.heading}</h2>}
      {str(data.intro) && <p className="shio-features__intro">{data.intro}</p>}
      <ul className="shio-features__list">
        {features.map((feature, index) => (
          <li className="shio-features__item" key={str(feature.title) ?? index}>
            {str(feature.icon) && <span className="shio-features__icon" data-icon={feature.icon} aria-hidden="true" />}
            {str(feature.title) && <h3 className="shio-features__title">{feature.title}</h3>}
            {str(feature.body) && <p className="shio-features__body">{feature.body}</p>}
          </li>
        ))}
      </ul>
    </section>
  );
}

export function Cta({ data, className }: SectionProps<CtaData>): JSX.Element {
  const ctaHref = str(data.ctaHref);
  const ctaLabel = str(data.ctaLabel);
  return (
    <section className={cx("shio-section", "shio-cta", className)} data-variant={str(data.variant) ?? "primary"}>
      {str(data.heading) && <h2 className="shio-cta__heading">{data.heading}</h2>}
      {str(data.body) && <p className="shio-cta__body">{data.body}</p>}
      {ctaHref && ctaLabel && (
        <a className="shio-cta__button" href={ctaHref}>
          {ctaLabel}
        </a>
      )}
    </section>
  );
}

export function LogoWall({ data, resolveAsset = defaultResolveAsset, className }: SectionProps<LogoWallData>): JSX.Element {
  const logos = rows<{ name?: string; image?: string; href?: string }>(data.logos);
  return (
    <section className={cx("shio-section", "shio-logos", className)}>
      {str(data.heading) && <h2 className="shio-logos__heading">{data.heading}</h2>}
      <ul className="shio-logos__list">
        {logos.map((logo, index) => {
          const image = str(logo.image);
          // The company name is the alt text, always: a logo wall of images with no alt
          // is the single most common accessibility defect on a marketing page, and it is
          // one SH101's digest reports.
          const mark = image ? (
            <img className="shio-logos__image" src={resolveAsset(image)} alt={str(logo.name) ?? ""} />
          ) : (
            <span className="shio-logos__name">{str(logo.name)}</span>
          );
          const href = str(logo.href);
          return (
            <li className="shio-logos__item" key={str(logo.name) ?? index}>
              {href ? <a href={href}>{mark}</a> : mark}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export function Testimonial({ data, resolveAsset = defaultResolveAsset, className }: SectionProps<TestimonialData>): JSX.Element {
  const quotes = rows<{ quote?: string; author?: string; role?: string; avatar?: string }>(data.quotes);
  return (
    <section className={cx("shio-section", "shio-testimonials", className)}>
      {str(data.heading) && <h2 className="shio-testimonials__heading">{data.heading}</h2>}
      {quotes.map((quote, index) => {
        const avatar = str(quote.avatar);
        return (
          <figure className="shio-testimonials__item" key={str(quote.author) ?? index}>
            {avatar && <img className="shio-testimonials__avatar" src={resolveAsset(avatar)} alt={str(quote.author) ?? ""} />}
            {str(quote.quote) && <blockquote className="shio-testimonials__quote">{quote.quote}</blockquote>}
            <figcaption className="shio-testimonials__author">
              {str(quote.author)}
              {str(quote.role) && <span className="shio-testimonials__role">{quote.role}</span>}
            </figcaption>
          </figure>
        );
      })}
    </section>
  );
}

export function Faq({ data, className }: SectionProps<FaqData>): JSX.Element {
  const questions = rows<{ question?: string; answer?: string }>(data.questions);
  return (
    <section className={cx("shio-section", "shio-faq", className)}>
      {str(data.heading) && <h2 className="shio-faq__heading">{data.heading}</h2>}
      {str(data.intro) && <p className="shio-faq__intro">{data.intro}</p>}
      <dl className="shio-faq__list">
        {questions.map((entry, index) => (
          <div className="shio-faq__item" key={str(entry.question) ?? index}>
            {str(entry.question) && <dt className="shio-faq__question">{entry.question}</dt>}
            {str(entry.answer) && (
              <dd className="shio-faq__answer" dangerouslySetInnerHTML={html(entry.answer as string)} />
            )}
          </div>
        ))}
      </dl>
    </section>
  );
}

export function RichText({ data, className }: SectionProps<RichTextData>): JSX.Element {
  return (
    <section className={cx("shio-section", "shio-richtext", className)}>
      {str(data.heading) && <h2 className="shio-richtext__heading">{data.heading}</h2>}
      {str(data.body) && <div className="shio-richtext__body" dangerouslySetInnerHTML={html(data.body as string)} />}
    </section>
  );
}

/**
 * Post-type name → component.
 *
 * This map is the whole reason the vocabulary is worth having: the front end never asks
 * "what kind of page is this", it asks each section what it is and renders it. Adding a
 * section type is one entry here and one post-type there — no page template changes.
 */
export const SECTION_COMPONENTS: Record<string, (props: SectionProps<never>) => JSX.Element> = {
  [SECTION_TYPES.hero]: Hero as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.featureGrid]: FeatureGrid as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.cta]: Cta as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.logoWall]: LogoWall as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.testimonial]: Testimonial as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.faq]: Faq as (props: SectionProps<never>) => JSX.Element,
  [SECTION_TYPES.richText]: RichText as (props: SectionProps<never>) => JSX.Element,
};
