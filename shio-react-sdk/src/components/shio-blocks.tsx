import type { ReactNode } from "react";
import type { ShioBlock } from "@viglet/shio-client";

/** A component rendering one block of its kind. */
export type ShioBlockComponent<T extends ShioBlock = ShioBlock> = (props: { block: T }) => ReactNode;

/** Block type to component. Every key is optional: a kind with no entry falls back. */
export type ShioBlockComponents = {
  [K in ShioBlock["type"]]?: ShioBlockComponent<Extract<ShioBlock, { type: K }>>;
};

export interface ShioBlocksProps {
  /** The field's blocks — `shioBlocks(post, "BODY")` from the core client. */
  blocks: ShioBlock[] | null | undefined;
  /** A component per block type. Anything not named here uses the fallback. */
  components?: ShioBlockComponents;
  /**
   * The rendered HTML the CDA delivers beside the blocks (`post.rendered?.BODY`), used for a
   * block type this build has no component for.
   */
  fallbackHtml?: Record<string, string> | string | null;
}

/**
 * SH1016 — a blocks body rendered with your own components.
 *
 * <p>The CDA delivers a body twice: as an array of typed blocks and as HTML. This maps the first
 * onto components you own, which is the whole reason the body stopped being a string.</p>
 *
 * <h2>The fallback is per block, not per document</h2>
 *
 * <p>A page with one block type you have no component for should render the seven it knows. Falling
 * back for the whole document would make adding a block type to the model blank a page that was
 * working — which is the failure a fallback exists to prevent, arriving by the fallback.</p>
 *
 * <p>An unhandled block renders a readable default rather than nothing: a heading is a heading, a
 * list is a list. Rendering nothing is how content goes missing silently, and "I did not write a
 * component for `embed`" should not mean "the embed is gone".</p>
 */
export function ShioBlocks({ blocks, components, fallbackHtml }: ShioBlocksProps) {
  if (!blocks?.length) return null;
  return (
    <>
      {blocks.map((block) => {
        const Component = components?.[block.type] as ShioBlockComponent | undefined;
        return (
          <div key={block.key}>
            {Component ? <Component block={block} /> : defaultFor(block, fallbackHtml)}
          </div>
        );
      })}
    </>
  );
}

/**
 * What a block renders as when nothing claims it.
 *
 * Built from the block's own fields rather than from the delivered HTML, because that HTML is the
 * *document's* — one string for the whole body — and slicing a block out of it is guesswork. The
 * HTML is offered for the case a caller genuinely wants it, and then it is all of it.
 */
function defaultFor(block: ShioBlock, fallbackHtml: ShioBlocksProps["fallbackHtml"]): ReactNode {
  switch (block.type) {
    case "heading": {
      const Tag = `h${Math.min(6, Math.max(1, block.level ?? 2))}` as "h1";
      return <Tag>{block.text}</Tag>;
    }
    case "paragraph":
      return <p>{block.text}</p>;
    case "quote":
      return <blockquote>{block.text}</blockquote>;
    case "code":
      return <pre><code>{block.text}</code></pre>;
    case "list":
      return block.ordered
        ? <ol>{(block.items ?? []).map(listItem)}</ol>
        : <ul>{(block.items ?? []).map(listItem)}</ul>;
    case "image":
      return <img src={block.src} alt={block.alt ?? ""} />;
    // An embed and a reference are addresses, not markup: what to do with one is the site's
    // decision, and guessing an iframe here would make that decision for every consumer. They
    // render nothing visible and stay in the tree, so a component can claim them later.
    case "embed":
    case "reference":
      return null;
    default:
      return typeof fallbackHtml === "string" && fallbackHtml
        ? <div dangerouslySetInnerHTML={{ __html: fallbackHtml }} />
        : null;
  }
}

function listItem(item: string, index: number) {
  return <li key={`${index}-${item}`}>{item}</li>;
}
