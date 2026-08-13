/**
 * SH716 — a crumb's React key.
 *
 * <h2>The defect this replaces</h2>
 *
 * <p>`page-header.tsx` rendered the list as `<React.Fragment key={item.label}>`, and
 * `post.edit.page.tsx` builds that list by pushing the **site name** and then the **folder name**.
 * For a post in a site's <b>root</b> folder those are the same string — the root folder carries the
 * site's name — so the key repeated on every such page. Not an edge case: it is the default shape
 * of a new site, and it failed `console:check` on the one instance that had a post in one.</p>
 *
 * <h2>Why the label was the wrong thing to key on at all</h2>
 *
 * <p>A React key is an identity claim, and a crumb's identity is <b>where it goes</b>, not the text
 * a curator typed. Two folders may legitimately share a name; two folders cannot share an address.
 * So the `href` is the key wherever there is one.</p>
 *
 * <p>The terminal crumb — the page you are on — has no `href`, and there is exactly one of it, so
 * its position is a complete identity. An index is usually a smell because it makes a reordered
 * list swap state between rows; a breadcrumb does not reorder, it is replaced whole on every
 * navigation, and the only index in play is the last one.</p>
 *
 * @param item  the crumb: its label, and its target where it has one
 * @param index its position in the list
 * @returns a key unique within the list, for any labels a curator can produce
 */
export function crumbKey(item: { label: string; href?: string }, index: number): string {
  return item.href ?? `#${index}`;
}
