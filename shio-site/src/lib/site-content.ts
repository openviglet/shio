import {
  Boxes,
  Bot,
  Braces,
  ClipboardCheck,
  Eye,
  FileCode2,
  FileStack,
  FolderTree,
  Gauge,
  GitCompareArrows,
  Layers,
  Lock,
  Radar,
  Route,
  ScrollText,
  ShieldCheck,
  Sparkles,
  TerminalSquare,
  Undo2,
  Workflow,
  type LucideIcon,
} from "lucide-react";

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * All page copy lives here; `src/components/sections/` only renders it.
 *
 * Everything on this page is a claim about a real surface, so the rule for
 * editing this file is the rule the product itself is built on: if a number,
 * a route, a tool name or an error body appears below, it is the one the build
 * produces. `docs/agents/` is the authority for each; where a figure came from
 * a measurement, the test that measures it is named in a comment.
 * ─────────────────────────────────────────────────────────────────────────────
 */

/* ── The ten design laws (agents.md) ─────────────────────────────────────── */

export interface Law {
  id: string;
  title: string;
  gloss: string;
  /** The section on this page where the law is shown rather than stated. */
  anchor?: string;
}

export const LAWS: Law[] = [
  {
    id: "P1",
    title: "Agent-first design order",
    gloss:
      "Every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete.",
    anchor: "#surface",
  },
  {
    id: "P2",
    title: "One call replaces a session",
    gloss:
      "Discovery is a product feature — a manifest and a context pack — not a documentation problem.",
    anchor: "#one-call",
  },
  {
    id: "P3",
    title: "Tokens are a measured budget",
    gloss:
      "Response budgets plus a CI benchmark. A token regression fails the build like any other test.",
    anchor: "#one-call",
  },
  {
    id: "P4",
    title: "Files beat APIs for authoring",
    gloss:
      "Content is projected to disk, because Edit, Write and Grep are an agent's cheapest tools.",
    anchor: "#files",
  },
  {
    id: "P5",
    title: "Determinism over cleverness",
    gloss:
      "Stable ordering, path addressing, idempotent writes, byte-identical serialization.",
    anchor: "#files",
  },
  {
    id: "P6",
    title: "Errors are instructions",
    gloss:
      "Every 4xx carries fix, allowed, didYouMean and example — so the retry is the next call, not the next session.",
    anchor: "#errors",
  },
  {
    id: "P7",
    title: "Never surprise the human",
    gloss:
      "Draft-default, dry-run, explicit publish, confirm tokens, attribution, a review queue.",
    anchor: "#human",
  },
  {
    id: "P8",
    title: "Skipping steps needs appliable units",
    gloss: "Blueprints — a package that applies — instead of a recipe written in prose.",
    anchor: "#blueprints",
  },
  {
    id: "P9",
    title: "Close the perception loop",
    gloss:
      "Cheap textual proof — a lint, the routes, a render digest — instead of a human round trip.",
    anchor: "#verify",
  },
  {
    id: "P10",
    title: "Compose, don't fork",
    gloss:
      "MCP is a shape over the delivery API and the console services, never a third contract.",
    anchor: "#surface",
  },
];

/* ── The hero transcript ─────────────────────────────────────────────────── */

export interface TranscriptStep {
  /** What the agent is doing, in the agent's own vocabulary. */
  call: string;
  /** The tool or command behind it. */
  tool: string;
  /** One line of what came back. */
  result: string;
  /** Which law this step is an instance of. */
  law: string;
  /** The artefact the call produced, shown beside the transcript (SH531). */
  artefact: {
    label: string;
    lines: string[];
    /**
     * Colour the plus and minus prefixes as a diff. Explicit rather than
     * sniffed from the prefix: the handoff artefact is Markdown, and every one
     * of its list items starts with a hyphen, which a heuristic paints as a
     * deletion.
     */
    diff?: true;
  };
}

/**
 * A session that builds one page, start to finish. Scripted — and the page says
 * so, because a recording dressed as a live connection is exactly the thing
 * this product's design laws refuse. SH531 turns it into a stepped player and
 * SH540 points it at a public instance.
 */
