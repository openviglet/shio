/*
 * ─────────────────────────────────────────────────────────────────────────────
 * SH539: the depth page behind each pillar on the landing page.
 *
 * What these are for is the *mechanism*, at the level a reader deciding needs
 * it and no deeper: the actual call, the actual file on disk, the actual error
 * body. `docs/agents/**` in the repository holds the authoritative version and
 * is written for somebody about to change the code, which makes it exactly the
 * wrong link to hand a reader who has not chosen the product yet.
 *
 * One record drives one prerendered route each, so adding a pillar is an entry
 * here plus a row in `entry-server.tsx`, and `routesAreDeclared()` fails the
 * build if you write only one of them.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import { ceiling } from "./token-budgets";

export interface FeatureBlock {
  title: string;
  body: string;
  code?: { caption: string; text: string };
  /** The design law this block is an instance of. */
  law?: string;
}

export interface FeatureDetail {
  slug: string;
  metaTitle: string;
  metaDescription: string;
  eyebrow: string;
  /** Split so the tail can carry the gradient without markup in the data. */
  title: string;
  titleAccent: string;
  lead: string;
  blocks: FeatureBlock[];
  related: { label: string; href: string }[];
}

const AGENT_GATEWAY: FeatureDetail = {
  slug: "agent-gateway",
  metaTitle: "The agent gateway: manifest, context pack, batch | Viglet Shio",
  metaDescription:
    "The namespace shaped for a coding agent rather than a browser: a manifest that reports unshipped capabilities as false and says where a capability lives when it lives elsewhere, a context pack that replaces the discovery session, a write vocabulary you can read before writing, and 4xx bodies that carry the fix.",
  eyebrow: "The agent gateway",
  title: "A namespace shaped for a caller that",
  titleAccent: "cannot click anything",
  lead: "Everything here is a shape over the delivery API and the console services, never a parallel semantic. What makes it a gateway rather than a second API is that it answers the questions a browser never has to ask: what can you do, what is here, and what did I just get wrong.",
  blocks: [
    {
      title: "The manifest answers first, including about what has not shipped",
      body: "A capability that has not shipped is reported false rather than omitted, because an absent flag sends a caller probing, which is the exact cost the manifest exists to remove. And where a capability lives somewhere else, an elsewhere map names the command instead: reporting false for a screenshot reads as not possible, when the truth is possible, from the CLI.",
      code: {
        caption: "the cheapest read on the surface",
        text: `GET /api/v2/agent/manifest?include=features,limits

# At most ${ceiling("response.manifest.check")} tokens, a ceiling the build enforces. The
# full document's is ${ceiling("response.manifest")}, and the difference is
# the reason this projection exists.`,
      },
      law: "P2",
    },
    {
      title: "One call for the model, the sitemap and the conventions",
      body: "The context pack is the discovery session collapsed into a response, and it carries a digest. Session N+1 sends the digest back and is told nothing changed, which turns re-reading the context from a decision an agent has to make into a free default. Ask for the write vocabulary in the same call and every op arrives with the keys it takes.",
      code: {
        caption: "the whole of discovery",
        text: `GET /api/v2/agent/context?include=all,manifest,ops`,
      },
      law: "P2",
    },
    {
      title: "A batch can be planned before it is applied",
      body: "The plan is the same document as the write, annotated with what each op would change, not a summary in a different shape, because two shapes drift. A destructive op is refused once with a confirm digest over exactly those deletions, so the second call cannot quietly cover more than the first one showed you.",
      code: {
        caption: "look, then write",
        text: `POST /api/v2/agent/batch
{
  "dryRun": true,
  "ops": [
    { "op": "post.upsert",
      "address": "post:acme/launch",
      "postType": "Page",
      "fields": { "title": "Launching Shio" } }
  ]
}`,
      },
      law: "P7",
    },
    {
      title: "A wrong call costs one retry, not one session",
      body: "Every 4xx carries the fix in the body: what was wrong, the near miss if the mistake was a typo, the closed set of what is allowed, an instruction, and a call that would have worked. An agent that gets Bad Request and nothing else does the expensive thing: it re-reads documentation, or it guesses.",
      law: "P6",
    },
  ],
  related: [
    { label: "Drive it from Claude Code", href: "/claude-code/" },
    { label: "Content as files", href: "/features/content-as-files/" },
  ],
};

