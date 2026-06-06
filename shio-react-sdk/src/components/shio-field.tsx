import type { ReactNode } from "react";
import { ShioRichText } from "./shio-rich-text";
import { ShioImage } from "./shio-image";

export type ShioFieldKind = "text" | "html" | "image" | "list" | "json";

export interface ShioFieldProps {
  /** A single value from a post's `attrs` map. */
  value: unknown;
  /**
   * Force a renderer. When omitted, the kind is inferred from the value shape.
   *
   * NOTE: the CDA delivers `attrs` as a plain JSON map without per-field widget
   * metadata, so inference is heuristic. For exact widget fidelity, pass `as`
   * explicitly (full widget-map resolution awaits post-type schema exposure in
   * the CDA — see roadmap SH11).
   */
  as?: ShioFieldKind;
  /** Prefix for relative image paths when `as="image"` is inferred/forced. */
  baseUrl?: string;
  className?: string;
}

const HTML_RE = /<[a-z][\s\S]*>/i;
const IMAGE_RE = /\.(png|jpe?g|gif|webp|svg|avif)$/i;

function infer(value: unknown): ShioFieldKind {
  if (typeof value === "string") {
    if (HTML_RE.test(value)) return "html";
    if (IMAGE_RE.test(value.trim())) return "image";
    return "text";
  }
  if (Array.isArray(value)) return "list";
  if (value !== null && typeof value === "object") return "json";
  return "text";
}

/**
 * Renders a single post field, picking a renderer by the value's shape (or the
 * explicit `as` prop). A small, dependency-free default — swap in your own
 * renderers for richer field types.
 */
export function ShioField({ value, as, baseUrl, className }: ShioFieldProps): ReactNode {
  if (value === null || value === undefined || value === "") return null;
  const kind = as ?? infer(value);

  switch (kind) {
    case "html":
      return <ShioRichText className={className} html={String(value)} />;
    case "image":
      return <ShioImage className={className} src={String(value)} baseUrl={baseUrl} />;
    case "list":
      return (
        <ul className={className}>
          {(value as unknown[]).map((item, i) => (
            <li key={i}>{typeof item === "object" ? JSON.stringify(item) : String(item)}</li>
          ))}
        </ul>
      );
    case "json":
      return <pre className={className}>{JSON.stringify(value, null, 2)}</pre>;
    case "text":
    default:
      return <span className={className}>{String(value)}</span>;
  }
}