export const TRANSCRIPT: TranscriptStep[] = [
  {
    call: "What is this instance, and what can I do to it?",
    tool: "shio_context",
    result: "model + sitemap + conventions + write vocabulary — 1 call, 1 digest to skip it next time",
    law: "P2",
    artefact: {
      label: "the context pack",
      lines: [
        "site:      acme",
        "postTypes: Page, Article, Author, File",
        "sitemap:   42 posts, 9 folders",
        "ops:       post.upsert, post.move, folder.upsert, ...",
        "digest:    9f2c41 - send this back to skip the re-read",
      ],
    },
  },
  {
    call: "Give me the site on disk",
    tool: "shio pull --content",
    result: "shio/content/**  ·  42 posts, 9 folders, front-mattered Markdown",
    law: "P4",
    artefact: {
      label: "shio/content/acme/",
      lines: [
        "index.md            post:acme/",
        "launch.md           post:acme/launch",
        "blog/hello.md       post:acme/blog/hello",
        "...  39 more",
      ],
    },
  },
  {
    call: "Write the launch page",
    tool: "Edit  shio/content/acme/launch.md",
    result: "one file, one post — no API call to author with",
    law: "P4",
    artefact: {
      label: "the edit, as a diff",
      diff: true,
      lines: [
        " ---",
        " postType: Page",
        "-title: Untitled",
        "+title: Launching Shio 2026.3",
        " status: DRAFT",
        " ---",
        "+",
        "+Shio is a CMS whose primary operator is a coding agent.",
      ],
    },
  },
  {
    call: "Push it back",
    tool: "shio push --content",
    result: "1 created  ·  status DRAFT  ·  nothing is live yet",
    law: "P7",
    artefact: {
      label: "what the instance now holds",
      lines: [
        "created  post:acme/launch      DRAFT",
        "",
        "0 published. The live site is unchanged.",
      ],
    },
  },
  {
    call: "Prove it before a human looks",
    tool: "shio verify --delivery",
    result: "ok — 0 errors, 1 warning: hero image has no alt text",
    law: "P9",
    artefact: {
      label: "the proof, before a human looks",
      lines: [
        "ok    post:acme/            200  layout Home",
        "ok    post:acme/launch      200  layout Page",
        "warn  post:acme/launch      hero image has no alt text",
        "",
        "2 checked, 0 errors, 1 warning   exit 0",
      ],
    },
  },
  {
    call: "Hand it over",
    tool: "shio report --since last",
    result: "a Markdown summary with a preview link per change, and the warning as an open question",
    law: "P7",
    artefact: {
      label: "the handoff, as Markdown",
      lines: [
        "## 1 change awaiting review",
        "",
        "- **Launching Shio 2026.3** - created",
        "  preview: /preview/acme/launch",
        "",
        "### Open questions",
        "- The hero image has no alt text. Intentional?",
      ],
    },
  },
];

/* ── The three roles ─────────────────────────────────────────────────────── */

export interface Role {
  icon: LucideIcon;
  actor: string;
  verb: string;
  description: string;
  surface: string[];
}

export const ROLES: Role[] = [
  {
    icon: Bot,
    actor: "The agent",
    verb: "builds",
    description:
      "Discovers the model in one call, authors content as files, applies a blueprint, renders a preview and proves the result — without a browser, a cookie or a click.",
    surface: ["MCP", "shio CLI", "/api/v2/agent"],
  },
  {
    icon: ClipboardCheck,
    actor: "The human",
    verb: "curates",
    description:
      "Approves, corrects and tweaks. Everything the agent wrote arrives as a draft with attribution on it, in a queue, with a preview link that opens the real page.",
    surface: ["React console", "Universal Editor", "Preview links"],
  },
  {
    icon: Route,
    actor: "The CDA",
    verb: "delivers",
    description:
      "A framework-agnostic delivery API — REST and GraphQL — with TypeScript SDKs, a React SDK and a Next.js starter that is generated rather than copied.",
    surface: ["REST", "GraphQL", "@viglet/shio-client"],
  },
];

/* ── P2 / P3: what a call costs ────────────────────────────── */

/**
 * SH533 — generated, never typed. `scripts/token-budgets.mjs` reads the build's
 * own `token-budgets.properties` — the single file the Java suite asserts every
 * response against — and writes the JSON imported below, on every build. A
 * renamed key fails the build rather than dropping a bar.
 *
 * They are **ceilings**, not measurements: what the build refuses to exceed. That
 * is the honest thing to publish, because it is the half that is machine-readable
 * and therefore the half that cannot silently drift.
 */
export { default as TOKEN_BUDGETS } from "./token-budgets.generated.json";

/* ── P1 / P10: the surface ───────────────────────────────────────────────── */

export const MCP_TOOLS = [
  { name: "shio_context", gloss: "model, sitemap and conventions in one call" },
  { name: "shio_find", gloss: "search the tree by address, type or term" },
  { name: "shio_read", gloss: "one post, one folder, one schema" },
  { name: "shio_write", gloss: "a batch of ops — upsert, move, delete" },
  { name: "shio_apply", gloss: "converge to a document, or to a blueprint" },
  { name: "shio_publish", gloss: "the separate, explicit act" },
  { name: "shio_assets", gloss: "binaries in and out of the media library" },
  { name: "shio_changes", gloss: "what moved since a cursor" },
  { name: "shio_verify", gloss: "the lint, and optionally the route proof" },
  { name: "shio_digest", gloss: "one page's structure, as text" },
  { name: "shio_remember", gloss: "what session N leaves for session N+1" },
  { name: "shio_marketplace", gloss: "packages this instance could install" },
];

export const MCP_RESOURCES = [
  "shio://manifest",
  "shio://context",
  "shio://ops",
  "shio://blueprints",
  "shio://schema/{postType}",
  "shio://blueprint/{name}",
];

export const CLI_VERBS = [
  { verb: "shio init", gloss: "scaffold a front end, or apply a blueprint" },
  { verb: "shio pull / push", gloss: "the model and the content, as files" },
  { verb: "shio apply", gloss: "a desired-state document; --check is the CI drift guard" },
  { verb: "shio verify", gloss: "the content lint; --delivery adds the route proof" },
  { verb: "shio digest", gloss: "a page's structure, cheap enough to read every turn" },
  { verb: "shio audit", gloss: "the browser as an instrument — overflow, contrast, dead requests" },
  { verb: "shio snapshot", gloss: "a PNG, for the human who has to look" },
  { verb: "shio clone", gloss: "capture an existing site, writing no content" },
  { verb: "shio report", gloss: "the handoff — what changed, with a preview link each" },
  { verb: "shio remember", gloss: "the instance memory, so the next session is cheaper" },
];

/* ── P4 / P5: content as files ───────────────────────────────────────────── */

