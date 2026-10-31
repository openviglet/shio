// SH1262 — Shio's end-user documentation, at https://openviglet.github.io/shio/docs/.
//
// It used to be `docs-shio/` in the vigletdocs Docusaurus repository, so every Shio ship that
// needed a page cost a commit in a second repository. It lives beside the code it describes now,
// as its own Astro + Starlight project inside the site (the shape roadkeep's site/docs runs).
//
// Two joins to the site next door, and both are load-bearing:
//
// - BASE. Astro rewrites the links it generates and NOT the ones typed in a page, so every link
//   the importer wrote is absolute and already carries `/shio/docs/`. Change the base here and
//   those change with it, or every hand-typed link 404s in production alone. The site itself is
//   under `/shio/` (vite.config.ts), and this area sits one level inside it.
// - OUT_DIR. The site builds to `shio-site/dist/` and this area into a subdirectory of it, which
//   is the one directory the Pages deploy uploads. Astro empties outDir before writing and
//   `vite build` empties `dist/`, which is why the site's build runs this one LAST.
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";

const BASE = "/shio/docs";
const OUT_DIR = "../dist/docs";

export default defineConfig({
  site: "https://openviglet.github.io",
  base: BASE,
  outDir: OUT_DIR,
  trailingSlash: "always",
  integrations: [
    starlight({
      title: "Viglet Shio",
      description: "The documentation of Viglet Shio, the open-source, agent-ready CMS.",
      logo: { src: "./src/assets/shio-mark.svg", alt: "Viglet Shio" },
      favicon: "/favicon.svg",
      social: [{ icon: "github", label: "GitHub", href: "https://github.com/openviglet/shio" }],
      // The site's look, as Turing's docs carry turing.viglet.org's (T1162): the site's
      // fonts, its palette on Starlight's tokens, and its brand, nav and footer.
      customCss: ["./src/styles/docs.css"],
      head: [
        { tag: "link", attrs: { rel: "preconnect", href: "https://fonts.googleapis.com" } },
        { tag: "link", attrs: { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: true } },
        {
          tag: "link",
          attrs: {
            rel: "stylesheet",
            href: "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:ital,wght@0,400;0,500;0,600;0,700;0,800;1,400&family=JetBrains+Mono:wght@400;500;700&display=swap",
          },
        },
      ],
      components: {
        Head: "./src/components/Head.astro",
        SiteTitle: "./src/components/SiteTitle.astro",
        SocialIcons: "./src/components/SocialIcons.astro",
        Footer: "./src/components/Footer.astro",
      },
      editLink: { baseUrl: "https://github.com/openviglet/shio/edit/2026.3/shio-site/docs/" },
      lastUpdated: false,
      // The four hubs Docusaurus had, in its order: a reader picks by job, not by subsystem.
      sidebar: [
        { label: "Getting started", items: ["getting-started/intro", "getting-started/core-concepts", "architecture-overview"] },
        {
          label: "The agent builds it",
          items: ["agent-builds-it", "mcp", "agent-surface", "content-as-files", "cli", "blueprints",
            "website-development", "design-tokens", "replication", "token-economy"],
        },
        {
          label: "The human curates it",
          items: ["curator-approves-it", "content-console", "content-lifecycle", "agent-safety",
            "universal-editor", "content-modeling", "content-i18n", "administration-guide", "import-export"],
        },
        {
          label: "The CDA delivers it",
          items: ["cda-delivers-it", "headless/content-delivery-api", "headless/javascript-client",
            "headless/react-sdk", "headless/nextjs-starter", "graphql", "webhooks"],
        },
        {
          label: "Run it",
          items: ["run-it", "installation-guide", "configuration-reference", "multi-tenancy",
            "search-caching", "security"],
        },
        { label: "Developers", items: ["developer-guide", "rest-api"] },
      ],
    }),
  ],
});
