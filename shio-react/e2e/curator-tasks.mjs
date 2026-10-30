/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1315 — six curator jobs, driven through the console, and what each one costs.
 *
 * Every task starts at `/bento` with no deep link, because finding the place is part of the job,
 * and ends with the API confirming the outcome, because a script that clicked through without the
 * result is a measurement of nothing. Login and the task's own fixture are done before the meter
 * starts. The path each task takes is the shortest one a curator who knows the console would take,
 * so a figure here is a floor: a curator learning the console pays more, never less.
 *
 * What is counted, and why, is in `scripts/curator-tasks-budget.mjs`.
 *
 * Usage (a clean instance provisioned by `e2e/provision.mjs`, the Vite dev server in front of it)
 *   node e2e/curator-tasks.mjs                     # compare with curator-task-budget.json
 *   node e2e/curator-tasks.mjs --record            # rewrite the budget from this run
 *   node e2e/curator-tasks.mjs --only find-page    # one task
 *
 *   --base  console origin (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api   backend origin (default http://localhost:2710, env SHIO_URL)
 *   --site  the provisioned site (default "Console Check")
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD, the pair provision.mjs set.
 *
 * It mutates the instance it runs against (it publishes, approves and creates), so it refuses an
 * instance whose site it did not provision rather than run on somebody's content.
 */
import { chromium } from "playwright"
import { readFile, writeFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { compare, GATED, record } from "../scripts/curator-tasks-budget.mjs"

const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}
const BASE = flag("base", process.env.SHIO_CONSOLE_URL || "http://localhost:5173")
const API = flag("api", process.env.SHIO_URL || "http://localhost:2710")
const SITE = flag("site", process.env.SHIO_SITE || "Console Check")
const ONLY = flag("only", "")?.split(",").filter(Boolean)
const RECORD = args.includes("--record")
const USER = process.env.SHIO_USER || "admin"
const PASS = process.env.SHIO_PASSWORD || "admin"
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64")
const BUDGET = join(dirname(fileURLToPath(import.meta.url)), "..", "curator-task-budget.json")
/** Each run's fixtures carry this, so two runs against one instance never collide. */
const RUN = Date.now().toString(36)

async function api(path, init = {}) {
  const res = await fetch(`${API}/api/v2${path}`, { ...init, headers: { Authorization: AUTH, ...init.headers } })
  if (!res.ok) throw new Error(`${init.method ?? "GET"} ${path} → ${res.status} ${(await res.text()).slice(0, 300)}`)
  return res.json()
}

/** An agent run, the way an agent makes one: a session header and a premise on the agent surface. */
async function agentRun(session, intent, ops) {
  return api("/agent/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Mcp-Session-Id": session, "X-Shio-Intent": intent },
    body: JSON.stringify({ ops }),
  })
}

/** What a post says now, published and working, read the way an agent reads it. */
async function read(address) {
  // One address in, a list of one out: `/agent/read` answers every read as an array.
  const answer = await api(`/agent/read?address=${encodeURIComponent(address)}`)
  return Array.isArray(answer) ? answer[0] : answer
}

/** The pricing hero as the blueprint wrote it, published; written outside any run, so it is no agent's. */
async function restoreHero(extra = {}) {
  await api("/agent/batch", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ops: [
      { op: "post.upsert", address: `post:${SITE}/sections/pricing/hero`, type: "SectionHero",
        // Whole: an upsert replaces `data`, so a heading alone would erase the rest of the hero.
        folder: "/sections/pricing",
        data: { heading: "Pricing", subheading: "What Acme costs, without a call.", align: "left", ...extra } },
      { op: "post.publish", address: `post:${SITE}/sections/pricing/hero` },
    ] }),
  })
}

/** A post to the trash, the two-step way the agent surface asks for a deletion (P7). */
async function trash(address) {
  const ops = [{ op: "post.delete", address }]
  const plan = await api("/agent/batch", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ dryRun: true, ops }) })
  await api("/agent/batch", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ confirm: plan.confirm, ops }) })
}

/** A write from inside a console page, on its session — upload is console-only (CSRF). */
async function upload(page, folderId, name) {
  // A 2x2 PNG: enough to be an image, small enough to never matter.
  const png = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGP4z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg=="
  return page.evaluate(async ({ folderId, name, png }) => {
    const csrf = await fetch("/api/v2/csrf", { credentials: "include" }).then((r) => r.json())
    const bytes = Uint8Array.from(atob(png), (c) => c.charCodeAt(0))
    const form = new FormData()
    form.append("file", new Blob([bytes], { type: "image/png" }), name)
    form.append("folderId", folderId)
    const res = await fetch("/api/v2/staticfile/upload", {
      method: "POST", credentials: "include", body: form,
      headers: { [csrf.headerName || "X-XSRF-TOKEN"]: csrf.token },
    })
    if (!res.ok) throw new Error(`upload → ${res.status} ${await res.text()}`)
    return res.json()
  }, { folderId, name, png })
}