export const CONTENT_TREE = `shio/
├─ post-types/
│  ├─ Page.json                 the model, as code
│  └─ Article.json
├─ content/
│  └─ acme/
│     ├─ index.md               post:acme/          the home page
│     ├─ launch.md              post:acme/launch
│     └─ blog/
│        └─ hello-world.md      post:acme/blog/hello-world
├─ assets/
│  └─ acme/img/hero.png
└─ .shio/
   └─ content-base.json         the fingerprint the 3-way merge reads`;

export const CONTENT_FILE = `---
postType: Page
title: Launching Shio 2026.3
status: DRAFT
folder: folder:acme/
---

Shio is a CMS whose primary operator is a coding agent.
The human curates. The delivery API delivers.`;

/* ── P6: an error that is an instruction ─────────────────────────────────── */

export const TEACHING_ERROR = `{
  "status": 400,
  "title": "Unknown post type 'Artcile'",
  "detail": "No post type by that name exists on site 'acme'.",
  "didYouMean": ["Article"],
  "allowed": ["Page", "Article", "Author", "File"],
  "fix": "Use one of 'allowed', or create the type first with post-type.upsert.",
  "example": {
    "op": "post.upsert",
    "address": "post:acme/blog/hello-world",
    "postType": "Article"
  }
}`;

/* ── P7: what stops it ───────────────────────────────────────────────────── */

export interface Guardrail {
  icon: LucideIcon;
  title: string;
  description: string;
}

export const GUARDRAILS: Guardrail[] = [
  {
    icon: Eye,
    title: "Draft by default",
    description:
      "A write lands as a draft. Publishing is a separate, explicit act with its own op — there is no flag that quietly does both.",
  },
  {
    icon: GitCompareArrows,
    title: "Dry run first",
    description:
      "Every batch can be planned instead of applied. The plan is the same document, annotated with what each op would change.",
  },
  {
    icon: Lock,
    title: "Confirm tokens",
    description:
      "A destructive op is refused once with a token, and accepted only when the token comes back — so no single call deletes anything.",
  },
  {
    icon: ScrollText,
    title: "Attribution on every write",
    description:
      "The record says which agent wrote it and when. A curator reviewing a change never has to guess where it came from.",
  },
  {
    icon: ClipboardCheck,
    title: "A queue, not a firehose",
    description:
      "Changes arrive in a review queue with a preview link each, plus the verify warnings restated as open questions.",
  },
  {
    icon: Undo2,
    title: "Nothing is gone",
    description:
      "Deletes land in the trash with untrash and purge as separate acts, and the file projection keeps a fingerprint for the three-way merge.",
  },
];

/* ── P9: the perception loop ─────────────────────────────────────────────── */

export interface Proof {
  icon: LucideIcon;
  command: string;
  title: string;
  description: string;
}

export const PROOFS: Proof[] = [
  {
    icon: ShieldCheck,
    command: "shio verify",
    title: "The content lint",
    description:
      "Broken references, undeclared fields, a field a template reads that no type declares. Exits 1 on an error; --delivery adds a proof that the route actually answers.",
  },
  {
    icon: FileCode2,
    command: "shio digest",
    title: "The page, as text",
    description:
      "One page's structure — regions, sections, what each resolved to — cheap enough to read after every edit instead of asking somebody to look.",
  },
  {
    icon: Radar,
    command: "shio audit",
    title: "The browser as an instrument",
    description:
      "Renders at each width and prints findings, not a picture: horizontal overflow reported where it is introduced, contrast, collapsed boxes, failed requests, console errors.",
  },
  {
    icon: Gauge,
    command: "shio snapshot",
    title: "A picture, for the human",
    description:
      "The one output that is deliberately for a person. A per-pixel diff against a baseline, and a draft is stamped as a draft because the image cannot say so itself.",
  },
];

/* ── P8: blueprints ──────────────────────────────────────────────────────── */

export const BLUEPRINT_STEPS = [
  {
    title: "List what applies",
    code: "shio blueprints",
    gloss: "the catalogue, with the parameters each package takes",
  },
  {
    title: "Apply one",
    code: "shio apply --blueprint corporate --param brand=Acme --site acme",
    gloss: "post types, folders, content and carried files, in one transaction",
  },
  {
    title: "Or scaffold from it",
    code: "shio init --blueprint corporate --site acme",
    gloss: "the front end and the content half in the same command",
  },
];

/* ── Replication ─────────────────────────────────────────────────────────── */

export const CLONE_STEPS = [
  {
    step: "Capture",
    code: "shio clone https://example.com",
    gloss:
      "walks one origin under robots.txt and writes an inventory — pages, assets, locales, forms, navigation, third-party scripts — and no content at all.",
  },
  {
    step: "Convert",
    code: "shio convert --site example",
    gloss:
      "derives post types from the pages themselves, proposes them for review, and turns the capture into content a curator can edit.",
  },
  {
    step: "Prove",
    code: "shio verify --delivery && shio audit /",
    gloss:
      "the replica answers its own routes, and the audit says whether the page a reader gets is the page you meant.",
  },
];

/* ── Delivery ────────────────────────────────────────────────────────────── */

export interface Pkg {
  name: string;
  gloss: string;
  href: string;
}