const CONTENT_AS_FILES: FeatureDetail = {
  slug: "content-as-files",
  metaTitle: "Content as files: the projection and the three-way merge | Viglet Shio",
  metaDescription:
    "The whole site projected to one Markdown file per post, with a three-way merge back. The path is the URL, only the draft projects, losslessness beats prettiness, and the field set is a table with a test, so a key the format drops is declared with a reason rather than discovered later.",
  eyebrow: "Content as files",
  title: "Authoring with the tools the agent",
  titleAccent: "already has",
  lead: "Edit, Write and Grep cost an agent almost nothing, and a REST call per field costs it a turn. So the site projects to disk: one Markdown file per post, pulled, edited with ordinary tools and pushed back through a merge that knows the instance moved while you were working.",
  blocks: [
    {
      title: "One file per post, and the path is the URL",
      body: "YAML frontmatter for the metadata and the fields, and the post's first rich-text field as the document body, with the file recording which field that was, so reading it back never needs the model. A URL is unique per site so the tree cannot collide, and the folder travels in frontmatter because the two namespaces are deliberately separate. A file whose path and url disagree is an error, not a guess: a half-done rename is a move on one side and a create on the other.",
      code: {
        caption: "shio/content/acme/launch.md",
        text: `---
postType: Page
title: Launching Shio 2026.3
status: DRAFT
folder: folder:acme/
bodyField: content
---

An open-source, agent-ready CMS with four ways in.`,
      },
      law: "P4",
    },
    {
      title: "The field set is a table with a test",
      body: "Twelve keys travel, and everything a post carries that the projection drops is declared with a reason in a test. That table exists because a round-trip guard can test the values of fields the format has and cannot see a field it lacks, so the thing that would otherwise be discovered by somebody losing content is an assertion instead.",
      law: "P5",
    },
    {
      title: "The merge happens in the projection's own domain",
      body: "The base, the server side and the file side are all compared as the file tree holds them. Skipping that step was measured and it produced a permanent phantom change: a CRLF body read as a local edit on every pull, forever, and a drift check that failed on content nobody had touched. Fingerprinting the projected document alone is only half the fix: it converts the phantom into the opposite phantom.",
      law: "P5",
    },
    {
      title: "Only the draft projects, and publish is an instruction",
      body: "A publish key in a file travels forwards as an intent; the merge deliberately never writes state backwards into it. And losslessness beats prettiness: a value that would not survive the round trip stays as JSON in fields rather than being prettified into the body it cannot come back from.",
      law: "P5",
    },
  ],
  related: [
    { label: "The agent gateway", href: "/features/agent-gateway/" },
    { label: "Blueprints", href: "/features/blueprints/" },
  ],
};

