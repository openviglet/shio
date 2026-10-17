import {
  Boxes,
  Bot,
  Braces,
  Building2,
  ClipboardCheck,
  Code2,
  Eye,
  FileCode2,
  FileStack,
  FolderTree,
  Gauge,
  GitCompareArrows,
  Layers,
  Lock,
  Megaphone,
  MousePointerClick,
  Radar,
  RefreshCw,
  Route,
  ScrollText,
  ShieldCheck,
  Sparkles,
  TerminalSquare,
  Undo2,
  UsersRound,
  Workflow,
  type LucideIcon,
} from "lucide-react";
import { ceiling } from "./token-budgets";

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * All page copy lives here; `src/components/sections/` only renders it.
 *
 * Everything on this page is a claim about a real surface, so the rule for
 * editing this file is the rule the product itself is built on: if a number,
 * a route, a tool name or an error body appears below, it is the one the build
 * produces. `docs/agents/` is the authority for each; where a figure came from
 * a measurement, the test that measures it is named in a comment.
 *
 * Two things run over what you write here (SH925). `scripts/ai-writing.test.mjs`
 * is the AI-writing checklist as a lint: the puffery vocabulary, and the counts
 * that can be read back from the surface they describe. And the `ai-writing`
 * skill is the pass somebody still has to make by reading: the tells worth
 * catching are density and inflation, which no grep can see.
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
      "Every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete. The order is about what may not be skipped on the way to shipping, never about who is allowed to operate the CMS.",
    anchor: "#paths",
  },
  {
    id: "P2",
    title: "One call replaces a session",
    gloss:
      "Discovery is a product feature, not a documentation problem: a manifest and a context pack.",
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
      "Every 4xx carries fix, allowed, didYouMean and example, so the retry is the next call, not the next session.",
    anchor: "#errors",
  },
  {
    id: "P7",
    title: "Never surprise the curator",
    gloss:
      "Draft-default, dry-run, explicit publish, confirm tokens, attribution, a review queue.",
    anchor: "#curator",
  },
  {
    id: "P8",
    title: "Skipping steps needs appliable units",
    gloss: "Blueprints, a package that applies, instead of a recipe written in prose.",
    anchor: "#blueprints",
  },
  {
    id: "P9",
    title: "Close the perception loop",
    gloss:
      "Cheap textual proof (a lint, the routes, a render digest) instead of asking someone to look.",
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
 * A session that builds one page, start to finish. Scripted, and the page says
 * so, because a recording dressed as a live connection is exactly the thing
 * this product's design laws refuse. SH531 turns it into a stepped player and
 * SH540 points it at a public instance.
 */
export const TRANSCRIPT: TranscriptStep[] = [
  {
    call: "What is this instance, and what can I do to it?",
    tool: "shio_context",
    result: "model + sitemap + conventions + write vocabulary: 1 call, 1 digest to skip it next time",
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
    result: "one file, one post, and no API call to author with",
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
        "+An open-source, agent-ready CMS with four ways in.",
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
    call: "Prove it before a curator looks",
    tool: "shio verify --delivery",
    result: "ok. 0 errors, 1 warning: hero image has no alt text",
    law: "P9",
    artefact: {
      label: "the proof, before a curator looks",
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

/* ── The same page, built by hand ────────────────────────────────────────── */

export interface ConsoleStep {
  /** What the person does, in the words the console itself uses. */
  action: string;
  /** Where in the console it happens. */
  screen: string;
  /** One line of what they get back. */
  result: string;
  /** What the screen shows afterwards. */
  artefact: { label: string; lines: string[] };
}

/**
 * The counterpart to `TRANSCRIPT`, and the reason both are on the page: they
 * end at the same draft. The site used to open on the agent session alone,
 * which reads as a CMS you cannot use without one: an impression the product
 * does not deserve and the console disproves in six clicks.
 *
 * Every screen named here is a route in the console (`shio-react/src/app/`),
 * under the same rule as the rest of this file: a claim is about a real surface.
 */
export const CONSOLE_SESSION: ConsoleStep[] = [
  {
    action: "Create the site",
    screen: "Content › Sites › New",
    result: "a site, its root folder and its home page. Nothing else required",
    artefact: {
      label: "Content › Sites",
      lines: [
        "acme                    just now",
        "  ├─ (root folder)",
        "  └─ Home               DRAFT",
        "",
        "or: Import a .zip export of a site you already have",
      ],
    },
  },
  {
    action: "Shape the model, or keep the one that shipped",
    screen: "Content › Post types",
    result: "fields, each drawn by one of fifteen widgets. No schema file to write",
    artefact: {
      label: "Post type › Page",
      lines: [
        "title          Text          required  ·  Title",
        "summary        Text Area               ·  Summary",
        "content        HTML Editor",
        "hero           File",
        "related        Relator",
      ],
    },
  },
  {
    action: "Write the launch page",
    screen: "Content › acme › New › Page",
    result: "a form built from the type: the same fields, drawn from the same model",
    artefact: {
      label: "New Page",
      lines: [
        "Title    │ Launching Shio 2026.3",
        "Summary  │ What changed, and why.",
        "Content  │ [ rich text ]",
        "URL      │ /launch",
      ],
    },
  },
  {
    action: "Save it",
    screen: "the sticky save bar",
    result: "status DRAFT. The live site is unchanged, exactly as for an agent write",
    artefact: {
      label: "what the instance now holds",
      lines: [
        "post:acme/launch        DRAFT",
        "",
        "0 published. The live site is unchanged.",
      ],
    },
  },
  {
    action: "Look at it, and fix the headline where you can see it",
    screen: "Preview  ·  Universal Editor",
    result: "the real rendered page, and the field edited on the page itself",
    artefact: {
      label: "/preview/acme/launch",
      lines: [
        "▸ the rendered page, from an expiring,",
        "  single-post token",
        "",
        "▸ click the headline, type, save:",
        "  it is the draft's title field",
      ],
    },
  },
  {
    action: "Publish it, or schedule it",
    screen: "Publish",
    result: "an immutable snapshot beside the draft, or a date and a sweep that fires it",
    artefact: {
      label: "post:acme/launch",
      lines: [
        "PUBLISHED   2026-03-01 09:30",
        "DRAFT       editable, diffable against it",
        "",
        "Activity: published by you, just now",
      ],
    },
  },
];

/* ── The four ways in ────────────────────────────────────────────────────── */

export interface UsePath {
  id: string;
  icon: LucideIcon;
  label: string;
  /** The short form, for the tags on a use case. */
  tag: string;
  /** Who arrives on this path. */
  audience: string;
  /** What it takes to start: the honest prerequisite. */
  needs: string;
  body: string;
  surface: string[];
  start: { label: string; href: string };
}

/**
 * The page's opening argument, and the correction that made this file worth
 * rewriting: agent-first is the order capabilities are *built* in (law P1), not
 * a condition of using the product. A visitor who wants an ordinary CMS is not
 * in the wrong place, and a page that implies otherwise loses them in one
 * paragraph.
 *
 * These are four doors onto one content engine, not tiers and not a migration
 * path. The same posts, the same drafts, the same publish act.
 */
export const PATHS: UsePath[] = [
  {
    id: "console",
    icon: MousePointerClick,
    label: "By hand, in the console",
    tag: "console",
    audience: "editors, marketers, anyone who has used a CMS before",
    needs: "a browser and a login",
    body:
      "Create the site, shape the content model, write the pages, upload the media, publish or schedule it, and add the rest of the team. All in a browser, with no agent, no terminal and no file to hand-edit anywhere in it.",
    surface: ["React console", "Universal Editor", "Preview links"],
    start: { label: "Shio without an agent", href: "/console/" },
  },
  {
    id: "agent",
    icon: Bot,
    label: "With a coding agent",
    tag: "agent",
    audience: "anyone who already works with Claude Code or another MCP client",
    needs: "one command to attach it",
    body:
      "Discovery in one call, content authored as files, blueprints applied as units, a render digest instead of a screenshot. The agent writes drafts and cannot publish them, which is what makes handing it write access reasonable.",
    surface: ["MCP", "shio CLI", "/api/v2/agent"],
    start: { label: "Drive it from Claude Code", href: "/claude-code/" },
  },
  {
    id: "files",
    icon: FolderTree,
    label: "As files, in your repository",
    tag: "files",
    audience: "teams who would rather review content in a pull request",
    needs: "the CLI and a repo",
    body:
      "Pull the whole site to disk as front-mattered Markdown, edit it with the editor you already use, push it back through a three-way merge. Put shio apply --check in CI so drift fails a build instead of surprising somebody.",
    surface: ["shio pull / push", "shio apply --check", "Git"],
    start: { label: "How the projection works", href: "/features/content-as-files/" },
  },
  {
    id: "api",
    icon: Braces,
    label: "Through the delivery API",
    tag: "API",
    audience: "front-end and platform engineers",
    needs: "a token",
    body:
      "Headless in the ordinary sense: REST and GraphQL, a TypeScript client with zero runtime dependencies, a React SDK, a generated Next.js starter, and a bridge that makes your own front end inline-editable.",
    surface: ["REST", "GraphQL", "@viglet/shio-client"],
    start: { label: "Delivery and the SDKs", href: "/features/delivery/" },
  },
];

/* ── Who actually uses it, and how ───────────────────────────────────────── */

export interface UseCase {
  icon: LucideIcon;
  title: string;
  who: string;
  body: string;
  /** Ids from PATHS: which doors this scenario actually walks through. */
  paths: string[];
}

/**
 * Six shapes a real deployment takes, ordered so the first one uses no agent at
 * all. That ordering is the point: it is the case the product supports fully
 * and the one the old version of this page accidentally denied.
 */
export const USE_CASES: UseCase[] = [
  {
    icon: Megaphone,
    title: "A team that just wants a CMS",
    who: "a marketing or comms team, no agent anywhere in sight",
    body:
      "Someone creates the site, defines the types, and the team writes pages in a form and publishes them. Everything an editor touches is the console. The agent surface sits there unused, costing nothing, and the day somebody wants it, no content has to move.",
    paths: ["console"],
  },
  {
    icon: Code2,
    title: "A front end you wrote, content someone else owns",
    who: "an engineering team with a Next.js app and non-technical editors",
    body:
      "The delivery API feeds your application and the console is where the copy lives. Add the editor bridge and your own pages become inline-editable, so a reviewer fixes a headline on the real page rather than hunting for a field in a form.",
    paths: ["api", "console"],
  },
  {
    icon: Bot,
    title: "An agent does the first build; people keep it",
    who: "a small team with a large site to stand up",
    body:
      "The agent applies a blueprint, writes forty pages and proves them against the lint, all as drafts. Then it stops being interesting: the people who maintain the site work in the console, and the agent comes back only for the next bulk job.",
    paths: ["agent", "console"],
  },
  {
    icon: GitCompareArrows,
    title: "Content reviewed like code",
    who: "a docs or product team already living in pull requests",
    body:
      "The site projects to Markdown in your repository, changes arrive as a diff a reviewer can read, and CI runs the drift check and the content lint before a merge. Publishing stays a separate, deliberate act on the instance.",
    paths: ["files", "console"],
  },
  {
    icon: RefreshCw,
    title: "An old site that has to become an editable one",
    who: "an agency, or whoever inherited the site nobody can change",
    body:
      "Capture the existing site under robots.txt, review what the crawl proposes, convert it into drafts, and prove by arithmetic that nothing was lost. What you get at the end is content in a model, not a screenshot with a CMS behind it.",
    paths: ["agent", "console"],
  },
  {
    icon: Building2,
    title: "Many sites, one instance",
    who: "a platform team serving several brands or clients",
    body:
      "Tenant isolation reaches every surface, including the agent's. Scoped API tokens, roles and groups, webhooks on publish, and an activity log that says who changed what, whether the who was a person or a credential you issued.",
    paths: ["console", "api", "agent"],
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

/**
 * Three seats, and the sentence that has to travel with them: **any of them can
 * be empty, and one person can hold all three.** The person's seat is first
 * because it is the only one that is never optional: an instance with no agent
 * is an ordinary CMS, and an instance with no person is not a product anyone
 * should ship.
 */
export const ROLES: Role[] = [
  {
    icon: UsersRound,
    actor: "The person",
    verb: "creates, and decides",
    description:
      "Sites, content models, folders, pages, media, publishing, scheduling, the rest of the team: all of it in the console, and none of it needing an agent to have happened first. When there is an agent, this seat also holds the approve button.",
    surface: ["React console", "Universal Editor", "Preview links"],
  },
  {
    icon: Bot,
    actor: "The agent",
    verb: "builds, if you want one",
    description:
      "Discovers the model in one call, authors content as files, applies a blueprint, renders a preview and proves the result, without a browser, a cookie or a click. Its writes are drafts, and its credential cannot publish them.",
    surface: ["MCP", "shio CLI", "/api/v2/agent"],
  },
  {
    icon: Route,
    actor: "The delivery API",
    verb: "delivers",
    description:
      "A framework-agnostic delivery API (REST and GraphQL) with TypeScript SDKs, a React SDK and a Next.js starter that is generated rather than copied. It serves whatever the other two seats agreed on.",
    surface: ["REST", "GraphQL", "@viglet/shio-client"],
  },
];

/* ── P2 / P3: what a call costs ────────────────────────────── */

/**
 * SH533: generated, never typed. `scripts/token-budgets.mjs` reads the build's
 * own `token-budgets.properties` (the single file the Java suite asserts every
 * response against) and writes the JSON imported below, on every build. A
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
  { name: "shio_write", gloss: "a batch of ops: upsert, move, delete" },
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
  { verb: "shio audit", gloss: "the browser as an instrument: overflow, contrast, dead requests" },
  { verb: "shio snapshot", gloss: "a PNG, for whoever has to look" },
  { verb: "shio clone", gloss: "capture an existing site, writing no content" },
  { verb: "shio report", gloss: "the handoff: what changed, with a preview link each" },
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

An open-source, agent-ready CMS with four ways in: the console,
an agent, files in a repository, and the delivery API.`;

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
      "A write lands as a draft. Publishing is a separate, explicit act with its own op. No flag quietly does both.",
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
      "A destructive op is refused once with a token, and accepted only when the token comes back, so no single call deletes anything.",
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
      "One page's structure (regions, sections, what each resolved to), cheap enough to read after every edit instead of asking somebody to look.",
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
    title: "A picture, for a person",
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
      "walks one origin under robots.txt and writes an inventory (pages, assets, locales, forms, navigation, third-party scripts) and no content at all.",
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
      "Framework-agnostic TypeScript client for the delivery API. Zero runtime dependencies: Node, browser, edge, server components.",
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
      "The page-section vocabulary: one shared language for the agent, the console and the front end.",
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
      "The Universal Editor bridge: inline editing on any front end that emits the data attributes.",
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
 * The axis is agent-operability, not features: every headless CMS has content
 * types and a delivery API. SH534 turns this teaser into a page that also names
 * where each alternative wins, which is what makes the rest credible.
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

/* ── /curator: the curator's half ────────────────────────────────────────────── */

export interface CuratorMoment {
  when: string;
  title: string;
  body: string;
}

/**
 * What a curator's Tuesday actually looks like: deliberately a narrative and
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
    body: "A preview link opens the real rendered page, from a token that expires and is scoped to that one post, not a staging environment somebody has to keep in sync.",
  },
  {
    when: "09:22",
    title: "You fix the headline where you can see it",
    body: "Inline, on the rendered page itself, through the visual editor, rather than hunting for the field in a form that looks nothing like the result.",
  },
  {
    when: "09:30",
    title: "You publish, or you schedule it",
    body: "Publishing is its own act. Or set a date and let the sweep fire it. It is the same path, so anything listening for a publish hears the identical event.",
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
      "Any batch can be sent as a dry run. What comes back is that batch annotated with what each op would change, not a summary of it.",
    because:
      "A preview in a different shape from the thing being previewed is a second implementation, and the two drift.",
  },
  {
    icon: ClipboardCheck,
    title: "The agent cannot approve its own work",
    mechanism:
      "The review queue is console-authenticated and deliberately not on the agent namespace at all. An agent token cannot reach the approve call.",
    because:
      "A credential that can both make a change and sign it off turns the review step into a log line.",
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
    gloss: "An immutable snapshot beside the draft, which is what makes diff and restore cheap.",
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

/* ── /console: the CMS, with nothing attached to it ──────────────────────── */

export interface CapabilityGroup {
  icon: LucideIcon;
  title: string;
  items: string[];
}

/**
 * What the console does on its own. Each line is a screen in
 * `shio-react/src/app/` (content, admin, editor); this list is a walk of those directories rather
 * than a wish, because the failure it prevents is the one this page exists to
 * correct: a claim about the curator's half that nobody checked against the
 * curator's half.
 */
export const CONSOLE_CAPABILITIES: CapabilityGroup[] = [
  {
    icon: FolderTree,
    title: "Content",
    items: [
      "Sites, created from scratch or imported from a .zip export",
      "Folders, and a tree you move and copy things around in",
      "Posts of any type, with search across the instance",
      "A trash that restores: deleting and purging are separate acts",
    ],
  },
  {
    icon: Layers,
    title: "The content model",
    items: [
      "Post types built in the browser: add a field, pick a widget, order them",
      "Fifteen widgets: text, rich text, date, select, file, relator, tabs, code",
      "Relations between posts, and a content browser to pick them",
      "The types the instance ships with, if you would rather not start empty",
    ],
  },
  {
    icon: Eye,
    title: "Publishing",
    items: [
      "Draft, preview, publish, schedule, unpublish",
      "Preview links that open the real rendered page from an expiring token",
      "Inline editing on that page through the Universal Editor",
      "A review queue and an activity log: who changed what, and when",
    ],
  },
  {
    icon: FileStack,
    title: "Media and files",
    items: [
      "A media library for the binaries a page references",
      "Static files served beside the site",
      "The file widget, so a field is a picker rather than a URL to paste",
    ],
  },
  {
    icon: UsersRound,
    title: "People and access",
    items: [
      "Users, groups and roles",
      "External authentication providers, and email configuration",
      "API tokens, scoped per job: read, preview, write, agent",
      "Tenants, when one instance serves several brands or clients",
    ],
  },
  {
    icon: Braces,
    title: "Delivery and integration",
    items: [
      "A GraphQL explorer against your own content",
      "Webhooks per site, so a publish can tell your build about it",
      "The package marketplace, with the dry run shown before anything installs",
    ],
  },
];

/**
 * And the honest other half. Naming these is what makes the list above worth
 * reading: four capabilities live only on the command line, because they
 * need a shell or a browser the instance deliberately does not run.
 */
export const CONSOLE_LIMITS = [
  {
    title: "Capturing a site that already exists",
    body: "shio clone walks an origin under robots.txt and writes an inventory. It needs a crawler, and the instance does not run one.",
  },
  {
    title: "Applying a blueprint to a site",
    body: "The console browses and installs packages, with the dry run shown first. Applying one to a site is shio apply --blueprint, or shio_apply over MCP.",
  },
  {
    title: "Screenshots and the browser audit",
    body: "shio audit and shio snapshot drive a real browser. That stays in the CLI, where the shell already is. An instance that starts a browser per request is a second service to operate, and nothing else about the CMS needs one.",
  },
  {
    title: "The file projection and the CI drift check",
    body: "Pulling the site to disk and failing a build on drift is what the CLI is for. Nothing about it is required to run the CMS.",
  },
];

/** What actually changes on the day someone attaches an agent to an instance in use. */
export const AGENT_LATER = [
  {
    title: "Nothing moves",
    body: "The agent reads the same posts, the same types and the same folders the console writes. There is no second store, no export step and no parallel model. Law P10 forbids a third contract, and this is what that buys.",
  },
  {
    title: "Its writes look like drafts, because they are",
    body: "An agent write lands in DRAFT with attribution on it. Whether a credential may publish at all is a property of the token, so the console's publish button stays the only way anything reaches a reader.",
  },
  {
    title: "You get a queue, not a surprise",
    body: "Changes arrive in the review queue with a preview link each and the verify warnings restated as open questions. The queue is console-authenticated, and an agent token cannot reach the approve call.",
  },
  {
    title: "And you can turn it off",
    body: "Do not issue an AGENT token, and nothing on that surface can write. The capability is additive in the literal sense: the CMS you were running is the CMS you keep.",
  },
];

/* ── /replicate: capture, propose, convert, prove, accept ───────────────── */

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
      "It applies nothing: no post, no post type, no layout. Review and mutation in one command is the surprise law P7 forbids.",
  },
  {
    verb: "convert",
    title: "Convert",
    command: "shio convert --site example --dry-run",
    what: "Turns the accepted plan into the desired-state document that `shio apply` already takes, and sends it down that same path. One post per candidate, not per page: the hero that recurs on twelve pages is one section reused.",
    promise:
      "Every post lands in DRAFT, and `publish` is absent rather than false, so re-running can never unpublish a page a curator has since published.",
  },
  {
    verb: "diff",
    title: "Prove",
    command: "shio diff --against capture --site example",
    what: "Walks the capture's pages, asks the instance for each one, and reads the served bytes with the same scanner that wrote the declared counts. Internal links, external links, assets, forms and prose. Arithmetic, not a second opinion.",
    promise:
      "A fact the replica gained is never a failure; a lost one exits 1. Every artefact carries the identity of what declared it, so nothing is guessed or re-fetched.",
  },
  {
    verb: "judge",
    title: "Accept",
    command: "shio judge --site example",
    what: "Runs the instruments, answers once, and names where two of them disagreed, which is where every real finding of this work came from. Then it holds that answer to a written bar: a finding at or below it prints as a task for later, one above it fails, and a kind the bar does not name fails too.",
    promise:
      "A round can end ACCEPTED, which is not the same as ending empty. What the bar leaves out is the point: looking like the source is not a test an authorable replica has to pass. It is presented by Shio's theme on purpose, so it answers for its content, its structure and its routes. `--fidelity` is how you ask for the pixel comparison instead.",
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
    keeps: "The source's appearance, exactly: the block's markup is written verbatim into a layout.",
    offers: "No fields. A curator editing “the hero headline” is editing an HTML file.",
    pick: "the page has to keep looking like it looks, and nobody is going to edit it.",
    accent: "fidelity",
  },
  {
    name: "Annotated fidelity",
    keeps: "The source's own markup, byte for byte: strip the annotations back out and they are identical.",
    offers: "Editable fields on that markup. `convert --annotate` stamps them onto the nodes the proposal already derived, so a curator edits a heading in place.",
    pick: "the page has to keep looking exactly like it looks, and somebody still has to edit it.",
    accent: "fidelity",
  },
  {
    name: "Authorable",
    keeps: "The content, as structured fields a curator edits in the console or on the page itself.",
    offers: "The site's own theme, in the source's own colours, type and corner radius. The structure is replaced deliberately: not ruined, and not judged against the source's own pixels.",
    pick: "somebody is going to maintain this site, and the model matters more than the pixels.",
    accent: "authorable",
  },
];

/**
 * What a page made of both looks like, which is the ordinary outcome of a per-block
 * choice and had no copy on this page at all (SH775).
 *
 * The modes above are honest about a **block**. A *page* has three shapes, and the third
 * is the common one: accept the hero and leave the footer, and the page is mixed. Until
 * SH763 that page's kept markup sat in a field a curator could not reach from the editor,
 * which made "authorable" quietly conditional on accepting everything.
 */
export const MIXED_PAGE = {
  title: "A page is usually both, and that is the point",
  body:
    "The choice is per block, so most pages end up mixed: the hero accepted, the "
    + "source's header and footer kept. The kept markup is not a leftover: each run of it "
    + "is a section in the page's own list, in the place it stood, so a curator can move "
    + "it, replace it with a real section, or delete it, from the same control they "
    + "compose the rest of the page with. That is how a replica becomes authorable one "
    + "block at a time instead of all at once, and the page keeps the source's stylesheet "
    + "for as long as any of its markup is still there.",
};

/** The bounds, which are the reason the capture is credible rather than a caveat. */
export const CLONE_BOUNDS = [
  {
    title: "One origin, and no further",
    body: "Every off-host link is recorded and never followed. A seed that redirects off-host is refused, naming the host it actually served, because example.com → www.example.com followed silently files every path under an origin nobody named.",
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
    body: "Mirroring a CDN's bytes into somebody's media library is a licensing decision this tool is not entitled to make, but somebody has to be able to, so the hosts left behind are listed rather than dropped.",
  },
  {
    title: "Deterministic, so two runs can be diffed",
    body: "Sequential over a sorted frontier, and no timestamp in the file: --max-pages 50 cuts the same fifty pages every run. The inventory exists to be compared with the next crawl, and a set that moves on its own makes every diff noise.",
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

/* ── /compare: the matrix, and where it loses ────────────────────────────── */

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
 * *documents the property as a guarantee*, not whether some path exists: an
 * export command is not a file projection with a three-way merge, and an MCP
 * server over an API designed for a browser is not a protocol designed for an
 * agent. Where a product's answer is "you can build it", the cell says so.
 * Verify against current vendor documentation before deciding; this reflects
 * 2026.3.
 */
export const COMPARE_GROUPS: CompareGroup[] = [
  {
    law: "P2",
    title: "Discovery: what does the agent's first call cost?",
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
        // SH601: the finding behind this row is that the manifest was already complete, and
        // completeness was never the constraint: mapping an intent onto it was the unbudgeted work.
        feature: "The manifest routes an intent to the one call, instead of listing everything",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
  {
    law: "P4",
    title: "Authoring: can the agent use its cheapest tools?",
    rows: [
      {
        feature: "The whole site projected to files, with a three-way merge back",
        cells: ["yes", "export only", "export only", "no", "no"],
      },
      {
        feature: "Path addressing, with no UUID carried between calls",
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
    title: "Failure: how expensive is a wrong call?",
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
    title: "Safety: what stops a wrong write?",
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
    title: "Proof: can the agent check its own work?",
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
    title: "Economy: is the protocol's cost a number?",
    rows: [
      {
        feature: "Response token budgets with a ceiling the build enforces",
        cells: ["yes", "no", "no", "no", "no"],
      },
    ],
  },
  {
    law: "none",
    title: "Operations: whose infrastructure, whose source?",
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
 * Where each alternative wins. Viglet Turing's compare page earns its
 * credibility this way and this one has to do the same, or the matrix above is
 * worth nothing: a table where one column is all ticks is a table nobody reads
 * twice.
 */
export const RIVALS: Rival[] = [
  {
    name: "Contentful",
    wins: "The enterprise ecosystem: integrations, SLAs, a global CDN you do not operate, and an editor a marketing team already knows how to use.",
    chooseIt:
      "your buyer is procurement, the content team is large and non-technical, and nobody on staff wants to run a JVM.",
  },
  {
    name: "Sanity",
    wins: "Structured-content editing: real-time collaboration, its own query language, and a studio you shape to the model.",
    chooseIt:
      "editors are the primary operator and the depth of the editing experience is the thing you are buying.",
  },
  {
    name: "Strapi",
    wins: "The closest neighbour: open source, self-hosted, Node all the way down, and a plugin ecosystem that fits a JavaScript team's existing habits.",
    chooseIt:
      "your team is JS end to end, you want self-hosting, and you do not need an agent protocol. It is a shorter path to the same content API.",
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
    q: "Do I have to use an agent?",
    a: "No, and nothing is withheld if you never attach one. A person creates the site, defines the content model, writes and publishes the pages, uploads the media and manages the team. All of it in a React console, with a visual editor that edits the rendered page. Agent-first is the order a capability is *built* in (law P1): MCP, then files and the CLI, then REST, then the console. It is a rule about what may not be skipped on the way to shipping something, not a condition on who is allowed to operate it.",
  },
  {
    q: "Everyone has an MCP server now. Isn't that the same thing?",
    a: "An MCP server is a transport. Put one over an API designed for a browser and the agent gets that API's protocol quality: the same UUID plumbing, the same 400 with no instruction, the same discovery session. The difference this page is about is what the protocol guarantees underneath, which is why every row above is a property you can test rather than a feature you can be told about.",
  },
  {
    q: "Our editors will never open a terminal. Is the console a second-class citizen?",
    a: "It is the surface the product is judged on by everyone who is not an agent, and it carries the whole ordinary job: sites, folders, post types with fifteen field widgets, a media library, search, a trash that restores, publishing and scheduling, users, groups, roles and tokens. Four things live only on the command line (capturing an existing site, applying a blueprint, the browser audit and the file projection), and each is named on the console page rather than left for you to discover.",
  },
  {
    q: "Java and Spring, in 2026?",
    a: "One JVM service in a container, with the content engine, the agent gateway, the MCP server and the renderer in the same process. Rewriting it in Node is a standing non-goal: it is high cost, high risk and zero user-visible value. What you interact with is HTTP, a CLI and files.",
  },
  {
    q: "What happens to my front end?",
    a: "Nothing: it is headless. REST and GraphQL, a zero-dependency TypeScript client, a React SDK and a shared section vocabulary. The built-in renderer exists so a site can exist before a front end does, and so a draft has something to preview.",
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
    iconClass: "from-[#5b21b6] to-[#7c3aed]",
    title: "The agent gateway",
    description:
      "MCP over the same services the console uses, a manifest that answers first, and a context pack that ends the discovery session.",
    anchor: "/features/agent-gateway/",
  },
  {
    icon: FolderTree,
    iconClass: "from-[#c2410c] to-[#f59e0b]",
    title: "Content as files",
    description:
      "The whole site projected to disk with a three-way merge behind it, so authoring is Edit and Write rather than a REST call per field.",
    anchor: "/features/content-as-files/",
  },
  {
    icon: Layers,
    iconClass: "from-[#b45309] to-[#facc15]",
    title: "Blueprints",
    description:
      "Appliable units instead of prose recipes: post types, folders, content and carried files, converged in one transaction.",
    anchor: "/features/blueprints/",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#9f1239] to-[#fb7185]",
    title: "Render and prove",
    description:
      "Pages, layouts, regions and a section vocabulary, with a digest, a lint and an audit that make a page checkable without a screenshot.",
    anchor: "/features/rendering/",
  },
  {
    icon: Boxes,
    iconClass: "from-[#7c2d12] to-[#fb923c]",
    title: "Replication",
    description:
      "Point it at a site that already exists and get one a curator can edit: capture, convert, prove, and a verdict that says when to stop.",
    anchor: "/replicate/",
  },
  {
    icon: Braces,
    iconClass: "from-[#831843] to-[#f472b6]",
    title: "Delivery",
    description:
      "REST and GraphQL, a zero-dependency TypeScript client, a React SDK and a generated Next.js starter.",
    anchor: "/features/delivery/",
  },
];

/* ── /claude-code: the first five minutes ────────────────────────────────── */

export const PLUGIN_INSTALL = `claude plugin marketplace add openviglet/shio
claude plugin install shio

# then point it at your instance
export SHIO_URL=http://localhost:2710      # the default
export SHIO_USER=admin
export SHIO_PASSWORD=...`;

// Kept under ~54 columns: these render in a half-width figure that scrolls
// rather than wraps, and a line past the fold reads as truncated.
export const BRIDGE_INSTALL = `# No plugin, any MCP client: the CLI is
# the stdio bridge.
claude mcp add shio -- npx -y @viglet/shio mcp

# POST /mcp speaks JSON-RPC 2.0, rev 2025-06-18.
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
        gloss: "Building and changing pages: addressing, apply, draft-then-publish, moves",
      },
      {
        name: "shio-content-model",
        gloss: "Post types and widgets as code: pull/push, the DSL, the traps",
      },
      {
        name: "shio-verify",
        gloss: "Checking work before handing it over: the lint's severities, digests, incidents",
      },
    ],
  },
  {
    icon: TerminalSquare,
    title: "Commands",
    items: [
      {
        name: "/shio-start [site]",
        gloss: "Reads the instance (model, sitemap, blueprints, lint state) and stops",
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
 * The rules the plugin encodes: the ones that are expensive to learn by making
 * the mistake. Kept in sync with `claude-plugin/README.md`.
 */
export const PLUGIN_RULES = [
  "Content is addressed by path, never by a UUID carried between calls.",
  "shio_apply before shio_write: a statement re-runs safely, a script does not.",
  "dryRun first, always. Read the plan: a delete line is a stop-and-ask.",
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
    cost: `≤ ${ceiling("response.manifest.check")} tokens`,
  },
  {
    step: "What is here, and how do I write to it?",
    call: "GET /agent/context?include=all,manifest,ops",
    gets: "The content model, the sitemap, the conventions and every op with the keys it takes, plus a digest, so session N+1 can skip this.",
    cost: "one call",
  },
  {
    step: "Change something",
    call: "POST /agent/batch  { dryRun: true, ops: [...] }",
    gets: "The plan first: what each op would change. Then the same document without dryRun, and it lands as a draft.",
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
 * `ShApiTokenScope`: the four scopes, verbatim in meaning. AGENT is the one
 * this page exists to explain: it is deliberately not a superset of WRITE.
 */
export const TOKEN_SCOPES: TokenScope[] = [
  { scope: "READ", reads: "published content", writes: "nothing", agent: false },
  { scope: "PREVIEW", reads: "published + drafts", writes: "nothing", agent: false },
  { scope: "WRITE", reads: "published + drafts", writes: "the delivery API, including published content", agent: false },
  {
    scope: "AGENT",
    reads: "published + drafts",
    writes: "the agent surface only: /api/v2/agent/** and /mcp",
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
    description: "Install, model, author, deliver: the end-user documentation set.",
    href: LINKS.docs,
  },
  {
    icon: TerminalSquare,
    title: "The CLI on npm",
    description: "@viglet/shio: init, pull, push, verify, clone, and the MCP bridge.",
    href: LINKS.cli,
  },
  {
    icon: FileStack,
    title: "The concept spec",
    description:
      "SH74, the agent-native CMS: the protocol design, the tool surface and a worked example.",
    href: LINKS.spec,
  },
  {
    icon: GitCompareArrows,
    title: "Roadmap and changelog",
    description: "The open backlog and the shipped ledger, both in the repository.",
    href: LINKS.roadmap,
  },
];
