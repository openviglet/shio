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
import { after, test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH964 — a toast moves the dock, and nothing in the console had to change for it.
 *
 * The claim worth testing is the one the design rests on: `useAssistantNotifications` reads the
 * live toast list, so the console's existing `toast.error(...)` calls reach the dock untouched.
 * That only exists with effects running, which `renderToStaticMarkup` never does — so this mounts
 * the real component in jsdom and drives it with the package's own `toast`, the one every file in
 * `src` imports.
 */
const { JSDOM } = await import("jsdom")
const dom = new JSDOM("<!doctype html><html><body><div id=root></div></body></html>", { pretendToBeVisual: true })
let reducedMotion = false
dom.window.matchMedia = (query) => ({
  matches: /prefers-reduced-motion:\s*reduce/.test(query) ? reducedMotion : false,
  media: query, onchange: null,
  addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false },
})
for (const key of ["window", "document", "navigator", "HTMLElement", "Node", "MutationObserver", "getComputedStyle",
  "requestAnimationFrame", "cancelAnimationFrame", "matchMedia"]) {
  Object.defineProperty(globalThis, key, { value: dom.window[key] ?? dom.window, configurable: true, writable: true })
}
globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} }
globalThis.IS_REACT_ACT_ENVIRONMENT = true

const { createElement: h, act } = await import("react")
const { createRoot } = await import("react-dom/client")
const { toast } = await import("@viglet/viglet-design-system")
const { BentoStatusDock } = await import("../src/app/system/bento-status-dock.ts")

const HERE = dirname(fileURLToPath(import.meta.url))
const container = dom.window.document.getElementById("root")
const root = createRoot(container)
after(() => act(() => root.unmount()))

/** The mascot draws on a canvas and names its state there; that attribute is the dock's state. */
const stateOf = () => container.querySelector("canvas[data-state]")?.getAttribute("data-state")
const settle = () => act(() => new Promise((resolve) => setTimeout(resolve, 30)))

test("SH964: the dock mounts idle, with no composer, because Shio passes no onSend", async () => {
  await act(() => root.render(h(BentoStatusDock)))
  await settle()
  assert.ok(container.innerHTML.length > 0, "SH964: the dock rendered nothing")
  assert.equal(container.querySelector("textarea, input[type=text]"), null,
    "SH964: the dock renders a composer. Shio hosts no model; a chat box with nothing behind it is a"
      + " promise the product cannot keep, which is why the component passes no onSend.")
  assert.equal(stateOf(), "idle", "SH964: with no toast on screen the dock is not idle")
})

test("SH964: a toast.error moves the dock to error, with the toast's title as its caption", async () => {
  await act(() => { toast.error("Publish refused: the page has no title") })
  await settle()
  assert.equal(stateOf(), "error",
    "SH964: a toast.error did not move the dock. The hook reads the live toast list, so this means"
      + " the dock and the console's toast are not the same sonner instance.")
  assert.match(container.textContent, /Publish refused: the page has no title/,
    "SH964: the dock's caption is not the toast's title")
  assert.ok(container.querySelector("[aria-live]"),
    "SH964: the caption is not a live region, so a screen-reader user hears nothing")
})

test("SH964: an empty toast list returns the dock to idle", async () => {
  await act(() => { toast.dismiss() })
  await act(() => new Promise((resolve) => setTimeout(resolve, 600)))
  assert.equal(stateOf(), "idle", "SH964: the dock did not return to idle once the toast list emptied")
  assert.doesNotMatch(container.textContent, /Publish refused/,
    "SH964: the dock still carries a dismissed toast's caption")
})

test("SH964: the shell hands the dock its corner", () => {
  const shell = readFileSync(join(HERE, "..", "src", "app", "system", "bento.root.page.tsx"), "utf8")
  assert.match(shell, /dock=\{<BentoStatusDock \/>\}/,
    "SH964: the dock is not passed to BentoShell's dock slot. Mounted anywhere else it pins itself to"
      + " the viewport corner and covers back-to-top, which is what VDS133 moved into the shell.")
})

/*
 * SH966 — a refusal goes to the dock as a report the curator can act on, and not also as a toast.
 * `reportRefusal` is imported from the real `lib/problem.ts` and fed an axios-shaped 400 carrying a
 * problem document, the same shape the backend's teaching errors have.
 */
