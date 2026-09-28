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
  assert.match(shell, /dock=\{<BentoStatusDock onSend=\{onSend\} \/>\}/,
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

test("SH971: a curator's still mascot reaches the dock, which then shows its caption whole, untyped", async () => {
  // The package marks a paused dock in no attribute; what a person sees change is the caption,
  // which a paused dock sets at once instead of typing (VDS171). So the same sentence is read
  // shortly after the toast, moving and still: typed, it is partial; still, it is all there.
  const { setMascotStill } = await import("../src/lib/dock-reports.ts")
  const sentence = "The schedule for the spring campaign landing page could not be saved because the date is in the past"
  // A moving dock types one character per 14 ms, so this sentence takes about 1.4 s to type; at
  // 100 ms it shows a few characters, and the toast has long since reached the dock.
  const shortly = () => act(() => new Promise((resolve) => setTimeout(resolve, 100)))
  // The visible caption is aria-hidden (the live region carries the sentence whole for a reader,
  // moving or not), so that is the text read here.
  const typedWhole = () => [...container.querySelectorAll('[aria-hidden="true"]')]
    .some((node) => node.textContent === sentence)

  await act(() => { toast.error(sentence) })
  await shortly()
  assert.ok(!typedWhole(),
    "SH971: the control — a moving dock already shows the whole caption this soon, so the comparison"
      + " below cannot tell a paused dock from a moving one")
  await act(() => { toast.dismiss() })
  await act(() => new Promise((resolve) => setTimeout(resolve, 600)))

  await act(() => setMascotStill(true))
  await act(() => { toast.error(sentence) })
  await shortly()
  assert.ok(typedWhole(),
    "SH971: the curator froze the mascot and the dock still typed its caption, so `paused` never reached"
      + " VigletAssistant. The caption must still arrive whole — only the motion stops.")
  assert.ok(container.querySelector("[aria-live]"), "SH971: a still dock stopped announcing")

  await act(() => { toast.dismiss(); setMascotStill(false) })
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

/*
 * SH1006 — the dock's chat reaches the connected agent over notes. ShNoteRelayTest proves the
 * server half (the request is listed on shio://requests and the agent's note.add reply lands under
 * it); this proves the dock half on the real store and the mounted component.
 */
test("SH1006: with onSend the dock opens into a chat, a question waits, and the reply reads after it", async () => {
  const chat = await import("../src/lib/dock-chat.ts")
  const holder = dom.window.document.createElement("div")
  dom.window.document.body.append(holder)
  const chatRoot = createRoot(holder)
  const sent = []
  await act(() => chatRoot.render(h(BentoStatusDock, { inline: true, onSend: (text) => sent.push(text) })))
  await act(() => holder.querySelector("button[aria-expanded]").click())
  await settle()
  assert.ok(holder.querySelector("textarea, input"),
    "SH1006: given onSend the dock renders no composer, so a curator cannot ask anything")

  await act(() => chat.recordRequest({ id: "note-1", objectId: "post-1", body: "Shorten the intro?" }))
  assert.equal(chat.waiting(chat.currentChat()).length, 1, "SH1006: a sent question is not waiting")
  assert.equal(chat.replyFor("note-1", [
    { id: "n-2", objectId: "post-1", kind: "COMMENT", body: "x", parentId: "note-1", resolved: false },
    { id: "n-3", objectId: "post-1", kind: "REPLY", body: "Done.", parentId: "note-1", resolved: false },
  ])?.id, "n-3", "SH1006: the reply is found by kind and parent, not by being the next note")

  await act(() => chat.recordReply("note-1", { id: "n-3", body: "Done, two sentences." }))
  await settle()
  const text = holder.textContent
  assert.ok(text.indexOf("Shorten the intro?") >= 0 && text.indexOf("Shorten the intro?") < text.indexOf("Done, two sentences."),
    "SH1006: the transcript does not read the question and then its answer")
  assert.equal(chat.waiting(chat.currentChat()).length, 0)
  await act(() => chatRoot.unmount())
  holder.remove()
})

test("SH1006: the shell offers the chat only with an agent around and a page open", () => {
  const relay = readFileSync(join(HERE, "..", "src", "app", "system", "use-dock-chat-relay.ts"), "utf8")
  const available = readFileSync(join(HERE, "..", "src", "app", "system", "use-agent-available.ts"), "utf8")
  assert.match(available, /noteService\.relay\(\{ signal: abort\.signal \}\)/, "SH1006: nothing asks whether an agent is around")
  assert.match(relay, /const agentActive = useAgentAvailable\(\)/, "SH1006: the dock no longer asks before offering the chat")
  assert.match(relay, /agentActive && anchor/,
    "SH1006: the composer appears with nobody behind it, or with no page for the request to hang on")
  assert.match(relay, /if \(pending === 0\) return/, "SH1006: replies are polled when nothing is waiting")
  const send = readFileSync(join(HERE, "..", "src", "app", "system", "dock-chat-send.ts"), "utf8")
  assert.match(send, /kind: "request"/, "SH1006: a question from the dock is not a request note")
})

/*
 * SH1007 — an answer that arrives with its change attached. ShNoteRelayTest holds the server half
 * (the reply names the run that drafted the change, and approving that page in the run publishes
 * it); this holds the console half.
 */
test("SH1007: a reply names its run only when an agent session wrote it", async () => {
  const chat = await import("../src/lib/dock-chat.ts")
  const note = (agentSession) => ({ id: "n", objectId: "p", kind: "REPLY", body: "b", resolved: false, agentSession })
  assert.equal(chat.runOf(note("run-7")), "run-7")
  assert.equal(chat.runOf(note("console:ana:2026-09-25")), undefined,
    "SH1007: a curator's own console reply offered to approve a run that is not an agent's")
  assert.equal(chat.runOf(note(null)), undefined)
})

test("SH1007: a reply with a run offers the change, and approving goes through review's decisions", () => {
  const relay = readFileSync(join(HERE, "..", "src", "app", "system", "use-dock-chat-relay.ts"), "utf8")
  assert.match(relay, /const run = runOf\(reply\)/, "SH1007: the reply's run is never read")
  assert.match(relay, /ROUTES\.ADMIN_REVIEW\}\?session=/, "SH1007: the change cannot be opened from the answer")
  assert.match(relay, /approveFromDock\(run, turn\.objectId/, "SH1007: the change cannot be approved from the answer")
  const send = readFileSync(join(HERE, "..", "src", "app", "system", "dock-chat-send.ts"), "utf8")
  assert.match(send, /reviewService\.decide\(run, \[\{ postId, decision: "APPROVE" \}\]\)/,
    "SH1007: the dock's approval is not the review queue's own call, so it is a way around review")
})

/*
 * SH1019 — ask the connected agent from a field, and apply its answer into the form. The server
 * leg is the note vocabulary SH1006 and ShNoteRelayTest already hold, with a field on the request.
 */
test("SH1019: an applied suggestion waits for its post and is taken exactly once", async () => {
  const store = await import("../src/lib/field-suggestions.ts")
  let heard = 0
  const off = store.onSuggestion(() => { heard += 1 })
  store.requestApply({ postId: "p-1", field: "title", value: "First" })
  store.requestApply({ postId: "p-1", field: "title", value: "Pricing, simply" })
  store.requestApply({ postId: "p-2", field: "title", value: "Other page" })
  assert.equal(heard, 3, "SH1019: the editor is not told a suggestion arrived")
  assert.deepEqual(store.takeFor("p-1"), [{ postId: "p-1", field: "title", value: "Pricing, simply" }],
    "SH1019: a second apply to the same field did not replace the first")
  assert.deepEqual(store.takeFor("p-1"), [], "SH1019: a suggestion was applied twice")
  assert.equal(store.takeFor("p-2").length, 1, "SH1019: another post's suggestion was taken by this one")
  off()
})

test("SH1019: a field asks with its path, the answer offers to apply, and applying edits the form only", () => {
  const read = (...path) => readFileSync(join(HERE, "..", "src", ...path), "utf8")
  const fields = read("components", "widgets", "post-form-fields.tsx")
  assert.match(fields, /onAsk && ASKABLE\.has\(field\.widget\)/, "SH1019: no field can ask the agent")
  const editor = read("app", "content", "post.edit.page.tsx")
  assert.match(editor, /onAsk=\{agentAvailable \? setAsking : undefined\}/,
    "SH1019: the ask control shows with no agent behind it")
  assert.match(editor, /useAppliedSuggestions\(postId, post != null, setData\)/,
    "SH1019: an applied suggestion does not reach the form, or arrives before the load overwrites it")
  const dialog = read("components", "ask-agent-dialog.tsx")
  assert.match(dialog, /sendRequest\(postId, text\.trim\(\), say, field\.name\)/,
    "SH1019: the question is not anchored to the field it is about")
  const relay = read("app", "system", "use-dock-chat-relay.ts")
  assert.match(relay, /requestApply\(\{ postId: turn\.objectId, field, value: reply\.body \}\)/,
    "SH1019: the answer does not offer to apply its value to the field")
  const hook = read("app", "content", "use-applied-suggestions.ts")
  assert.doesNotMatch(hook, /service|axios/i, "SH1019: applying a suggestion writes to the server instead of the form")
})
