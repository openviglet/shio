import type { HTMLAttributes } from "react";

export interface ShioRichTextProps extends HTMLAttributes<HTMLDivElement> {
  /** HTML string (e.g. an HTML Editor field value). */
  html: string | null | undefined;
}

/**
 * Renders an HTML field value.
 *
 * ⚠️ The HTML is injected verbatim via `dangerouslySetInnerHTML`. Shio content
 * is authored by trusted editors, but if your content can contain untrusted
 * input, sanitize upstream (e.g. with DOMPurify) before passing it here.
 */
export function ShioRichText({ html, ...rest }: ShioRichTextProps) {
  if (!html) return null;
  return <div {...rest} dangerouslySetInnerHTML={{ __html: html }} />;
}
