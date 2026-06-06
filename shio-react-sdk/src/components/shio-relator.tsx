import { Fragment } from "react";
import type { ReactNode } from "react";

export interface ShioRelatorProps<T = Record<string, unknown>> {
  /** Relator field value — expected to be an array of nested item objects. */
  items: unknown;
  /** Render prop for each item. */
  children: (item: T, index: number) => ReactNode;
  /** Optional key extractor (defaults to the array index). */
  keyFor?: (item: T, index: number) => string | number;
}

/**
 * Renders a Relator field (a list of nested content items). Tolerant of a
 * non-array value (renders nothing), since the CDA `attrs` payload is untyped.
 */
export function ShioRelator<T = Record<string, unknown>>({
  items,
  children,
  keyFor,
}: ShioRelatorProps<T>) {
  const list: T[] = Array.isArray(items) ? (items as T[]) : [];
  return (
    <>
      {list.map((item, index) => (
        <Fragment key={keyFor ? keyFor(item, index) : index}>
          {children(item, index)}
        </Fragment>
      ))}
    </>
  );
}