/**
 * The meter. Counted in the page by capture-phase listeners and reported to Node through a
 * binding, so a full page load (which resets the page's own state) loses nothing.
 */
async function meter(context) {
  const counts = { clicks: 0, keys: 0, screens: 0 }
  let on = false
  await context.exposeBinding("__shioUxCount", (_source, kind) => { if (on) counts[kind]++ })
  await context.addInitScript(() => {
    const editable = (el) => el instanceof HTMLElement
      && (el.isContentEditable || el.matches("input:not([type=checkbox]):not([type=radio]):not([type=button]):not([type=submit]), textarea"))
    addEventListener("click", (e) => { if (e.isTrusted) window.__shioUxCount("clicks") }, true)
    addEventListener("keydown", (e) => {
      if (!e.isTrusted || ["Shift", "Control", "Alt", "Meta"].includes(e.key)) return
      const typing = editable(e.target) && !e.ctrlKey && !e.metaKey && !["Enter", "Escape", "Tab", "ArrowDown", "ArrowUp"].includes(e.key)
      if (!typing) window.__shioUxCount("keys")
    }, true)
  })
  const watch = (page) => {
    let last = null
    page.on("framenavigated", (frame) => {
      if (frame !== page.mainFrame()) return
      const path = new URL(frame.url()).pathname
      if (on && path !== last) counts.screens++
      last = path
    })
  }
  return {
    counts, watch,
    start() { on = true; this.at = Date.now() },
    stop() { on = false; return { ...counts, ms: Date.now() - this.at } },
  }
}

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: "load" })
  await page.getByRole("textbox", { name: /usuário|username/i }).fill(USER)
  await page.getByRole("textbox", { name: /senha|password/i }).fill(PASS)
  await page.getByRole("button", { name: /^(entrar|sign in)$/i }).click()
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20_000 })
}

/** Ctrl+K, the title, and a click on the result of the given type: how a curator finds a post. */
async function openFromSearch(page, title, type) {
  await page.keyboard.press("Control+k")
  await page.getByRole("combobox").first().fill(title)
  // The option reads "<title><type> <state>" with no separator, so the type is matched after the title.
  await page.getByRole("option", { name: new RegExp(`^${title}.*${type}\\b`) }).first().click()
  await page.waitForURL(/\/content\/post\//, { timeout: 15_000 })
  await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 15_000 })
}

/**
 * Click, and wait for the write it causes to answer. Waiting for a word on screen was the first
 * draft and it was wrong: the editor already says "Publicado (no ar)" before anything is published,
 * so the check ran ahead of the write and read the old value.
 */
async function clickAndWrite(page, locator, matches = () => true) {
  const wrote = page.waitForResponse((r) => r.request().method() !== "GET" && r.url().includes("/api/v2/")
    && !r.url().includes("dryRun=true") && matches(r.url()), { timeout: 20_000 })
  await locator.click()
  const response = await wrote
  if (!response.ok()) throw new Error(`${response.request().method()} ${response.url()} → ${response.status()}`)
}

/**
 * Actions → Publish, then the confirmation. The menu item asks the server for a dry run and opens
 * "Publish this page?" with what it would do (P7), so publishing is three clicks by design; the
 * write to wait for is the real publish, not the dry run or the draft save that precede it.
 */
async function publish(page) {
  await page.getByRole("button", { name: /^(ações|actions)$/i }).first().click()
  await page.getByRole("menuitem", { name: /^(publicar|publish)$/i }).click()
  await clickAndWrite(page, page.getByRole("dialog").getByRole("button", { name: /^(publicar|publish)$/i }),
    (url) => /\/publish\b/.test(url))
}

/**
 * The six jobs. `setup` runs before the meter (fixture, and anything the job assumes is already
 * there); `run` is the curator's path from `/bento`; `check` asks the instance whether it happened.
 */
