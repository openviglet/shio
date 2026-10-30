/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import SURFACES from "../../../../shio-app/src/main/resources/console/surfaces.json"
import { ROUTES } from "@/app/routes.const"

/**
 * SH1028 — the verbs the command palette offers, compiled from the surface declaration.
 *
 * ## Why these are not a list of their own
 *
 * The palette used to be handed the nav array and nothing else: seventeen destinations, and a
 * placeholder honest enough to say *search areas*. Finding a post was open the palette, go to
 * search, type the term again. Adding "the actions a curator wants" as a fourth hand-kept list —
 * after the rail, the surface declaration and the agent's own op vocabulary — is how four lists
 * come to disagree about what this console does.
 *
 * So every command **names the surface it belongs to**, and the surface declaration is the
 * backend's file (SH979), imported here the way `bento-nav.ts` imports it. A command naming a
 * surface that does not exist fails at module load, and `bento-commands.test.mjs` holds the other
 * direction: the agent equivalent a command claims is the one the declaration names for that
 * surface, so the palette's verbs really are the MCP verbs in a console shape.
 *
 * ## `confirm` is a field, not a habit
 *
 * A palette is a place people press Enter fast. Anything that **writes** carries `confirm: true`
 * and its `route` opens the flow that asks — a publish plan, a form, a dry run — rather than
 * firing. That is P7 at the one surface where it is easiest to lose: the whole value of a palette
 * is that it is quicker than navigating, and "quicker" is exactly what turns a mis-typed Enter
 * into a published page. The rule is a field rather than a convention so a test can hold it, and
 * one does.
 */

/** One command the palette can offer. */
export interface ShioCommand {
  /** Stable id — what recency remembers, and what a test names. */
  id: string
  /** The surface this belongs to, which must be one the declaration names. */
  surface: string
  /** i18n key for the row's label. */
  labelKey: string
  /** Other words a reader might type for it. */
  aliases: readonly string[]
  /**
   * Where selecting it goes. Every command navigates — see `confirm`: a palette that performed
   * a write would be the fastest way in the product to do something by accident.
   */
  route: (context: ShioCommandContext) => string | null
  /**
   * True when this command writes, and therefore opens the flow that asks rather than acting.
   *
   * It is not "does the destination write" — it is "would selecting this row change content if
   * the destination did not stop to ask". Every one of these routes to a plan, a form or a dry
   * run, which is what makes the answer to that question always no.
   */
  confirm: boolean
  /** True when it is about the page the reader is on, which outranks everything else. */
  contextual?: (context: ShioCommandContext) => boolean
  /** Values the label interpolates, when it names something in the context (SH1338). */
  labelArgs?: (context: ShioCommandContext) => Record<string, string>
}

/** What the palette knows about where the reader is. */
export interface ShioCommandContext {
  /** The current path, which is how a command knows whether it is contextual. */
  pathname: string
  /** The folder being browsed, when one is. */
  folderId?: string | null
  /** The post being edited, when one is. */
  postId?: string | null
  /**
   * SH1338 — where "new page" goes when the reader has exactly one site: that site's page form.
   * Read only while the palette is open, so absent until then and on any instance with more sites.
   */
  newPage?: { site: string; href: string } | null
}

const DECLARED: ReadonlySet<string> = new Set(SURFACES.surfaces.map((surface) => surface.id))

/**
 * The commands, in declaration order — which is the order an empty palette shows them in.
 *
 * Ordered by how often a curator wants them rather than by surface, because an empty palette is
 * a menu and a menu's first row is the one it is really offering.
 */