export const PACKAGES: Pkg[] = [
  {
    name: "@viglet/shio-client",
    gloss:
      "Framework-agnostic TypeScript client for the delivery API. Zero runtime dependencies — Node, browser, edge, server components.",
    href: "https://www.npmjs.com/package/@viglet/shio-client",
  },
  {
    name: "@viglet/shio-react-sdk",
    gloss: "Hooks and render components over the same core.",
    href: "https://www.npmjs.com/package/@viglet/shio-react-sdk",
  },
  {
    name: "@viglet/shio-sections",
    gloss:
      "The page-section vocabulary — one shared language for the agent, the console and the front end.",
    href: "https://www.npmjs.com/package/@viglet/shio-sections",
  },
  {
    name: "@viglet/create-shio-app",
    gloss: "Generates a Next.js App Router site already wired to a site's content.",
    href: "https://www.npmjs.com/package/@viglet/create-shio-app",
  },
  {
    name: "@viglet/shio-editor-cors",
    gloss:
      "The Universal Editor bridge — inline editing on any front end that emits the data attributes.",
    href: "https://www.npmjs.com/package/@viglet/shio-editor-cors",
  },
  {
    name: "@viglet/shio",
    gloss: "The CLI. Also the MCP stdio bridge: claude mcp add shio -- shio mcp.",
    href: "https://www.npmjs.com/package/@viglet/shio",
  },
];

/* ── Comparison ──────────────────────────────────────────────────────────── */

export const COMPARISON_COLUMNS = {
  shio: "Shio",
  headless: "Headless SaaS CMS",
  diy: "Git-backed / DIY",
};

export interface ComparisonRow {
  feature: string;
  shio: string;
  headless: string;
  diy: string;
}

/**
 * The axis is agent-operability, not features — every headless CMS has content
 * types and a delivery API. SH534 turns this teaser into a page that also names
 * where each alternative genuinely wins, which is what makes the rest credible.
 */
export const COMPARISON: ComparisonRow[] = [
  { feature: "One call replaces discovery", shio: "yes", headless: "no", diy: "n/a" },
  { feature: "MCP server, first-class", shio: "yes", headless: "partial", diy: "build it" },
  { feature: "Content addressable as files", shio: "yes", headless: "no", diy: "yes" },
  { feature: "4xx bodies carry the fix", shio: "yes", headless: "no", diy: "n/a" },
  { feature: "Dry run before every write", shio: "yes", headless: "partial", diy: "git diff" },
  { feature: "Draft-default + review queue", shio: "yes", headless: "yes", diy: "pull request" },
  { feature: "Token cost measured in CI", shio: "yes", headless: "no", diy: "no" },
  { feature: "Textual render proof", shio: "yes", headless: "no", diy: "build it" },
  { feature: "Capture an existing site", shio: "yes", headless: "no", diy: "build it" },
  { feature: "Self-host, your database", shio: "yes", headless: "no", diy: "yes" },
];

/* ── /curator — the human half ───────────────────────────────────────────── */

export interface CuratorMoment {
  when: string;
  title: string;
  body: string;
}

/**
 * What a curator's Tuesday actually looks like — deliberately a narrative and
 * not an architecture diagram, because the reader this page is for does not
 * want to know which service owns the review session.
 */
export const CURATOR_DAY: CuratorMoment[] = [
  {
    when: "Overnight",
    title: "The agent worked, and published nothing",
    body: "It read the model, wrote nine pages and proved them against the lint. Every one of them is a draft. The site your readers see has not changed.",
  },
  {
    when: "09:10",
    title: "A queue, not a diff to hunt through",
    body: "“The agent proposed 9 changes.” One session, what each change touched, and two buttons that mean something: approve, or revert.",
  },
  {
    when: "09:14",
    title: "You open the page before anyone else can",
    body: "A preview link opens the real rendered page, from a token that expires and is scoped to that one post — not a staging environment somebody has to keep in sync.",
  },
  {
    when: "09:22",
    title: "You fix the headline where you can see it",
    body: "Inline, on the rendered page itself, through the visual editor — rather than hunting for the field in a form that looks nothing like the result.",
  },
  {
    when: "09:30",
    title: "You publish, or you schedule it",
    body: "Publishing is its own act. Or set a date and let the sweep fire it — the same path, so anything listening for a publish hears the identical event.",
  },
];

export interface Gate {
  icon: LucideIcon;
  title: string;
  mechanism: string;
  /** The design decision behind it, stated as the reason rather than the rule. */
  because: string;
}

export const GATES: Gate[] = [
  {
    icon: Eye,
    title: "Writing and publishing are different permissions",
    mechanism:
      "An agent's write lands in DRAFT, and whether a credential may publish at all is a property of the token rather than of the call.",
    because:
      "Anything able to write was previously able to publish, which made draft-by-default a convention instead of a gate.",
  },
  {
    icon: Lock,
    title: "A delete takes two calls",
    mechanism:
      "A delete, a folder delete or any prune that would remove something is refused once with a confirm digest over exactly those deletions; the real run has to present it back.",
    because:
      "A digest over the deletions means the second call cannot quietly cover more than the first one showed you.",
  },
  {
    icon: GitCompareArrows,
    title: "The plan is the same document as the write",
    mechanism:
      "Any batch can be sent as a dry run. What comes back is that batch annotated with what each op would change — not a summary of it.",
    because:
      "A preview in a different shape from the thing being previewed is a second implementation, and the two drift.",
  },
  {
    icon: ClipboardCheck,
    title: "The agent cannot approve its own work",
    mechanism:
      "The review queue is console-authenticated and deliberately not on the agent namespace at all. An agent token cannot reach the approve call.",
    because:
      "A credential that can both make a change and sign it off is not a review step, it is a log line.",
  },
  {
    icon: Undo2,
    title: "Deleting is reversible, and so is a folder",
    mechanism:
      "A deleted post goes to the trash with restore and purge as separate acts. A deleted folder leaves a trace that can be undone.",
    because:
      "A mis-click that loses content is the failure a curator cannot recover from, and the one nobody reports until much later.",
  },
  {
    icon: ScrollText,
    title: "Every change has a name and a time on it",
    mechanism:
      "The history is written and readable, with an Activity view in the console. A published post is an immutable snapshot beside the editable draft, so the diff is always available.",
    because:
      "Reviewing a change you cannot attribute is reviewing a rumour.",
  },
];

