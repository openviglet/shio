/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `<Sections>` — render a page's sections in order (SH95).
 */
import type { JSX } from "react";

import { SECTION_COMPONENTS } from "./components";
import { defaultResolveAsset, type SectionPost, type SectionProps } from "./types";

export interface SectionsProps {
  /** The page's sections, already resolved, in render order. */
  sections: SectionPost[];
  resolveAsset?: (value: string) => string;
  className?: string;
  /**
   * What to render for a section type this vocabulary does not know.
   *
   * Defaults to nothing. An unknown section is a site that has grown its own type, which
   * is legitimate — but rendering a placeholder by default would put the CMS's diagnostics
   * on somebody's home page.
   */
  fallback?: (section: SectionPost) => JSX.Element | null;
}

/**
 * Renders each section with the component registered for its post-type.
 *
 * Deliberately takes resolved sections rather than fetching them. A page's `sections`
 * field holds friendly URLs, and resolving a URL is the delivery client's job
 * (`@viglet/shio-client`) — a component that fetched would tie this vocabulary to one
 * data-loading strategy and make it unusable from a server component, a loader, or a
 * static build. The resolution is three lines in the app; the vocabulary is the part
 * worth sharing.
 */
export function Sections({
  sections,
  resolveAsset = defaultResolveAsset,
  className,
  fallback,
}: SectionsProps): JSX.Element {
  return (
    <>
      {sections.map((section, index) => {
        const Component = SECTION_COMPONENTS[section.type];
        if (!Component) {
          return fallback ? fallback(section) : null;
        }
        const props = {
          data: section.data,
          resolveAsset,
          className,
        } as unknown as SectionProps<never>;
        return <Component key={section.url ?? `${section.type}-${index}`} {...props} />;
      })}
    </>
  );
}
