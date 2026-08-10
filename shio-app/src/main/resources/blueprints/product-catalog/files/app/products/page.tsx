import Link from "next/link";
import type { Metadata } from "next";
import { getShioClient, SITE_ID } from "@/lib/shio";

/**
 * The catalogue grid the `product-catalog` package was missing (SH216).
 *
 * A Product's own page renders from the starter's catch-all. A *grid* cannot: it is about the
 * set rather than about any member, and it needs the one thing a listing does not carry — the
 * price.
 *
 * <h2>The one place this pays for a second call, and the cap that bounds it</h2>
 *
 * `query` returns summaries with no `attrs`, so `price`, `currency` and `image` are only on the
 * full post. A catalogue without prices is not a catalogue, so this fetches the page's products
 * in parallel — the same trade `resolveSections` makes — and **caps the page at `PAGE_SIZE`**
 * so the cost is bounded by the page rather than by the catalogue. Twenty-four parallel reads
 * cost one round trip's latency; two hundred would be a different thing entirely, which is why
 * the cap is a constant here and not a `size` somebody passes.
 */
export const revalidate = 60;

export const metadata: Metadata = {
  title: "Products",
};

/** Products per page. The bound on this page's fan-out, so it is stated once and named. */
const PAGE_SIZE = 24;

/** Money is text in this model on purpose — it is not a float — so it is printed, not parsed. */
function price(attrs: Record<string, unknown>): string {
  const amount = String(attrs?.price ?? "").trim();
  if (!amount) return "";
  const currency = String(attrs?.currency ?? "").trim();
  return currency ? `${amount} ${currency}` : amount;
}

export default async function ProductIndex() {
  if (!SITE_ID) {
    return (
      <main>
        <p>Set SHIO_SITE_ID to list your products.</p>
      </main>
    );
  }

  const client = await getShioClient();
  const listing = await client.query({
    siteId: SITE_ID,
    postType: "Product",
    size: PAGE_SIZE,
  });

  const products = (
    await Promise.all(listing.posts.map((row) => client.getPost(row.id).catch(() => null)))
  ).filter((product) => product !== null);

  return (
    <main>
      <h1>Products</h1>
      {products.length === 0 && <p>No products yet.</p>}
      <ul>
        {products.map((product) => (
          <li key={product.id}>
            <h2>
              <Link href={product.furl ?? "#"}>{product.title}</Link>
            </h2>
            {product.summary && <p>{product.summary}</p>}
            <p>{price(product.attrs)}</p>
            {/* SKU is the curator's own identifier, so it is shown as one rather than styled. */}
            {product.attrs?.sku ? <small>SKU {String(product.attrs.sku)}</small> : null}
          </li>
        ))}
      </ul>
      {listing.totalPosts > PAGE_SIZE && (
        // Said rather than silently truncated: a grid that shows the first 24 of 200 and does not
        // say so reads as a catalogue with 24 products in it.
        <p>
          Showing {products.length} of {listing.totalPosts}.
        </p>
      )}
    </main>
  );
}
