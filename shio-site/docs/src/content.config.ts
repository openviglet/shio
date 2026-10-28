import { defineCollection } from "astro:content";
import { glob } from "astro/loaders";
import { docsSchema } from "@astrojs/starlight/schema";

// Starlight's docsLoader, minus COVERAGE.md: the coverage matrix sits beside the pages so its
// `./page.md#anchor` links and the docs gates read one directory, but it is a maintenance file
// and never a page (SH1262).
export const collections = {
  docs: defineCollection({
    loader: glob({
      base: "./src/content/docs",
      pattern: ["**/[^_]*.{markdown,mdown,mkdn,mkd,mdwn,md,mdx}", "!COVERAGE.md"],
    }),
    schema: docsSchema(),
  }),
};