const COMMANDS: readonly ShioCommand[] = [
  {
    // SH1338 — the most common job, one row from anywhere: the site's page form, which saves and
    // publishes. The palette routes to the form and never writes (SH1028).
    id: "new-page",
    surface: "sites",
    labelKey: "palette.newPageIn",
    labelArgs: (context) => ({ site: context.newPage?.site ?? "" }),
    aliases: ["create", "add", "page", "new"],
    route: (context) => context.newPage?.href ?? null,
    confirm: true,
    contextual: (context) => Boolean(context.newPage),
  },
  {
    id: "search-content",
    surface: "search",
    labelKey: "palette.searchContent",
    aliases: ["find", "posts", "drafts"],
    route: () => ROUTES.SEARCH,
    confirm: false,
  },
  {
    id: "new-post",
    surface: "sites",
    labelKey: "palette.newPost",
    aliases: ["create", "add", "page"],
    // Into the folder being browsed when there is one: "create a post here" is the offer, and
    // `here` is a fact the palette already has.
    route: (context) => (context.folderId
      ? `${ROUTES.CONTENT_ROOT}/folder/${context.folderId}/new-post`
      : ROUTES.CONTENT_ROOT),
    confirm: true,
    contextual: (context) => Boolean(context.folderId),
  },
  {
    id: "publish-page",
    surface: "sites",
    labelKey: "palette.publishPage",
    aliases: ["go live", "release"],
    // The post's own editor, which is where the publish plan is shown and confirmed. The palette
    // does not publish: it takes you to the page that asks.
    route: (context) => (context.postId
      ? `${ROUTES.CONTENT_ROOT}/post/${context.postId}`
      : null),
    confirm: true,
    contextual: (context) => Boolean(context.postId),
  },
  {
    id: "open-review",
    surface: "review",
    labelKey: "palette.openReview",
    aliases: ["queue", "approve", "waiting"],
    route: () => ROUTES.ADMIN_REVIEW,
    confirm: false,
  },
  {
    id: "open-activity",
    surface: "activity",
    labelKey: "palette.openActivity",
    aliases: ["history", "audit", "trail"],
    route: () => ROUTES.ADMIN_ACTIVITY,
    confirm: false,
  },
  {
    id: "open-scheduled",
    surface: "scheduled",
    labelKey: "palette.openScheduled",
    aliases: ["schedule", "upcoming", "later"],
    route: () => ROUTES.SCHEDULED,
    confirm: false,
  },
  {
    id: "open-files",
    surface: "files",
    labelKey: "palette.openFiles",
    aliases: ["media", "images", "assets", "upload"],
    route: () => ROUTES.FILES,
    confirm: false,
  },
  {
    id: "open-trash",
    surface: "trash",
    labelKey: "palette.openTrash",
    aliases: ["deleted", "restore", "undo"],
    route: () => ROUTES.TRASH,
    confirm: false,
  },
  {
    id: "apply-blueprint",
    surface: "marketplace",
    labelKey: "palette.applyBlueprint",
    aliases: ["package", "install", "starter", "template"],
    route: () => ROUTES.ADMIN_MARKETPLACE,
    confirm: true,
  },
  {
    id: "new-post-type",
    surface: "post-types",
    labelKey: "palette.newPostType",
    aliases: ["model", "field", "schema"],
    route: () => ROUTES.POST_TYPE_NEW,
    confirm: true,
  },
]

for (const command of COMMANDS) {
  if (!DECLARED.has(command.surface)) {
    throw new Error(
      `bento-commands: "${command.id}" names surface "${command.surface}", which `
        + "console/surfaces.json does not declare. A command for a surface that does not exist is "
        + "the fourth list this file was written to avoid.",
    )
  }
}

export const BENTO_COMMANDS = COMMANDS

/** The commands available here, with `contextual` resolved and dead routes dropped. */
export function commandsFor(context: ShioCommandContext): Array<ShioCommand & {
  contextualHere: boolean
}> {
  return COMMANDS
    .filter((command) => command.route(context) !== null)
    .map((command) => ({
      ...command,
      contextualHere: Boolean(command.contextual?.(context)),
    }))
}

/** What the palette knows, read off the router's own path. */
export function contextOf(pathname: string): ShioCommandContext {
  const folder = /\/content\/folder\/([^/]+)/u.exec(pathname)?.[1]
  const post = /\/content\/post\/([^/]+)/u.exec(pathname)?.[1]
  return {
    pathname,
    folderId: folder && folder !== "new" ? folder : null,
    postId: post && post !== "new" ? post : null,
  }
}