const BLUEPRINTS: FeatureDetail = {
  slug: "blueprints",
  metaTitle: "Blueprints: appliable units instead of prose recipes | Viglet Shio",
  metaDescription:
    "A section of a site the instance can apply: a content model, a folder tree, sample pages, the parameters that make it somebody's, and the promise it keeps about what exists afterwards. A package may not name its own site and may not prune, substitution is one form and nothing else, and everything is validated at load.",
  eyebrow: "Blueprints",
  title: "A recipe is prose.",
  titleAccent: "A blueprint applies.",
  lead: "Everything a tutorial tells an agent to do is something it could instead be handed as a unit. A blueprint is a content model, a folder tree, sample pages, the parameters that make it yours, and a promise about what exists when it finishes.",
  blocks: [
    {
      title: "Nothing in the format is a new serialization",
      body: "The post types are the same bytes the CLI already writes, the content files are the same desired-state documents apply already takes, and the front-end sources are ordinary files. That is what keeps a blueprint from becoming a third contract with its own semantics to learn and its own bugs to find.",
      code: {
        caption: "apply a package",
        text: `shio blueprints                       # the catalogue and its parameters
shio apply --blueprint corporate \\
  --param brand=Acme --site acme      # or --dry-run first`,
      },
      law: "P10",
    },
    {
      title: "A package may not name its own site, and may not prune",
      body: "The site is the caller's, so a site key inside a package is refused at load rather than ignored, because an ignored key looks like it worked. And prune's blast radius depends on what an author forgot to mention, which is an unacceptable thing to inherit from a package the user did not write.",
      law: "P7",
    },
    {
      title: "Substitution is one form and nothing else",
      body: "No conditionals and no expressions, so the same parameters always produce the same site. An unknown parameter is a teaching 400 rather than a silent no-op. And a malformed package answers 409, not 400: nothing the caller sent caused it, and an error that blames the caller for the author's mistake sends them looking in the wrong place.",
      law: "P5",
    },
    {
      title: "The model is provisioned, never upserted",
      body: "An existing post type of the same name is left exactly as it is, including a field a curator added, and the response names the fields it lacks instead of overwriting them. A plan writes nothing and needs no write transaction, so looking before you write works on a read-only credential.",
      law: "P8",
    },
  ],
  related: [
    { label: "Content as files", href: "/features/content-as-files/" },
    { label: "Rendering and themes", href: "/features/rendering/" },
  ],
};

const RENDERING: FeatureDetail = {
  slug: "rendering",
  metaTitle: "Rendering, themes and design tokens | Viglet Shio",
  metaDescription:
    "Pages resolve through layouts and regions to a shared section vocabulary, and a theme carries its design tokens in the W3C DTCG shape that Style Dictionary, Figma and Tokens Studio already export, so the commonest change to a site is a reviewable field edit rather than a stylesheet rewrite.",
  eyebrow: "Rendering and themes",
  title: "A site can exist before",
  titleAccent: "a front end does",
  lead: "The built-in renderer is what makes a draft previewable, an agent's work checkable and a replica servable on the day it is captured, whether or not a Next.js app of your own sits in front. A page resolves through a layout and its regions to sections from a vocabulary the agent, the console and the front end all share.",
  blocks: [
    {
      title: "One vocabulary, three consumers",
      body: "A page is an ordered list of known sections rather than an ad-hoc content model per site. That is what lets an agent compose a page it has never seen the design of, a console render an editor for it without configuration, and a front end import the matching components from a published package.",
      law: "P8",
    },
    {
      title: "A theme's tokens are declared data, in a vocabulary that was adopted rather than invented",
      body: "A theme carries its tokens in the W3C Design Tokens Community Group shape, the same thing Style Dictionary, Figma and Tokens Studio already export, so a designer's file pastes in and no dialect is invented for an agent to learn. They compile to custom properties on :root and are prepended to the theme's own CSS, so every existing layout gets tokens without being edited; shipping them behind a separate template handle would have shipped the feature switched off. The same values are also a dotted map for the places var() cannot reach.",
      code: {
        caption: "Theme.TOKENS, and what it compiles to",
        text: `{ "color": { "accent": { "$value": "#0b5" } } }

/* prepended to the theme CSS */
:root { --color-accent: #0b5; }

{{! and for where var() cannot reach }}
{{theme.tokens.color.accent}}`,
      },
      law: "P5",
    },
    {
      title: "Why that matters: the commonest change stops being a stylesheet rewrite",
      body: "The accent colour, the type scale, the density: these are what actually change about a site, and as raw CSS each one is a diff a curator cannot review and an agent cannot make safely. As declared fields they are a reviewable edit with a name. Raw CSS stays as the escape hatch rather than the only door, and a theme with no tokens emits nothing at all rather than an empty block, because the render digest is over exactly those bytes.",
      law: "P7",
    },
    {
      title: "A capture reads tokens too, and says which ones are real",
      body: "Replication runs the argument backwards: a source's stylesheet contains its palette, its type scale and its spacing rhythm, so a conversion that concatenates the file into CSS throws all of it away and the replica looks right but cannot be changed. Eight groups are read (colour, font family, font size, font weight, spacing, radius, border width and shadow), and the report tells two kinds apart. A declared custom property is the strong case: the source's own CSS already says var(), so changing the token changes the replica. A recurring literal is weaker: writing #0b5 twenty-three times is the accent colour, and naming it is how a curator learns the palette exists, but nothing references the name until somebody rewires the CSS.",
      law: "P9",
    },
    {
      title: "What it deliberately will not do",
      body: "Rewrite those twenty-three literals into var() calls across a stranger's stylesheet. This scans CSS; it does not parse it into a model it could safely rewrite. A search-and-replace over somebody else's bytes, one of them inside a content: string or a data URI, breaks the replica in a way no digest catches. A token whose name would not round-trip back to the property the source actually wrote is reported rather than written, because nothing gets guessed into a file nobody reviewed.",
      law: "P7",
    },
  ],
  related: [
    { label: "Replicate an existing site", href: "/replicate/" },
    { label: "Delivery", href: "/features/delivery/" },
  ],
};