const TASKS = [
  {
    name: "change-headline",
    what: "change a page's headline and publish it",
    async run(page, ctx) {
      ctx.heading = `Pricing, plainly ${RUN}`
      await openFromSearch(page, "Pricing", "SectionHero")
      await page.getByRole("textbox", { name: /^heading/i }).fill(ctx.heading)
      await publish(page)
    },
    async check(ctx) {
      const post = await read(`post:${SITE}/sections/pricing/hero`)
      return JSON.stringify(post).includes(ctx.heading)
    },
    // A section's title is its heading, so the next run searches for "Pricing" again only if it is put back.
    cleanup: () => restoreHero(),
  },
  {
    name: "approve-run",
    what: "approve one agent run waiting in review",
    async setup(_page, ctx) {
      ctx.slug = `careers-${RUN}`
      ctx.intent = `Add a careers page ${RUN}`
      await agentRun(`ux-${RUN}-approve`, ctx.intent, [{
        op: "post.upsert", address: `post:${SITE}/${ctx.slug}`, type: "Page", folder: "/",
        data: { title: `Careers ${RUN}`, description: "Work with us.", content: "We are hiring." },
      }])
    },
    // SH1337 — home's waiting section names a pending run by its intent (SH1318), so the row to
    // press is this run's own, as the curator reads it, not a generic link to the queue.
    async run(page, ctx) {
      await page.getByRole("link", { name: ctx.intent }).first().click()
      await clickAndWrite(page, page.getByRole("button", { name: /^(aprovar|approve)$/i }).first())
    },
    // Its own page, live: a queue count would also move for a run some other task left behind.
    async check(ctx) {
      return (await read(`post:${SITE}/${ctx.slug}`))?.published === true
    },
  },
  {
    name: "replace-image",
    what: "replace a page's image with one from the library",
    async setup(page, ctx) {
      const sections = await api(`/agent/resolve?address=${encodeURIComponent(`folder:${SITE}/sections`)}`)
      // "Replace" means there is an image already: the old one is set and published before the
      // meter starts, so the task measures the same state on a fresh instance and on the tenth run.
      const old = await upload(page, sections.id, `hero-old-${RUN}.png`)
      ctx.file = `hero-new-${RUN}.png`
      ctx.fileId = (await upload(page, sections.id, ctx.file)).id
      await restoreHero({ image: old.id })
    },
    async run(page, ctx) {
      await openFromSearch(page, "Pricing", "SectionHero")
      // A filled image field opens the picker on the current file's folder, which is what makes a
      // replace short. An empty one opens beside the post since SH1325, and is not measured here.
      await page.getByRole("group", { name: /^image$/i }).getByRole("button", { name: /^(pesquisar|search)$/i }).click()
      const dialog = page.getByRole("dialog")
      await dialog.getByRole("button", { name: new RegExp(`^${ctx.file.replace(".", "\\.")}`) }).first().click()
      await dialog.getByRole("button", { name: /^(selecionar|select)$/i }).click()
      await publish(page)
    },
    cleanup: () => restoreHero(),
    // The image field holds the file's id, not its name.
    async check(ctx) {
      const post = await read(`post:${SITE}/sections/pricing/hero`)
      if (post.data?.image !== ctx.fileId) console.log(`  image=${post.data?.image} uploaded=${ctx.fileId} state=${post.state}`)
      return post.data?.image === ctx.fileId
    },
  },
  {
    name: "find-page",
    what: "find a page by its title and open it",
    async run(page) {
      await openFromSearch(page, "Pricing", "Page")
    },
    async check(_ctx, page) {
      return /\/content\/post\//.test(page.url())
        && await page.getByRole("heading", { level: 1, name: "Pricing" }).isVisible()
    },
  },
  {
    name: "create-page",
    what: "create a page under an existing section and publish it",
    async run(page, ctx) {
      ctx.title = `Team ${RUN}`
      // SH1338 — the palette offers "New page in <site>" when the reader has one site, which is the
      // site's page form; before it, the way there was Content, the hub, the Sites card, the site and
      // New Page. (With up to four sites the hub also names each, one screen shorter than the card.)
      await page.keyboard.press("Control+k")
      await page.getByRole("option", { name: /^(nova página em|new page in) /i }).first().click()
      await page.waitForURL(/\/post-type\/[^/]+\/new/)
      await page.getByRole("textbox", { name: /^title/i }).fill(ctx.title)
      // SH1326 — the form publishes: "Publish…" saves, and the editor opens on the new page with
      // the same publish confirmation already asking. It used to be Save, back to the folder, find
      // the page, open it, Actions, Publish, confirm.
      await clickAndWrite(page, page.getByRole("button", { name: /^(publicar|publish)…$/i }).first())
      await page.waitForURL(/\/content\/post\/[0-9a-f-]{36}/, { timeout: 15_000 })
      await clickAndWrite(page, page.getByRole("dialog").getByRole("button", { name: /^(publicar|publish)$/i }),
        (url) => /\/publish\b/.test(url))
    },
    // Live, not merely made: the task is "create and publish".
    async check(ctx) {
      const found = await api(`/agent/find?site=${encodeURIComponent(SITE)}&q=${encodeURIComponent(ctx.title)}`)
      return (found.results ?? []).some((r) => r.title === ctx.title && r.published === true)
    },
  },
  {
    name: "preview-draft",
    what: "preview a draft as the visitor will see it",
    async setup(_page, ctx) {
      ctx.title = `Draft ${RUN}`
      ctx.text = `Only in the draft ${RUN}`
      await agentRun(`ux-${RUN}-preview`, "Draft a page", [{
        op: "post.upsert", address: `post:${SITE}/draft-${RUN}`, type: "Page", folder: "/",
        data: { title: ctx.title, description: ctx.text, content: ctx.text },
      }])
    },
    async run(page, ctx) {
      await openFromSearch(page, ctx.title, "Page")
      const opened = page.context().waitForEvent("page")
      await page.getByRole("button", { name: /^(visualizar|preview)$/i }).first().click()
      ctx.tab = await opened
      await ctx.tab.waitForLoadState("load")
      ctx.screensExtra = 1
    },
    async check(ctx) {
      return (await ctx.tab.content()).includes(ctx.text)
    },
    // A draft left behind is a run left waiting, which the next approve-run would find first.
    cleanup: (ctx) => trash(`post:${SITE}/draft-${RUN}`),
  },
]

