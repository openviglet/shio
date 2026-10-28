# SH1250: the Shio showcase site, designed before it is built

Adobe ships WKND so that anyone evaluating AEM can install it and see each capability on a real
page. This is Shio's equivalent: one marketplace package that installs a finished, bilingual,
good-looking site, plus a feature tour that says which capability each page shows and how it was
built. This spec settles the brand, the page map, the visual direction, the languages and the
feature-to-page matrix. It writes no content. The tasks that build on it:

- SH1252 lets a package carry its images;
- SH1254 builds the package (model, theme, layouts, menus, brand pages, en and pt);
- SH1255 turns the matrix into a coverage test whose debt is the tour's backlog;
- SH1256 builds the tour pages and drains that debt;
- SH1257 decides how a fresh instance reaches the package;
- SH1258 puts it on shio.viglet.org.

The matrix is data: [SH1250-showcase-matrix.json](SH1250-showcase-matrix.json).
`cli/test/showcase-matrix.test.mjs` holds it to the product as it is today: every manifest
`features` key and every MCP tool appears exactly once, every entry names a page from the page
map or gives a reason, and no key appears that the product does not declare.

## The brand

**Quilhara**: a fictional magazine and guided-trip outfitter for coastal and ridge trails, written
in English and Brazilian Portuguese. The name is invented, so it borrows no trademark; check it
against a trademark search before the package is published. The trails, places, guides and
stories are invented too, and `/about` says so in its first sentence.

Why this brand: it needs the content shapes a real site needs, and each one exercises something
Shio does. Stories are long-form editorial with captioned photography (asset metadata, image
transforms). Trails are structured records with relations to guides and stories (the relator,
listings, related content). Trips have dates and a price and an enquiry form (site forms that land
as drafts). Members get a page nobody else sees (content ACL). And two languages are native to the
subject rather than bolted on, which is what makes localization worth showing.

**Voice.** Plain and specific, a field guide rather than a brochure: distances, tides, what to
carry, who keeps the trail open. No superlatives. Every sample text follows the project's em-dash
rule (SH930) and the copy rules `shio-site` applies to its own pages.

## The page map

Paths are the package's `en-us` post urls, and every page exists in both languages: the `pt-br`
page is the post whose `translationOf` names the English one, under its own Portuguese slug
(SH1254 built them that way, and SH1255 aligned the matrix with it). The authoritative list is the
matrix's `pages` object; in summary:

| Section | Pages | What it is for |
|---|---|---|
| Home | `/` | The lead story, three featured trails, the next trips. Rendered on the server. |
| Trails | `/trails`, three details | A filtered card listing and structured detail pages with map, gallery, guide, related stories. |
| Stories | `/stories`, two details | The magazine: long-form, captioned photography. |
| Guides | `/guides`, one profile | People, linked to the trails and trips they lead. |
| Trips | `/trips`, one detail | Dates, price, an enquiry form. |
| Members | `/members` | Visible only to a signed-in member. |
| Utility | `/search`, `/about`, `/contact` | Search, the fictional-brand notice, a site form. |
| Tour | `/tour` and eight topic pages | How the site was built, one capability family per page. |

The tour pages each carry a "how this was built" panel naming the console screen, the MCP tool
and the CDA call behind the page (SH1256). Agent-side capabilities have nothing a visitor can see,
so their page is a tour page showing the call and its real answer from this site.

## Visual direction

- **Palette** as theme tokens: deep sea `#0E3B4F` for text and the header, sand `#F2E6D0` for
  surfaces, coral `#E4572E` as the one accent, foam `#F7FAFA` for the page background. Contrast is
  checked by the a11y gate SH1254 runs, not by eye.
- **Type**: a serif for display (Fraunces) and a sans for body and UI (Inter), both under the SIL
  Open Font Licence and self-hosted in the package, never loaded from a third-party CDN.
- **Images**: own photography or CC0 only, never a licence that needs attribution on every page
  or forbids redistribution. At most 25 images, WebP, each under 300 KB at its largest size, so
  the set stays under 8 MB against the installer's 64 MB bound. Every image has alt text in both
  languages and an entry in the package's `assets/CREDITS.json` naming its source and licence.
- **Layout**: one `PageLayout` per page kind (home, listing, detail, story, tour), regions from the
  section vocabulary, menus through `{{#menu}}` (SH873), no hand-sorted queries.

## Languages

`en-us` and `pt-br`, through the localization model Block BH shipped. Both are written, not
machine-translated: the Portuguese is the subject's native language and has to read as such.
A third locale is out of scope; the point is to show two working, not to count them.

## The matrix, and what it excludes

Every manifest `features` key maps to one page or one exclusion. Six are excluded, each with its
reason in the JSON: `multiTenant`, `oidc`, `rateLimit`, `signedImageUrls`, `usageMeter` and
`searchIndexing`. They are operator settings with nothing a visitor could see, or they need a
service (Viglet Turing) that a fresh install does not have. An exclusion is a decision, so
changing one is a diff to this file that somebody reviews.

The twelve MCP tools map to tour pages. The curator surfaces (Universal Editor, releases,
revisions, review queue, preview links, site forms) map to `/tour/curator` and `/contact`.

When a feature ships after this spec, the test fails until the matrix names it: that is
deliberate, and it is the precursor of SH1255's ratchet.
