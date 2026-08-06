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
  },
  {
    call: "Give me the site on disk",
    tool: "shio pull --content",
    result: "shio/content/**  ·  42 posts, 9 folders, front-mattered Markdown",
    law: "P4",
  },
  {
    call: "Write the launch page",
    tool: "Edit  shio/content/acme/launch.md",
    result: "one file, one post — no API call to author with",
    law: "P4",
  },
  {
    call: "Push it back",
    tool: "shio push --content",
    result: "1 created  ·  status DRAFT  ·  nothing is live yet",
    law: "P7",
  },
  {
    call: "Prove it before a human looks",
    tool: "shio verify --delivery",
    result: "ok — 0 errors, 1 warning: hero image has no alt text",
    law: "P9",
  },
  {
    call: "Hand it over",
    tool: "shio report --since last",
    result: "a Markdown summary with a preview link per change, and the warning as an open question",
    law: "P7",
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

/* ── P2 / P3: what a call costs ──────────────────────────────────────────── */

export interface Measure {
  label: string;
  tokens: number;
  note: string;
}

/**
 * Real figures from the agent surface's own budget tests
 * (`ShAgentResponseBudgetTest`, `ShMcpToolBudgetTest`) and from the token-economy
 * page in the end-user docs. SH533 replaces this hand-copied list with the
 * benchmark's own JSON artefact, which is the only version that cannot drift.
 */
export const MEASURES: Measure[] = [
  {
    label: "Capability check",
    tokens: 298,
    note: "manifest?include=features,limits — what a session opens with",
  },
  {
    label: "Full manifest",
    tokens: 2343,
    note: "every capability, every limit, a curated endpoint index",
  },
  {
    label: "Write vocabulary",
    tokens: 447,
    note: "context?include=ops — every op and the keys it takes",
  },
  {
    label: "Build a site, end to end",
    tokens: 2700,
    note: "4 calls, measured to the verify — not to a page a person opened",
  },
];

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
    anchor: "#surface",
  },
  {
    icon: FolderTree,
    iconClass: "from-[#c2410c] to-[#f59e0b]",
    title: "Content as files",
    description:
      "The whole site projected to disk with a three-way merge behind it, so authoring is Edit and Write rather than a REST call per field.",
    anchor: "#files",
  },
  {
    icon: Layers,
    iconClass: "from-[#b45309] to-[#facc15]",
    title: "Blueprints",
    description:
      "Appliable units instead of prose recipes — post types, folders, content and carried files, converged in one transaction.",
    anchor: "#blueprints",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#9f1239] to-[#fb7185]",
    title: "Render and prove",
    description:
      "Pages, layouts, regions and a section vocabulary — with a digest, a lint and an audit that make a page checkable without a screenshot.",
    anchor: "#verify",
  },
  {
    icon: Boxes,
    iconClass: "from-[#7c2d12] to-[#fb923c]",
    title: "Replication",
    description:
      "Point it at a site that already exists and get one a curator can edit — capture, convert, prove.",
    anchor: "#replicate",
  },
  {
    icon: Braces,
    iconClass: "from-[#831843] to-[#f472b6]",
    title: "Delivery",
    description:
      "REST and GraphQL, a zero-dependency TypeScript client, a React SDK and a generated Next.js starter.",
    anchor: "#delivery",
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