export const LIFECYCLE = [
  {
    state: "DRAFT",
    gloss: "The editable row. Every agent write lands here, and so does yours.",
  },
  {
    state: "PREVIEW",
    gloss: "An expiring, single-post token that opens the real rendered page.",
  },
  {
    state: "PUBLISHED",
    gloss: "An immutable snapshot beside the draft — which is what makes diff and restore cheap.",
  },
  {
    state: "SCHEDULED",
    gloss: "A publish or unpublish instant; a sweep fires it down the same path.",
  },
  {
    state: "TRASH",
    gloss: "Reversible. Restore and purge are separate acts, and purge is the one nobody does by accident.",
  },
];

/* ── /replicate — capture, propose, convert, prove ───────────────────────── */

export interface Phase {
  verb: string;
  title: string;
  command: string;
  what: string;
  /** The one promise this phase makes that the next one depends on. */
  promise: string;
}

export const PHASES: Phase[] = [
  {
    verb: "clone",
    title: "Capture",
    command: "shio clone https://example.com --max-pages 50",
    what: "Walks one origin under robots.txt and writes an inventory: the pages with titles and paths, the assets they reference, the locales and how each shows up in a URL, the forms and their fields, the navigation as the source spells it, and the third-party scripts.",
    promise:
      "It writes no content at all. The thing you are about to be asked to review does not already exist.",
  },
  {
    verb: "propose",
    title: "Propose",
    command: "shio propose --site example",
    what: "Reads the capture and reports candidates: for each, the pages it recurs on, the section type it looks like and why, the fields it would carry, and what accepting it would set aside. Accepting writes one line into a plan file.",
    promise:
      "It applies nothing — no post, no post type, no layout. Review and mutation in one command is the surprise law P7 forbids.",
  },
  {
    verb: "convert",
    title: "Convert",
    command: "shio convert --site example --dry-run",
    what: "Turns the accepted plan into the desired-state document that `shio apply` already takes, and sends it down that same path. One post per candidate, not per page — the hero that recurs on twelve pages is one section reused.",
    promise:
      "Every post lands in DRAFT, and `publish` is absent rather than false — so re-running can never unpublish a page a curator has since published.",
  },
  {
    verb: "diff",
    title: "Prove",
    command: "shio diff --against capture --site example",
    what: "Walks the capture's pages, asks the instance for each one, and reads the served bytes with the same scanner that wrote the declared counts. Internal links, external links, assets, forms and prose — arithmetic, not a second opinion.",
    promise:
      "A fact the replica gained is never a failure; a lost one exits 1. Every artefact carries the identity of what declared it, so nothing is guessed or re-fetched.",
  },
];

export interface Mode {
  name: string;
  keeps: string;
  offers: string;
  pick: string;
  accent: "fidelity" | "authorable";
}

/**
 * The choice the whole pipeline exists to put in front of a reviewer, and the
 * one thing on this page that took a measurement to get right (SH507): a mode
 * that tried to be both delivered a wall of unstyled text, so the *promise*
 * changed rather than the implementation being patched.
 */
export const MODES: Mode[] = [
  {
    name: "Fidelity",
    keeps: "The source's appearance, exactly — the block's markup is written verbatim into a layout.",
    offers: "No fields. A curator editing “the hero headline” is editing an HTML file.",
    pick: "the page has to keep looking like it looks, and nobody is going to edit it.",
    accent: "fidelity",
  },
  {
    name: "Authorable",
    keeps: "The content, as structured fields a curator edits in the console or on the page itself.",
    offers: "The site's own theme. The source's styling is replaced, deliberately — not ruined.",
    pick: "somebody is going to maintain this site, and the model matters more than the pixels.",
    accent: "authorable",
  },
];

/** The bounds, which are the reason the capture is credible rather than a caveat. */
export const CLONE_BOUNDS = [
  {
    title: "One origin, and no further",
    body: "Every off-host link is recorded and never followed. A seed that redirects off-host is refused, naming the host it actually served — because example.com → www.example.com followed silently files every path under an origin nobody named.",
  },
  {
    title: "robots.txt decides, and the answer is recorded",
    body: "An exact agent group beats * outright, the longest rule wins and Allow wins a tie. --ignore-robots is written into the inventory, so a capture taken without permission can never later look like one taken with it.",
  },
  {
    title: "A ceiling marks the capture PARTIAL",
    body: "No silent caps. A bounded capture read as a complete one is the failure the whole file exists to prevent, so --max-pages says so in the artefact rather than in your memory of the command you ran.",
  },
  {
    title: "Assets are same-origin, and the rest are named",
    body: "Mirroring a CDN's bytes into somebody's media library is a licensing decision this tool is not entitled to make — but somebody has to be able to, so the hosts left behind are listed rather than dropped.",
  },
  {
    title: "Deterministic, so two runs can be diffed",
    body: "Sequential over a sorted frontier, and no timestamp in the file — --max-pages 50 cuts the same fifty pages every run. The inventory exists to be compared with the next crawl, and a set that moves on its own makes every diff noise.",
  },
  {
    title: "It is somebody's content",
    body: "The tool records what it was allowed to do and refuses to guess about the rest. Capturing a site you do not own is a decision about their content, and it stays your decision.",
  },
];

