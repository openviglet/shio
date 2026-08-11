/**
 * SH660 — telling a reference apart from a value a field happens to hold.
 *
 * ## The defect
 *
 * `file.widget.tsx` renders two different things with one component. On another post's field it
 * holds a **reference**: the content browser writes `selection.id`, and resolving it to a readable
 * path is the widget's whole job. On the `File` post-type's own `FILE` field it holds what the
 * uploader wrote — the **stored file name** (`ShStaticFileService` puts `fileName` there, and the
 * bytes live under the post's id).
 *
 * The resolver assumed the first, so opening any uploaded file in the console issued
 * `GET /v2/post/xp/<name>` and `GET /v2/folder/<name>`, both 404. Nothing reported it: each call
 * ends `.catch(() => null)`, the widget rendered its null branch, and the page returned 200. The
 * only witness was the network tab, which is why it took a probe that reads failed requests
 * (`console-check.mjs`) to see it at all.
 *
 * ## Why the shape and not the field
 *
 * The narrow alternative — "if this is the `File` type's `FILE` field, skip" — couples a generic
 * widget to one system post-type, and would be wrong for the next field that stores a name. What is
 * actually true is simpler and checkable: **a reference in this console is an object id, and an
 * object id is a UUID.** Anything else is a value the field holds, and looking it up is a request
 * that cannot succeed.
 *
 * Deliberately not "does it look like a filename": that is a guess about the value, and the guesses
 * fail on the cases nobody pictured. This asks about the only form a reference can take.
 */

/** An RFC 4122 id, which is what every object in this console is addressed by. */
const OBJECT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Whether this field value addresses another object, and can therefore be resolved.
 *
 * A value that is not one is not an error: it is a field holding its own content, and the caller's
 * job is to render it rather than to look it up.
 */
export function isObjectReference(value: string | null | undefined): boolean {
  return typeof value === "string" && OBJECT_ID.test(value.trim())
}