const DELIVERY: FeatureDetail = {
  slug: "delivery",
  metaTitle: "Delivery: REST, GraphQL and the TypeScript SDKs | Viglet Shio",
  metaDescription:
    "Headless where it counts: a framework-agnostic delivery client with zero runtime dependencies, a React SDK over the same core, the shared section vocabulary, a generated Next.js starter and a bridge that makes any front end inline-editable.",
  eyebrow: "Delivery",
  title: "Headless where it counts:",
  titleAccent: "the front end is yours",
  lead: "The content engine is a JVM service and nothing you build has to know that. What reaches your application is HTTP, a typed client with no runtime dependencies, and components you can take or leave.",
  blocks: [
    {
      title: "A client that runs wherever your code does",
      body: "Zero runtime dependencies, which is a constraint rather than a boast: it is what lets the same package work in Node, in a browser, on an edge runtime, in a server component and in a plain script, and what keeps a CMS client from dragging a dependency tree into your bundle.",
      code: {
        caption: "@viglet/shio-client",
        text: `import { createShioClient } from "@viglet/shio-client";

const shio = createShioClient({
  url: "https://cms.example.com",
  token: process.env.SHIO_CDA_TOKEN,
});

const page = await shio.post("acme", "launch");`,
      },
    },
    {
      title: "The starter is generated, not copied",
      body: "A Next.js App Router site wired to a real site's content, produced by a generator rather than pasted out of a tutorial. With a blueprint it brings the content half in the same command, so what you get on the first run is a site with pages in it.",
      code: {
        caption: "one command",
        text: `npm create @viglet/shio-app
# or, with the content:
shio init --blueprint corporate --site acme`,
      },
      law: "P8",
    },
    {
      title: "Inline editing on a front end you wrote",
      body: "The renderer annotates what it renders, and a published bridge package does the same job for markup you produce yourself. Once a page emits the annotations, a curator clicks the headline on the real page and edits the field that produced it, on the draft, with the same safety as every other write.",
    },
    {
      title: "A token scope per job",
      body: "Read for published content, preview for drafts, write for the editing path, and a separate agent scope that reads like preview and writes only on the agent surface. They are four scopes rather than a hierarchy on purpose: the agent's credential is deliberately not a superset of the one that can patch published content.",
      law: "P7",
    },
  ],
  related: [
    { label: "Rendering and themes", href: "/features/rendering/" },
    { label: "The curator’s half", href: "/curator/" },
  ],
};

export const FEATURE_DETAILS: Record<string, FeatureDetail> = {
  "agent-gateway": AGENT_GATEWAY,
  "content-as-files": CONTENT_AS_FILES,
  blueprints: BLUEPRINTS,
  rendering: RENDERING,
  delivery: DELIVERY,
};

export const FEATURE_SLUGS = Object.keys(FEATURE_DETAILS);