async function runTask(browser, task) {
  const context = await browser.newContext({ viewport: { width: 1920, height: 1000 }, locale: "pt-BR" })
  const m = await meter(context)
  const page = await context.newPage()
  m.watch(page)
  const ctx = {}
  try {
    await login(page)
    await task.setup?.(page, ctx)
    await page.goto(`${BASE}/bento`, { waitUntil: "load" })
    await page.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 20_000 })
    m.start()
    await task.run(page, ctx)
    const cost = m.stop()
    cost.screens += ctx.screensExtra ?? 0
    const ok = await task.check(ctx, page)
    if (!ok) throw new Error("the path ran, and the instance does not show the outcome")
    return { ...cost, ok: true }
  } catch (error) {
    const where = await page.locator("main").first().ariaSnapshot().catch(() => "(no main)")
    return { ok: false, error: String(error).split("\n")[0], where: `${page.url()}\n${where}` }
  } finally {
    await task.cleanup?.(ctx).catch((e) => console.log(`  cleanup of ${task.name} failed: ${e.message}`))
    await context.close()
  }
}

async function main() {
  const sites = await api("/site")
  if (!sites.some((s) => s.name === SITE)) {
    throw new Error(`no site '${SITE}' on ${API}: run e2e/provision.mjs against a clean instance first`)
  }
  const browser = await chromium.launch()
  const measured = {}
  let failed = 0
  const previous = JSON.parse(await readFile(BUDGET, "utf8").catch(() => "null"))
  for (const task of TASKS) {
    if (ONLY?.length && !ONLY.includes(task.name)) continue
    const result = await runTask(browser, task)
    const blocked = previous?.tasks?.[task.name]?.blocked
    if (!result.ok) {
      // A task the budget records as blocked by a filed defect is expected to fail; it is printed,
      // not passed silently, and the day it completes it is an improvement to record.
      if (blocked) {
        measured[task.name] = { blocked }
        console.log(`${task.name.padEnd(16)} BLOCKED ${blocked}  (${task.what})\n  ${result.error}`)
        continue
      }
      failed++
      console.log(`FAIL ${task.name} — ${task.what}\n  ${result.error}\n${result.where}`)
      continue
    }
    measured[task.name] = result
    console.log(`${task.name.padEnd(16)} ${GATED.map((f) => `${f}=${result[f]}`).join(" ")} ms=${result.ms}  (${task.what})`)
  }
  await browser.close()
  if (failed) {
    process.exitCode = 1
    return
  }
  if (RECORD) {
    await writeFile(BUDGET, JSON.stringify(record(measured, previous), null, 2) + "\n")
    console.log(`recorded ${Object.keys(measured).length} task(s) to ${BUDGET}`)
    return
  }
  if (!previous) throw new Error(`no budget at ${BUDGET}: run once with --record`)
  const budget = ONLY?.length
    ? { tasks: Object.fromEntries(Object.entries(previous.tasks).filter(([k]) => ONLY.includes(k))) }
    : previous
  const verdict = compare(measured, budget)
  for (const line of verdict.regressions) console.log(`REGRESSED ${line}`)
  for (const line of verdict.improvements) console.log(`IMPROVED  ${line} — re-record with --record`)
  for (const task of verdict.unbudgeted) console.log(`UNBUDGETED ${task}`)
  for (const task of verdict.unmeasured) console.log(`UNMEASURED ${task}`)
  if (!verdict.ok) process.exitCode = 1
}

main().catch((e) => {
  console.error(`curator-tasks: ${e.message}`)
  process.exitCode = 1
})
