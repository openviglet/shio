/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createRequire } from "node:module"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join, relative, sep } from "node:path"

/**
 * SH965 — which console handlers write to the backend and do not say how it ended.
 *
 * Three steps, all syntactic, over the TypeScript AST:
 *
 * 1. **Mutating methods.** A method of a class under `src/services` whose body calls
 *    `axios.post|put|patch|delete` is a write. The name is what is kept, because a page reaches a
 *    service through an instance (`siteService.purge`, `serviceFor(kind).purge`), never the class.
 * 2. **Write calls.** Outside `src/services`, a call `<receiver>.<write>(…)` whose receiver's text
 *    names a service (`/service/i`). That filter is what keeps `set.delete` and `params.delete`
 *    out: a collection is never named like a service here.
 * 3. **The handler and its two paths.** The handler is the nearest enclosing function that is not
 *    an inline callback: an argument to `.then`, `.map`, `Promise.all` and the like belongs to
 *    the function that made the call. A hook's argument (`useCallback(fn)`) is the handler. It reports **success** when it reaches a toast
 *    outside any catch, and **failure** when a `catch` clause or `.catch(…)` in it reaches one.
 *    A function anywhere in `src` whose own body toasts counts as reaching one, a single level
 *    deep, so `reportProblem(error, …)` and a local `fail(error)` are honoured and a whole call
 *    graph is not walked.
 *
 * `onSuccess`/`onError` of a mutation object count as the two paths, for the day one appears.
 */
const require_ = createRequire(import.meta.url)
const ts = require_("typescript")

const WRITE = /^(post|put|patch|delete)$/

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) walk(path, out)
    else if (/\.tsx?$/.test(name) && !/\.d\.ts$/.test(name)) out.push(path)
  }
  return out
}

function parse(file) {
  return ts.createSourceFile(file, readFileSync(file, "utf8"), ts.ScriptTarget.Latest, true,
    file.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
}

function visit(node, fn) {
  fn(node)
  ts.forEachChild(node, (child) => { visit(child, fn) })
}

/** `axios.post(...)`, `axios.delete<T>(...)`. */
function isAxiosWrite(node) {
  return ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
    && ts.isIdentifier(node.expression.expression) && node.expression.expression.text === "axios"
    && WRITE.test(node.expression.name.text)
}

export function mutatingMethods(servicesDir) {
  const names = new Set()
  for (const file of walk(servicesDir)) {
    visit(parse(file), (node) => {
      if (!(ts.isMethodDeclaration(node) || ts.isPropertyDeclaration(node)) || !node.name) return
      let writes = false
      visit(node, (inner) => { if (isAxiosWrite(inner)) writes = true })
      if (writes) names.add(node.name.getText())
    })
  }
  return names
}

function isInlineCallback(fn) {
  const call = fn.parent
  if (!ts.isCallExpression(call) || !call.arguments.includes(fn)) return false
  const callee = ts.isPropertyAccessExpression(call.expression) ? call.expression.name : call.expression
  return !/^use[A-Z]/.test(callee.getText())
}

function handlerOf(node) {
  let fn = null
  for (let at = node.parent; at; at = at.parent) {
    if (ts.isFunctionLike(at)) {
      fn = at
      if (!isInlineCallback(at)) return at
    }
  }
  return fn
}

/** A toast, or (SH966) a report pushed to the dock, which is the other outcome channel it reads. */
function isToast(node) {
  if (!ts.isCallExpression(node)) return false
  const callee = node.expression
  if (ts.isIdentifier(callee)) return callee.text === "toast" || callee.text === "pushReport"
  return ts.isPropertyAccessExpression(callee) && ts.isIdentifier(callee.expression)
    && callee.expression.text === "toast"
}

/** Names of the functions a file declares whose own body toasts. */
function reporters(sf) {
  const names = new Set()
  visit(sf, (node) => {
    let name = null
    let body = null
    if (ts.isFunctionDeclaration(node) && node.name) { name = node.name.text; body = node }
    else if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
      name = node.name.text; body = node.initializer
    }
    if (!name || !body) return
    let toasts = false
    visit(body, (inner) => { if (isToast(inner)) toasts = true })
    if (toasts) names.add(name)
  })
  return names
}

function reports(node, known) {
  let found = false
  visit(node, (inner) => {
    if (found || !ts.isCallExpression(inner)) return
    if (isToast(inner)) found = true
    else if (ts.isIdentifier(inner.expression) && known.has(inner.expression.text)) found = true
  })
  return found
}

