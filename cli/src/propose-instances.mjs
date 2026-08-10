/**
 * Modelling by example, pointed inward (SH525).
 *
 * `propose` derives a post-type from markup nobody wrote for Shio: on `viglet.org` it offered
 * `BlockBuildTheFuture` with `heading:text, body:htmlEditor, image:file, items:relator`, inferred
 * from one page's structure. That is data modelling by example, it works, and it existed **only
 * inside conversion** — pointed at a stranger's site.
 *
 * This points it at your own content. Three product pages written as content, and "what post-type
 * are these three instances of" has the same answer computed the same way. The capability was
 * built; only its input was hard-wired to a capture, which is why this is a second reader over the
 * same idea rather than a second idea.
 *
 * <h2>Why it is worth having at all</h2>
 *
 * Writing a post-type from nothing means naming fields before knowing what they hold, which is the
 * wrong order — a model is easier to see once three instances exist and disagree. The only route
 * before this was to author blind, discover the shape, then rewrite the type and migrate what
 * already used it.
 *
 * <h2>Two things the conversion version does not need</h2>
 *
 * **Which instances disagree.** A field two of three share is a different proposal from one all
 * three do, and the disagreement is the interesting part — it is the difference between a required
 * field, an optional one, and a field that belongs to a different type altogether. So every
 * proposed field carries the count and the addresses that lack it.
 *
 * **The honest limit, in the report.** Three instances of a thing are evidence about those three.
 * This is a proposal, not a conclusion — which is what `propose` already gets right, and the
 * property most easily lost when the input stops being a stranger's site and starts being content
 * the reader wrote.
 */

/** Below this share of instances, a field reads as belonging to some of them and not the type. */
const SHARED = 0.5;

/** A string longer than this is prose rather than a label, whatever it happens to contain. */
const PROSE = 120;

/**
 * The `@viglet/shio-model` field kind a stored value implies.
 *
 * The same vocabulary `proposePostType` infers from markup, so a type proposed from instances and
 * one proposed from a capture are the same kind of artefact and can be compared. Ordered from the
 * most specific signal to the least: a shape that is unmistakable (an array, a boolean) decides
 * before a length heuristic gets a say.
 */
export function kindOf(value) {
  if (Array.isArray(value)) return "relator";
  if (typeof value === "boolean") return "checkBox";
  if (typeof value === "number") return "text";
  if (value && typeof value === "object") return "relator";
  const text = String(value ?? "");
  // A stored file reference is a path into the library, and the console needs a File widget to
  // edit it — getting this wrong is the difference between a picker and a text box holding a path.
  if (/^\/?(assets|file_source)\//.test(text) || /\.(png|jpe?g|gif|svg|webp|avif|pdf)$/i.test(text)) {
    return "file";
  }
  if (/<\/?[a-z][\s\S]*>/i.test(text)) return "htmlEditor";
  if (/^\d{4}-\d{2}-\d{2}([T ]|$)/.test(text)) return "date";
  if (text.length > PROSE) return "textArea";
  return "text";
}

/**
 * Whether a stored value counts as present.
 *
 * An empty string is **absent**, deliberately: a field every instance declares and none fills is
 * not evidence of a field, it is evidence of a form control somebody left blank. Counting it would
 * make the disagreement — the part this proposal exists for — read as agreement.
 */
const present = (value) =>
  value !== undefined && value !== null
  && !(typeof value === "string" && value.trim() === "")
  && !(Array.isArray(value) && value.length === 0);

/**
 * The post-type these instances look like they are of (SH525).
 *
 * @param instances `{ address, data }` — the posts to model from
 * @param name      what to call the proposed type
 * @returns a proposal, or `null` when nothing can be derived
 */
export function proposeFromInstances(instances, name) {
  const rows = (instances ?? []).filter((row) => row && row.data && typeof row.data === "object");
  if (rows.length === 0) return null;

  const keys = [];
  for (const row of rows) {
    for (const key of Object.keys(row.data)) if (!keys.includes(key)) keys.push(key);
  }

  const fields = keys.sort().map((key) => {
    const held = rows.filter((row) => present(row.data[key]));
    const missing = rows.filter((row) => !present(row.data[key])).map((row) => row.address);
    // The kind is decided by the values that EXIST. A field two instances fill and one does not is
    // still whatever those two hold; letting an absent value vote would make every partly-filled
    // field a `text`.
    const kinds = held.map((row) => kindOf(row.data[key]));
    return {
      name: key,
      kind: commonest(kinds) ?? "text",
      in: held.length,
      of: rows.length,
      // Required only when every instance has it — the strongest claim these three can support,
      // and the one a reader can check by looking at the `missing` list when it is not made.
      required: held.length === rows.length,
      missing,
      // A field a minority carries is reported and NOT proposed as part of the type: it is more
      // likely to be a second type's field than an optional one, and a proposal that swept it in
      // would produce exactly the over-wide type this verb exists to avoid.
      shared: held.length / rows.length >= SHARED,
    };
  });

  const proposed = fields.filter((field) => field.shared);
  if (proposed.length === 0) return null;

  return {
    name,
    instances: rows.map((row) => row.address),
    fields: proposed,
    // Kept, not dropped: "these two instances carry something the others do not" is a finding about
    // the model, and hiding it would make the proposal look more settled than the evidence is.
    outliers: fields.filter((field) => !field.shared),
  };
}

function commonest(values) {
  const counts = new Map();
  for (const value of values) counts.set(value, (counts.get(value) ?? 0) + 1);
  let best = null;
  let bestCount = 0;
  // Sorted so a tie resolves the same way every run (P5) rather than by insertion order.
  for (const value of [...counts.keys()].sort()) {
    if (counts.get(value) > bestCount) {
      best = value;
      bestCount = counts.get(value);
    }
  }
  return best;
}

/**
 * The proposal as a reader sees it.
 *
 * The limit is printed rather than implied: three instances of a thing are evidence about those
 * three, and a report that reads like a conclusion is one somebody applies without looking.
 */
export function describeInstanceProposal(proposal) {
  if (!proposal) {
    return "Nothing to model: no instance carried a field that a majority of them share.\n";
  }
  const lines = [
    `${proposal.name} — proposed from ${proposal.instances.length} instance(s)`,
    "",
  ];
  for (const field of proposal.fields) {
    const where = field.required
      ? "all"
      : `${field.in}/${field.of}, missing from ${field.missing.join(", ")}`;
    lines.push(`  ${field.name}: ${field.kind}${field.required ? " (required)" : ""}  [${where}]`);
  }
  if (proposal.outliers.length) {
    lines.push("", "  Not proposed — carried by a minority, so more likely another type's field:");
    for (const field of proposal.outliers) {
      lines.push(`    ${field.name}: ${field.kind}  [${field.in}/${field.of}]`);
    }
  }
  lines.push(
    "",
    `A proposal, not a conclusion: ${proposal.instances.length} instance(s) are evidence about `
      + `those ${proposal.instances.length}. Check it against one you did not include.`,
    "",
  );
  return lines.join("\n");
}
