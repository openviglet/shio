/**
 * The post-type authoring shape — the console's mirror of the backend
 * `ShPostTypeDef` / `ShPostTypeAttrDef` (SH16).
 *
 * **SH117 collapsed two shapes into this one.** The console used to read a
 * different post-type from a different endpoint: the `ShPostType` JPA entity off
 * the id-keyed `/api/v2/post/type`, with `shPostTypeAttrs`, a `shWidget: {id,
 * name}` object per field and `0`/`1` flags. The CLI and the agent read
 * `ShPostTypeDef` off the name-keyed `/api/v2/post-type`, with `fields`, a plain
 * `widget` name and real booleans. One concept, two shapes, and every caller had
 * to know which — the tax P5/P10 exist to refuse. The entity shape is gone; this
 * is the shape.
 *
 * A post-type is addressed by its API `name` (SH79's rule for everything else),
 * which is why there is no `id` here. Nothing in the console needs one: the
 * editor routes on the name, and `POST /api/v2/post-unified` takes `postType` by
 * name.
 */
export type ShPostType = {
  name: string;
  title?: string;
  namePlural?: string;
  description?: string;
  workflowPublishEntity?: string;
  /** Read-only platform type — not editable or deletable over the API. */
  system?: boolean;
  /**
   * Owned by code (SH66). Delivered on every read; on a **write** it is a claim
   * about the writer, so the console always sends `false` — a console save is a
   * hand edit by definition, and the server refuses one against a managed type
   * rather than racing the repository. Omitting the key means the opposite
   * ("code-owned"), so it must be sent, not defaulted.
   */
  managed?: boolean;
  fields?: ShPostTypeField[];
};

/** One field of the content model. Recursive: a Relator/Tab carries its children in `fields`. */
export type ShPostTypeField = {
  name: string;
  label?: string;
  description?: string;
  /** The widget name (e.g. `"Text"`, `"HTML Editor"`) — the catalogue is a closed set. */
  widget?: string;
  required?: boolean;
  isSummary?: boolean;
  isTitle?: boolean;
  ordinal: number;
  /** Verbatim widget-settings JSON, kept as a string so a round trip is lossless. */
  widgetSettings?: string;
  fields?: ShPostTypeField[];
};

/**
 * What a rename moved (SH163) — the response of `POST /v2/post-type/{name}/rename`.
 *
 * It reports rather than just succeeding, because the workaround it replaces (a `PUT` to a new
 * name) also succeeded and moved nothing: the posts stayed on the original type. `posts` is how
 * many rows came along, `referencesUpdated` names the `Multi Select` fields that were narrowing
 * to the old name, and `notes` says what the server cannot rename — a project's content files
 * and documents address a post-type by name too.
 */
export type ShPostTypeRenamed = {
  from: string;
  to: string;
  posts: number;
  referencesUpdated: string[];
  notes: string[];
};
