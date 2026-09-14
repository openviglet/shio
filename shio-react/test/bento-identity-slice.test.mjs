/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH904 — the identity slice: users, groups and roles.
 *
 * §LV.7 called these "the clearest case in the product for the entity shell" on the grounds that
 * "each of these entities has a title, a description and an enabled state". Two of the three
 * claims are wrong, and both were only findable by opening the models:
 *
 * - **No `enabled`.** `ShGroup` and `ShRole` are `{id, name, description}` and nothing more, so
 *   `hasStatus` stays off. A status pill driven by a column that does not exist would have
 *   rendered "Idle" against every group in the instance and meant nothing.
 * - **A user has neither.** `ShUser` is `{username, firstName, lastName, email, password?}` —
 *   its identity is a **login key** and its name is **two columns**. The shell's hero inline-edits
 *   a single `title` and commits on blur, so mapping `title` to `username` would rename someone's
 *   login by tabbing away from it, and mapping it to the full name would need a whitespace split
 *   that is lossy for half the names in the world.
 *
 * So the slice divides where the data divides rather than where the section predicted: groups and
 * roles take the shell, and the user *list* takes the mosaic while the user *editor* stays the
 * console component under the chrome provider.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const bento = (name) => stripComments(readFileSync(join(SRC, "app", "admin", name), "utf8"))

const ENTITY = bento("identity-entity.page.tsx")
const LIST = bento("admin-list.page.tsx")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

test("SH904: a create page sends no id, because a blank one is stored verbatim", () => {
  /*
   * The assertion this task's worst defect earns (SH527). The first draft passed
   * `{ id: "", name, description }` — `IdentityEntity` requires the field, and the empty string
   * is the honest value for "not assigned yet". `POST /v2/group` stored it: the row appeared in
   * the list and **nothing could address it**, because `/v2/group/` does not route and every
   * id-shaped path 404s. Read 200, delete 404, still listed. It had to be removed at the DB.
   *
   * Nothing about the flow looked wrong — the page navigated to the list and the group was on it.
   * Only reading the created row back showed it. SH924 carries the server half; this is the half
   * that stops the client re-sending it.
   */
  assert.doesNotMatch(ENTITY, /create\(\{\s*id:/,
    "SH904: the create call sends an `id` again. A blank primary key is persisted verbatim and"
      + " produces a row that GET lists, DELETE 404s and no console path can reach. Send the"
      + " fields and let the server mint the key — the type is `Omit<IdentityEntity, \"id\">` for"
      + " exactly this reason.")
  assert.match(ENTITY, /Omit<IdentityEntity, "id">/,
    "SH904: `create` no longer states that it takes an entity without an id, so the next caller"
      + " has nothing stopping them passing a blank one.")
})

test("SH904: the create page has a real form, and the edit page has no dead save button", () => {
  /*
   * The two halves of the same trap, which the shell resolves in opposite directions.
   *
   * A group is nothing but identity, so the form body is empty — SH899's dead-button shape.
   * `autosaveOnly` suppresses Save on an **existing** entity (its own line is
   * `showSaveSubmit = !(autosaveOnly && !isNew)`) and deliberately keeps it on a **new** one,
   * because there Save is what creates the entity. So the new branch must render a real
   * `<form id={formId}>` or the only button on the page does nothing, silently.
   */
  assert.match(ENTITY, /autosaveOnly/,
    "SH904: the identity editor dropped `autosaveOnly`, so an existing group renders a Save"
      + " button whose form has no fields — SH899's dead button exactly.")
  assert.match(ENTITY, /<form\s+id=\{formId\}/,
    "SH904: the create branch lost its form. `autosaveOnly` does not hide Save in new mode, so"
      + " the hero renders `<GradientButton type=\"submit\" form={formId}>` — and with no form of"
      + " that id the create button is inert and the page still looks finished.")
})

test("SH904/SH905: a load failure is not the empty state, on the one list every page shares", () => {
  /*
   * SH905 moved this from `identity-list.page.tsx` to `admin-list.page.tsx`, and the move is the
   * point rather than a rename. SH904 wrote a list fixed to `{id, name, description}` because
   * that is all a group is; SH905 needed the same list for five entities that also carry a status
   * and a second identifying line. Two nearly-identical list components is the state that slice
   * exists to leave, not to arrive at — so there is one, and this assertion covers all eight
   * lists at once because they are all the same component now.
   */
  assert.match(LIST, /\.catch\(/,
    "SH904: the shared admin list has no catch, so a failed request leaves the array empty and"
      + " every one of the eight lists renders 'no results'. The console lists all did this:"
      + " 'no groups' and 'the server did not answer' were the same screen.")
  assert.match(LIST, /setError/,
    "SH904: the shared admin list swallows the failure instead of reporting it — and"
      + " `items: undefined` means 'still loading' to the package's gate, so it would spin.")
})

test("SH904: the user editor is not on the entity shell, and the reason is in the model", () => {
  const editor = stripComments(
    readFileSync(join(SRC, "app", "admin", "admin.user.page.tsx"), "utf8"))

  assert.doesNotMatch(editor, /BentoEntityShell/,
    "SH904: the user editor mounts the entity shell. Its hero inline-edits a single `title` and"
      + " commits on blur — against a user that is either the login key (renamed by tabbing away)"
      + " or a full name that has to be split back into two columns. Neither is a rename a curator"
      + " asked for, which is P7. It stays an adapter page.")
  assert.match(editor, /PageHero/,
    "SH904: the user editor lost its adaptive header, so it cannot follow the chrome at all.")

  const model = readFileSync(join(SRC, "models", "auth", "user.ts"), "utf8")
  assert.doesNotMatch(model, /^\s*(title|description)\??:/m,
    "SH904: `ShUser` grew a title or a description. If it now has both, the argument above no"
      + " longer holds and the user editor should be reconsidered against the shell — but check"
      + " what `username` does first: it is still the key the routes and the update call use.")
})

test("SH904: `new` is a param value, not a static route beside the param one", () => {
  /*
   * A static `admin/groups/new` sibling matches before `:groupId` and carries **no param at all**,
   * so `useParams().groupId` is undefined, `isNew` is false, the load never runs and the page
   * renders `null`. It reads as a route-ordering problem and is the opposite one. The live check
   * caught it as "no hero heading on a bento surface"; nothing in this suite would have.
   */
  for (const segment of ["groups", "roles"]) {
    assert.doesNotMatch(APP, new RegExp(`path="admin/${segment}/new"`),
      `SH904: a static admin/${segment}/new route is back. It matches before the param route and`
        + " carries no param, so `isNew` is false and the create page renders nothing at all."
        + " `new` is a value of `:id`, which is the sentinel the console already uses.")
  }
})