function isInsideCatch(node, stop) {
  // `a.then(ok).catch(fail)`: `ok` sits under the `.catch` call too, as part of its callee, so
  // only the call's own arguments are the failure path.
  for (let child = node, at = node.parent; at && at !== stop; child = at, at = at.parent) {
    if (ts.isCatchClause(at) && child === at.block) return true
    if (ts.isCallExpression(at) && ts.isPropertyAccessExpression(at.expression)
      && at.expression.name.text === "catch" && at.arguments.includes(child)) return true
  }
  return false
}

/**
 * `deleteEach` (`lib/batch-delete.ts`) catches per target and hands the caller its refusals, so a
 * handler using it has no `catch` of its own and still reports both outcomes.
 *
 * <p>SH1139 — the same exemption {@link shellReports} makes, for the same reason: a helper that owns
 * the reporting contract is not a silent write, and the alternative was to move the delete behind a
 * local function so this reader stopped seeing it at all. That passes the lint by hiding from it,
 * which is worse than the defect.</p>
 *
 * <p>Narrow on purpose. It is not enough to <i>call</i> `deleteEach`: the handler has to say
 * something about `refusals`, which is the one thing the outcome carries that a silent caller would
 * drop. A handler that took the outcome and reported only `deleted` is exactly the swallow SH953 and
 * SH1139 both fixed, and still fails here.</p>
 */
const BATCH = "deleteEach"
function batchReports(handler) {
  let uses = false
  let refusals = false
  visit(handler, (node) => {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)
      && node.expression.text === BATCH) uses = true
    if (ts.isPropertyAccessExpression(node) && node.name.text === "refusals") refusals = true
  })
  return uses && refusals
}

function paths(handler, known) {
  let success = false
  let failure = batchReports(handler)
  visit(handler, (node) => {
    if (ts.isCatchClause(node) && reports(node.block, known)) failure = true
    if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)
      && node.expression.name.text === "catch" && node.arguments.some((a) => reports(a, known))) failure = true
    if (ts.isPropertyAssignment(node) && ts.isIdentifier(node.name)) {
      if (node.name.text === "onError" && reports(node.initializer, known)) failure = true
      if (node.name.text === "onSuccess" && reports(node.initializer, known)) success = true
    }
    if (!success && ts.isCallExpression(node) && !isInsideCatch(node, handler)
      && (isToast(node) || (ts.isIdentifier(node.expression) && known.has(node.expression.text)))) {
      success = true
    }
  })
  return { success, failure }
}

/**
 * `BentoEntityShell` toasts both outcomes of the `onUpdate` and `onDelete` it is handed
 * (`forms.common.updated`/`notUpdated`, `deleted`/`notDeleted`, read out of the shipped bundle),
 * so a handler passed there reports through the package's contract rather than its own body.
 */
const SHELL_REPORTED = new Set(["onUpdate", "onDelete"])
function shellReports(handler) {
  for (let at = handler.parent; at && !ts.isSourceFile(at); at = at.parent) {
    if (ts.isJsxAttribute(at)) {
      const element = at.parent.parent
      return SHELL_REPORTED.has(at.name.getText()) && element.tagName?.getText() === "BentoEntityShell"
    }
    if (ts.isFunctionLike(at)) return false
  }
  return false
}

function handlerName(handler, sf) {
  let at = handler
  while (at && !ts.isSourceFile(at)) {
    if ((ts.isVariableDeclaration(at) || ts.isFunctionDeclaration(at) || ts.isMethodDeclaration(at)
      || ts.isPropertyAssignment(at) || ts.isJsxAttribute(at)) && at.name) {
      return at.name.getText(sf)
    }
    at = at.parent
  }
  return "(anonymous)"
}

/**
 * Every write call outside the services, with whether its handler reports each path. `id` is
 * `file#handler.method`, stable across line moves, which is what an asserted list needs.
 */
export function writeSites(srcDir) {
  const methods = mutatingMethods(join(srcDir, "services"))
  const files = walk(srcDir)
  const known = new Set()
  for (const file of files) for (const name of reporters(parse(file))) known.add(name)
  const sites = []
  for (const file of files) {
    const rel = relative(srcDir, file).split(sep).join("/")
    if (rel.startsWith("services/")) continue
    const sf = parse(file)
    visit(sf, (node) => {
      if (!ts.isCallExpression(node) || !ts.isPropertyAccessExpression(node.expression)) return
      const method = node.expression.name.text
      if (!methods.has(method) || !/service/i.test(node.expression.expression.getText(sf))) return
      const handler = handlerOf(node)
      if (!handler) return
      const name = handlerName(handler, sf)
      sites.push({
        id: `${rel}#${name}.${method}`,
        line: sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1,
        ...(shellReports(handler) ? { success: true, failure: true, via: "BentoEntityShell" } : paths(handler, known)),
      })
    })
  }
  return { methods, sites }
}