const { reportRefusal } = await import("../src/lib/problem.ts")
const { currentReports, dismissReport } = await import("../src/lib/dock-reports.ts")

const translate = (key, options = {}) => `${key}:${JSON.stringify(options)}`

test("SH966: a refusal with at and didYouMean becomes a report whose actions go to the field and apply the fix", async () => {
  const went = []
  const applied = []
  const toastsBefore = toast.getToasts().length
  const refusal = {
    message: "Request failed with status code 400",
    response: {
      status: 400,
      data: {
        type: "https://shio.viglet.org/problems/unknown-field", title: "Unknown field",
        detail: "The post type Article has no field 'titel'.", fix: "Use one of the declared fields.",
        at: "data.titel", didYouMean: "title",
      },
    },
  }

  await act(() => {
    reportRefusal(refusal, "An error occurred", {
      t: translate,
      goToField: (field) => went.push(field),
      applySuggestion: (field, value) => applied.push([field, value]),
    })
  })
  await settle()

  assert.equal(toast.getToasts().length, toastsBefore,
    "SH966: the refusal was toasted as well as reported, so the curator reads it twice and the"
      + " live region announces it twice")
  const report = currentReports().at(-1)
  assert.equal(report.role, "report")
  assert.equal(report.tone, "error")
  assert.match(report.text, /has no field 'titel'.*Use one of the declared fields/,
    "SH966: the report does not carry the server's detail and fix")
  assert.equal(report.actions.length, 2, "SH966: expected go-to-field and apply-suggestion")

  report.actions[0].onSelect()
  assert.deepEqual(went, ["titel"], "SH966: the first action does not go to the field `at` names")
  report.actions[1].onSelect()
  assert.deepEqual(applied, [["titel", "title"]], "SH966: the second action does not apply didYouMean")

  assert.equal(stateOf(), "error", "SH966: an unread refusal did not turn the mascot to error")
  assert.match(container.textContent, /has no field 'titel'/,
    "SH966: the dock's caption is not the refusal's detail")
  await act(() => dismissReport(report.id))
})

test("SH966: a refusal with no problem document still reports, carrying its HTTP status", async () => {
  await act(() => {
    reportRefusal({ message: "Request failed", response: { status: 502, data: "<html>Bad Gateway</html>" } },
      "An error occurred", { t: translate })
  })
  const report = currentReports().at(-1)
  assert.match(report.text, /common\.refusalStatus.*"status":502/,
    "SH966: a bare refusal reported without its status, which is the one fact it has")
  assert.deepEqual(report.actions, [], "SH966: a refusal with no `at` offers no field actions")
  await act(() => dismissReport(report.id))
  await settle()
  assert.equal(stateOf(), "idle", "SH966: with every report dismissed the dock is not idle again")
})