export const PROPOSE_OUTPUT = `$ shio propose --site example

  fidelity keeps the source's appearance and offers no fields;
  authorable gives these fields, presented by the site's own theme.

  hero-a91c   Hero          on 12 pages
              fields: heading, subheading, image, ctaLabel, ctaHref
              discards: 3x<iframe>  1x onclick
              replaces: 22 class name(s) of the source's styling

  grid-4f02   CardGrid      on 6 pages
              fields: title, items[].title, items[].href
              replaces: 41 class name(s) of the source's styling

  2 candidate(s) - accept with --accept hero-a91c grid-4f02`;

/* ── /compare — the matrix, and where it loses ───────────────────────────── */

export const COMPARE_COLUMNS = ["Shio", "Contentful", "Sanity", "Strapi", "WordPress"] as const;

export interface CompareRow {
  /** The capability, phrased so it can be checked rather than argued. */
  feature: string;
  /** One value per column in COMPARE_COLUMNS order. */
  cells: string[];
}

export interface CompareGroup {
  law: string;
  title: string;
  rows: CompareRow[];
}

/**
 * The axis is agent-operability, because every headless CMS has content types
 * and a delivery API and comparing those produces a tie. Each row is one of the
 * ten laws turned into a question with a checkable answer.
 *
 * **How to read the competitor columns.** They say whether the product
 * *documents the property as a guarantee*, not whether some path exists — an
 * export command is not a file projection with a three-way merge, and an MCP
 * server over an API designed for a browser is not a protocol designed for an
 * agent. Where a product's answer is "you can build it", the cell says so.
 * Verify against current vendor documentation before deciding; this reflects
 * 2026.3.
 */
