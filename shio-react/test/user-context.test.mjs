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
import { createElement as h } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import {
  UserProvider as VigUserProvider,
  useCurrentUser as useVigCurrentUser,
} from "@viglet/viglet-design-system"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH920 — one provider satisfies both user hooks, from one fetch.
 *
 * ## The failure this closes, and why it has no type error
 *
 * Shio declared its own `UserProvider`/`useCurrentUser` pair. A local variant of a context is a
 * **different context object**, so mounting Shio's around a shared component that reads the
 * package's satisfies nothing — `BentoUserMenu` threw
 * `useCurrentUser must be used within UserProvider` and blanked the whole bento shell behind the
 * error boundary. `tsc` is silent: both are named `UserProvider`, both take children, and the
 * mismatch exists only at runtime in `useContext`. So is every import scan.
 *
 * SH888 worked around it by mounting the package's provider in the bento shell alone, which left
 * the console tree on one context and the bento tree on the other, and put the same trap in front
 * of each of Block AR's fourteen surfaces. This closes it at the source: Shio's provider now
 * *wraps* the package's and republishes its value.
 *
 * ## Rendered, not read
 *
 * `user.context.tsx` is TSX and node's type stripping does not handle JSX, so the module cannot
 * be imported here — the same limit `bento-shell.test.mjs` hit. What *can* be rendered is the
 * arrangement: the package's provider with a consumer of its hook underneath, which is the half
 * that was broken. The rest is read out of the source, and the two together are what the
 * workaround's removal actually depends on.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const contextSource = stripComments(
  readFileSync(join(SRC, "contexts", "user.context.tsx"), "utf8"))
const shellSource = stripComments(
  readFileSync(join(SRC, "app", "bento", "bento.root.page.tsx"), "utf8"))

const USER = {
  username: "curator", firstName: "C", lastName: "Urator",
  email: "c@example.test", admin: false, privileges: ["POST_EDIT"],
}

test("SH920: a consumer of the package's hook is satisfied by the package's provider", () => {
  function Consumer() {
    const { user } = useVigCurrentUser()
    return h("span", null, user.username ?? "")
  }

  // The control: without a provider it throws, which is what the blank shell was.
  assert.throws(() => renderToStaticMarkup(h(Consumer)), /useCurrentUser/,
    "SH920: the package's hook no longer requires its provider. If that changed, the nesting in"
      + " user.context.tsx may be unnecessary — but check, because the symptom of getting it"
      + " wrong is an entirely blank page rather than an error anyone sees in a diff.")

  const html = renderToStaticMarkup(
    h(VigUserProvider, { fetchUser: () => Promise.resolve(USER) }, h(Consumer)))
  assert.ok(html.length > 0,
    "SH920: the package's provider did not render its children")
})

test("SH920: Shio's provider wraps the package's rather than declaring a second one", () => {
  assert.match(contextSource, /VigUserProvider/,
    "SH920: user.context.tsx no longer mounts the package's provider. Every shared component that"
      + " knows who is signed in reads the package's context, and a provider that does not mount"
      + " it leaves them all throwing — one blank surface per Block AR task.")
  assert.match(contextSource, /useVigCurrentUser\(\)/,
    "SH920: Shio's context value is no longer derived from the package's. If it fetches on its"
      + " own again, the two contexts hold two answers and the endpoint is asked twice.")

  // One fetch. The bridge exists so that mounting Shio's provider does not also mean mounting a
  // second source of truth beside it.
  assert.doesNotMatch(contextSource, /useEffect\(/,
    "SH920: user.context.tsx runs its own effect again, which means it is fetching rather than"
      + " republishing — two requests for the same user on every page load.")
  const fetches = [...contextSource.matchAll(/shUserService\.get\(\)/g)]
  assert.equal(fetches.length, 1,
    `SH920: shUserService.get() appears ${fetches.length} times; it must appear exactly once, as`
      + " the `fetchUser` handed to the package's provider.")
})

test("SH920: the bento shell no longer carries its own copy of the arrangement", () => {
  assert.match(shellSource, /import \{ UserProvider \} from "@\/contexts\/user\.context"/,
    "SH920: the bento shell mounts something other than Shio's provider. That was SH888's"
      + " workaround and the reason the two trees held different contexts; the whole point of"
      + " this task is that one import now serves both shells.")
  assert.doesNotMatch(shellSource, /fetchUser/,
    "SH920: the shell passes fetchUser again, so it is configuring the package's provider"
      + " directly — the knowledge this task moved into user.context.tsx has leaked back out,"
      + " and the next shell to be written will copy it.")
  assert.doesNotMatch(shellSource, /ShUserService/,
    "SH920: the shell reaches for the user service. A shell should not know how a user is"
      + " fetched.")
})

test("SH920: the reader is not vacuous — the arrangement it asserts is one that can fail", () => {
  // Nesting the package's provider inside something is not automatically enough: the consumer
  // has to be *below* it. This is the shape user.context.tsx uses, rendered.
  function Bridge({ children }) {
    const { user } = useVigCurrentUser()
    return h("div", { "data-user": user.username ?? "" }, children)
  }
  const html = renderToStaticMarkup(
    h(VigUserProvider, { fetchUser: () => Promise.resolve(USER) },
      h(Bridge, null, h("span", null, "child"))))

  assert.match(html, /data-user=/,
    "SH920: the bridge shape does not render, so the assertions above are describing an"
      + " arrangement that does not work")

  // And a bridge mounted OUTSIDE the provider must still throw — otherwise the ordering this
  // whole file is about would not matter and the check would pass on any arrangement.
  assert.throws(
    () => renderToStaticMarkup(h(Bridge, null, h("span", null, "child"))),
    /useCurrentUser/,
    "SH920: a consumer outside the provider did not throw, so provider order is unobservable"
      + " here and nothing above is being tested")
})