test("SH966: the post editor reports its refusals to the dock, not as toasts", () => {
  const page = readFileSync(join(HERE, "..", "src", "app", "content", "post.edit.page.tsx"), "utf8")
  assert.doesNotMatch(page, /reportProblem\(/,
    "SH966: the post editor toasts a refusal again. Its save, publish and unpublish refusals go to"
      + " the dock through reportRefusal, which keeps them until read and offers the field and fix.")
  assert.ok((page.match(/reportRefusal\(error, t\("common\.error"\), refusalActions\)/g) ?? []).length >= 4,
    "SH966: fewer than four of the editor's refusal paths report through reportRefusal")
})

test("SH968: an agent run that is writing turns the mascot to working, with its caption", async () => {
  const { setWorking } = await import("../src/lib/dock-reports.ts")
  await act(() => setWorking("claude-code is working on acme"))
  await settle()
  assert.equal(stateOf(), "working", "SH968: a running agent did not move the mascot to working")
  assert.match(container.textContent, /claude-code is working on acme/,
    "SH968: the caption does not say which agent is working where")
  await act(() => setWorking(null))
  await settle()
  assert.equal(stateOf(), "idle", "SH968: the mascot kept working after the run ended")
})

/*
 * SH971 — what the dock reports is the curator's choice, and a failure is never theirs to hide.
 * Driven through the real store and the mounted dock.
 */
test("SH971: a muted kind drops its reports, except a failure, which always reaches the dock", async () => {
  const { pushReport, setMutedKinds } = await import("../src/lib/dock-reports.ts")
  await act(() => setMutedKinds(["arrivals", "schedules"]))
  const before = currentReports().length

  assert.equal(pushReport({ tone: "attention", text: "a lead arrived" }, "arrivals"), undefined,
    "SH971: a report of a muted kind reached the dock")
  const failure = pushReport({ tone: "error", text: "the launch did not happen" }, "schedules")
  assert.ok(failure, "SH971: a muted kind's FAILURE was dropped; a failure is the report least safe to lose")
  const unmuted = pushReport({ tone: "success", text: "agent finished" }, "agent-runs")
  assert.ok(unmuted, "SH971: a kind nobody muted was dropped")
  assert.equal(currentReports().length, before + 2)

  await act(() => { dismissReport(failure); dismissReport(unmuted); setMutedKinds([]) })
})

test("SH971: with saves muted, a save's toast no longer moves the mascot, but a failing toast does", async () => {
  const { setMutedKinds } = await import("../src/lib/dock-reports.ts")
  await act(() => setMutedKinds(["saves"]))
  await act(() => { toast.success("Saved successfully") })
  await settle()
  assert.equal(stateOf(), "idle", "SH971: a muted save still moved the mascot")

  await act(() => { toast.error("Could not save") })
  await settle()
  assert.equal(stateOf(), "error", "SH971: a failing toast was muted along with the saves")

  await act(() => { toast.dismiss(); setMutedKinds([]) })
  await act(() => new Promise((resolve) => setTimeout(resolve, 600)))
})

test("SH971: the shell loads the choice and offers it, and the kinds agree with the server's", () => {
  const shell = readFileSync(join(HERE, "..", "src", "app", "system", "bento.root.page.tsx"), "utf8")
  assert.match(shell, /useDockPreferencesLoad\(\)/, "SH971: the curator's choice is never read")
  assert.match(shell, /<BentoDockPreferences \/>/, "SH971: nothing lets a curator make the choice")

  const client = readFileSync(join(HERE, "..", "src", "services", "dock", "dock.service.ts"), "utf8")
  const server = readFileSync(join(HERE, "..", "..", "shio-app", "src", "main", "java", "com", "viglet",
    "shio", "api", "auth", "dock", "ShDockPreferencesService.java"), "utf8")
  const kinds = (text, pattern) => [...(pattern.exec(text)?.[1] ?? "").matchAll(/"([a-z-]+)"/g)].map((m) => m[1])
  assert.deepEqual(kinds(client, /DOCK_KINDS = \[([^\]]*)\]/), kinds(server, /KINDS =\s*List\.of\(([^)]*)\)/),
    "SH971: the console offers a kind the server refuses, or the server accepts one nobody can choose")
})

/*
 * SH972 — the Universal Editor is outside the shell, so it docks the assistant itself, in flow at
 * the foot of its properties panel, and its saves drive it.
 */
test("SH972: the inline dock renders in flow, never pinned over the page", async () => {
  const holder = dom.window.document.createElement("div")
  dom.window.document.body.append(holder)
  const inlineRoot = createRoot(holder)
  await act(() => inlineRoot.render(h(BentoStatusDock, { inline: true })))
  const box = holder.firstElementChild
  assert.ok(box, "SH972: the inline dock rendered nothing")
  assert.doesNotMatch(box.className, /\bfixed\b/,
    "SH972: the inline dock is pinned to the viewport, so in the editor it would cover the preview")
  await act(() => inlineRoot.unmount())
  holder.remove()
})

test("SH972: the editor docks it in the properties panel, and its saves drive it", () => {
  const editor = readFileSync(join(HERE, "..", "src", "app", "editor", "visual-editor.page.tsx"), "utf8")
  const aside = editor.slice(editor.indexOf("<aside"), editor.indexOf("</aside>"))
  assert.match(aside, /<BentoStatusDock inline \/>/,
    "SH972: the dock is not in the editor's properties panel, so the one surface that saves on"
      + " every click has no outcome channel")
  const persist = editor.slice(editor.indexOf("const persist = async"), editor.indexOf("const refresh = () =>"))
  assert.match(persist, /setWorking\(t\("universalEditor\.saving"\)\)/, "SH972: a save in flight moves nothing")
  assert.match(persist, /setWorking\(null\)/, "SH972: the working state outlives the save")
  assert.match(persist, /reportRefusal\(error,/,
    "SH972: a refused save is the word Error again rather than the server's problem document")
  assert.match(persist, /universalEditor\.draftSavedFields/, "SH972: a landed save does not name its fields")
})