export const COMPARE_GROUPS: CompareGroup[] = [
  {
    law: "P2",
    title: "Discovery — what does the agent's first call cost?",
    rows: [
      {
        feature: "One call returns the model, the sitemap and the conventions",
        cells: ["yes", "compose several", "compose several", "compose several", "compose several"],
      },
      {
        feature: "A machine-readable capability manifest, with unshipped ones reported false",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        feature: "A digest so a later session can skip re-reading it",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        // SH601 — the finding behind this row is that the manifest was already complete, and
        // completeness was never the constraint: mapping an intent onto it was the unbudgeted work.
        feature: "The manifest routes an intent to the one call, instead of listing everything",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
  {
    law: "P4",
    title: "Authoring — can the agent use its cheapest tools?",
    rows: [
      {
        feature: "The whole site projected to files, with a three-way merge back",
        cells: ["yes", "export only", "export only", "no", "no"],
      },
      {
        feature: "Path addressing — no UUID carried between calls",
        cells: ["yes", "partial", "partial", "partial", "partial"],
      },
      {
        feature: "Idempotent desired-state apply (converge, don't script)",
        cells: ["yes", "build it", "build it", "build it", "build it"],
      },
    ],
  },
  {
    law: "P6",
    title: "Failure — how expensive is a wrong call?",
    rows: [
      {
        feature: "Every 4xx carries fix, allowed, didYouMean and example",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        feature: "Every capability reachable without a browser session",
        cells: ["yes", "partial", "partial", "partial", "partial"],
      },
    ],
  },
  {
    law: "P7",
    title: "Safety — what stops a wrong write?",
    rows: [
      {
        feature: "Dry run: the plan before the batch, as the same document",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        feature: "A confirm token on a destructive op",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        feature: "Draft-default and a review queue",
        cells: ["yes", "yes", "yes", "yes", "yes"],
      },
    ],
  },
  {
    law: "P9",
    title: "Proof — can the agent check its own work?",
    rows: [
      {
        feature: "A textual render digest of a page's structure",
        cells: ["yes", "no", "no", "no", "no"],
      },
      {
        feature: "A content lint that also proves the route answers",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
  {
    law: "P3",
    title: "Economy — is the protocol's cost a number?",
    rows: [
      {
        feature: "Response token budgets with a ceiling the build enforces",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
  {
    law: "—",
    title: "Operations — whose infrastructure, whose source?",
    rows: [
      {
        feature: "Self-host on your own database",
        cells: ["yes", "no", "no", "yes", "yes"],
      },
      { feature: "Open source", cells: ["yes", "no", "partial", "yes", "yes"] },
      {
        feature: "Capture an existing site into an authorable one",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
];

export interface Rival {
  name: string;
  wins: string;
  chooseIt: string;
}

/**
 * Where each alternative genuinely wins. Turing's compare page earns its
 * credibility this way and this one has to do the same, or the matrix above is
 * worth nothing: a table where one column is all ticks is a table nobody reads
 * twice.
 */
export const RIVALS: Rival[] = [
  {
    name: "Contentful",
    wins: "The enterprise ecosystem — integrations, SLAs, a global CDN you do not operate, and an editor a marketing team already knows how to use.",
    chooseIt:
      "your buyer is procurement, the content team is large and non-technical, and nobody on staff wants to run a JVM.",
  },
  {
    name: "Sanity",
    wins: "The best structured-content editing experience in the category — real-time collaboration, a genuinely good query language, and a studio you shape to the model.",
    chooseIt:
      "editors are the primary operator and the depth of the editing experience is the thing you are buying.",
  },
  {
    name: "Strapi",
    wins: "The closest neighbour: open source, self-hosted, Node all the way down, and a plugin ecosystem that fits a JavaScript team's existing habits.",
    chooseIt:
      "your team is JS end to end, you want self-hosting, and you do not need an agent protocol — it is a shorter path to the same content API.",
  },
  {
    name: "WordPress",
    wins: "A plugin for everything and the largest talent pool on earth. For a content site with commodity requirements, nothing is cheaper to staff.",
    chooseIt:
      "the requirements are ordinary, the budget is small, and the people maintaining it in three years are not the people building it now.",
  },
];

export interface Objection {
  q: string;
  a: string;
}

export const OBJECTIONS: Objection[] = [
  {
    q: "Everyone has an MCP server now. Isn't that the same thing?",
    a: "An MCP server is a transport. Put one over an API designed for a browser and the agent gets that API's protocol quality — the same UUID plumbing, the same 400 with no instruction, the same discovery session. The difference this page is about is what the protocol guarantees underneath, which is why every row above is a property you can test rather than a feature you can be told about.",
  },
  {
    q: "Do I have to use an agent?",
    a: "No. There is a React console a curator works in, a delivery API with TypeScript SDKs, a Next.js starter and a visual editor that edits the rendered page. The agent-first ordering means every capability reaches the agent first — not that it reaches nobody else.",
  },
  {
    q: "Java and Spring, in 2026?",
    a: "One JVM service in a container, with the content engine, the agent gateway, the MCP server and the renderer in the same process. Rewriting it in Node is a standing non-goal: it is high cost, high risk and zero user-visible value. What you interact with is HTTP, a CLI and files.",
  },
  {
    q: "What happens to my front end?",
    a: "Nothing — it is headless. REST and GraphQL, a zero-dependency TypeScript client, a React SDK and a shared section vocabulary. The built-in renderer exists so a site can exist before a front end does, and so a draft has something to preview.",
  },
  {
    q: "Is the agent going to publish something embarrassing?",
    a: "Not without a person. Writes land as drafts, publishing is a separate explicit op, a destructive op needs a confirm token, a batch can be planned before it is applied, and every write carries attribution into a review queue. That is law P7, and it is on by default rather than configurable.",
  },
];

/* ── Pillars, for the nav and the resources rail ─────────────────────────── */

export interface Pillar {
  icon: LucideIcon;
  iconClass: string;
  title: string;
  description: string;
  anchor: string;
}

export const PILLARS: Pillar[] = [
  {
    icon: Workflow,
    iconClass: "from-[#d94328] to-[#ff6347]",
    title: "The agent gateway",
    description:
      "MCP over the same services the console uses, a manifest that answers first, and a context pack that ends the discovery session.",
    anchor: "/features/agent-gateway",
  },
  {
    icon: FolderTree,
    iconClass: "from-[#c2410c] to-[#f59e0b]",
    title: "Content as files",
    description:
      "The whole site projected to disk with a three-way merge behind it, so authoring is Edit and Write rather than a REST call per field.",
    anchor: "/features/content-as-files",
  },
  {
    icon: Layers,
    iconClass: "from-[#b45309] to-[#facc15]",
    title: "Blueprints",
    description:
      "Appliable units instead of prose recipes — post types, folders, content and carried files, converged in one transaction.",
    anchor: "/features/blueprints",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#9f1239] to-[#fb7185]",
    title: "Render and prove",
    description:
      "Pages, layouts, regions and a section vocabulary — with a digest, a lint and an audit that make a page checkable without a screenshot.",
    anchor: "/features/rendering",
  },
  {
    icon: Boxes,
    iconClass: "from-[#7c2d12] to-[#fb923c]",
    title: "Replication",
    description:
      "Point it at a site that already exists and get one a curator can edit — capture, convert, prove.",
    anchor: "/replicate",
  },
  {
    icon: Braces,
    iconClass: "from-[#831843] to-[#f472b6]",
    title: "Delivery",
    description:
      "REST and GraphQL, a zero-dependency TypeScript client, a React SDK and a generated Next.js starter.",
    anchor: "/features/delivery",
  },
];

/* ── /claude-code — the first five minutes ───────────────────────────────── */

export const PLUGIN_INSTALL = `claude plugin marketplace add openviglet/shio
claude plugin install shio

# then point it at your instance
export SHIO_URL=http://localhost:2710      # the default
export SHIO_USER=admin
export SHIO_PASSWORD=...`;

// Kept under ~54 columns: these render in a half-width figure that scrolls
// rather than wraps, and a line past the fold reads as truncated.
export const BRIDGE_INSTALL = `# No plugin, any MCP client — the CLI is
# the stdio bridge.
claude mcp add shio -- npx -y @viglet/shio mcp

# POST /mcp — JSON-RPC 2.0, revision 2025-06-18.
# GET /mcp is a deliberate 405: this server
# sends nothing unprompted.`;

export interface PluginPart {
  icon: LucideIcon;
  title: string;
  items: { name: string; gloss: string }[];
}

/** What `claude plugin install shio` actually adds. Mirrors `claude-plugin/`. */
export const PLUGIN_PARTS: PluginPart[] = [
  {
    icon: Workflow,
    title: "Skills",
    items: [
      {
        name: "shio-site",
        gloss: "Building and changing pages — addressing, apply, draft-then-publish, moves",
      },
      {
        name: "shio-content-model",
        gloss: "Post types and widgets as code — pull/push, the DSL, the traps",
      },
      {
        name: "shio-verify",
        gloss: "Checking work before handing it over — the lint's severities, digests, incidents",
      },
    ],
  },
  {
    icon: TerminalSquare,
    title: "Commands",
    items: [
      {
        name: "/shio-start [site]",
        gloss: "Reads the instance — model, sitemap, blueprints, lint state — and stops",
      },
      {
        name: "/shio-blueprint <name> <site>",
        gloss: "Plans a blueprint, asks, applies, reports the verification",
      },
      {
        name: "/shio-check [site]",
        gloss: "Verifies and reports errors, warnings and not-checked separately",
      },
    ],
  },
  {
    icon: FileStack,
    title: "Project instructions",
    items: [
      {
        name: "AGENTS.fragment.md",
        gloss:
          "Paste into your project's AGENTS.md so the conventions survive a session without the plugin",
      },
    ],
  },
];

/**
 * The rules the plugin encodes — the ones that are expensive to learn by making
 * the mistake. Kept in sync with `claude-plugin/README.md`.
 */
export const PLUGIN_RULES = [
  "Content is addressed by path, never by a UUID carried between calls.",
  "shio_apply before shio_write: a statement re-runs safely, a script does not.",
  "dryRun first, always. Read the plan — a delete line is a stop-and-ask.",
  "Writes land in DRAFT. Publishing is a separate, explicit step.",
];

export interface FirstCall {
  step: string;
  call: string;
  gets: string;
  cost: string;
}

/** What the first three calls of a session are, and what each one buys. */
export const FIRST_CALLS: FirstCall[] = [
  {
    step: "Can this instance do what I need?",
    call: "GET /agent/manifest?include=features,limits",
    gets: "Capabilities and limits. Unshipped ones read false, never absent; where a capability lives elsewhere, `elsewhere` names the command.",
    cost: "298 tokens",
  },
  {
    step: "What is here, and how do I write to it?",
    call: "GET /agent/context?include=all,manifest,ops",
    gets: "The content model, the sitemap, the conventions and every op with the keys it takes — plus a digest, so session N+1 can skip this.",
    cost: "one call",
  },
  {
    step: "Change something",
    call: "POST /agent/batch  { dryRun: true, ops: [...] }",
    gets: "The plan first: what each op would change. Then the same document without dryRun — and it lands as a draft.",
    cost: "reviewable",
  },
];

export interface TokenScope {
  scope: string;
  reads: string;
  writes: string;
  agent: boolean;
}

/**
 * `ShApiTokenScope` — the four scopes, verbatim in meaning. AGENT is the one
 * this page exists to explain: it is deliberately not a superset of WRITE.
 */
export const TOKEN_SCOPES: TokenScope[] = [
  { scope: "READ", reads: "published content", writes: "nothing", agent: false },
  { scope: "PREVIEW", reads: "published + drafts", writes: "nothing", agent: false },
  { scope: "WRITE", reads: "published + drafts", writes: "the delivery API, including published content", agent: false },
  {
    scope: "AGENT",
    reads: "published + drafts",
    writes: "the agent surface only — /api/v2/agent/** and /mcp",
    agent: true,
  },
];

/* ── Links ───────────────────────────────────────────────────────────────── */

export const LINKS = {
  docs: "https://docs.viglet.org/shio/",
  repo: "https://github.com/openviglet/shio",
  license: "https://github.com/openviglet/shio/blob/HEAD/LICENSE",
  roadmap: "https://github.com/openviglet/shio/blob/HEAD/docs/ROADMAP.md",
  changelog: "https://github.com/openviglet/shio/blob/HEAD/docs/CHANGELOG.md",
  laws: "https://github.com/openviglet/shio/blob/HEAD/agents.md",
  spec: "https://github.com/openviglet/shio/blob/HEAD/docs/specs/SH74-agent-native-cms.md",
  npm: "https://www.npmjs.com/package/@viglet/shio-client",
  cli: "https://www.npmjs.com/package/@viglet/shio",
  turing: "https://turing.viglet.org",
  viglet: "https://www.viglet.org",
  mcpSpec: "https://modelcontextprotocol.io",
} as const;

export interface ResourceCard {
  icon: LucideIcon;
  title: string;
  description: string;
  href: string;
}

export const RESOURCES: ResourceCard[] = [
  {
    icon: ScrollText,
    title: "Documentation",
    description: "Install, model, author, deliver — the end-user documentation set.",
    href: LINKS.docs,
  },
  {
    icon: TerminalSquare,
    title: "The CLI on npm",
    description: "@viglet/shio — init, pull, push, verify, clone, and the MCP bridge.",
    href: LINKS.cli,
  },
  {
    icon: FileStack,
    title: "The concept spec",
    description:
      "SH74 — the agent-native CMS: the protocol design, the tool surface and a worked example.",
    href: LINKS.spec,
  },
  {
    icon: GitCompareArrows,
    title: "Roadmap and changelog",
    description: "The open backlog and the shipped ledger, both in the repository.",
    href: LINKS.roadmap,
  },
];
