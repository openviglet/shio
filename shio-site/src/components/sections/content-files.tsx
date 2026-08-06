import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { CONTENT_FILE, CONTENT_TREE } from "@/lib/site-content";

const ADDRESSES = [
  { form: "post:<site>/<friendly-url>", gloss: "a post — and post:acme/ is the home page" },
  { form: "folder:<site>/<name-chain>", gloss: "a folder, by the names a human reads" },
  { form: "site:<name>", gloss: "the site itself" },
  { form: "id:<uuid>", gloss: "when you already have the id — reads only" },
];

export function ContentFiles() {
  return (
    <section id="files" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="P4 + P5 — authoring"
          title={
            <>
              An agent's cheapest tools are{" "}
              <span className="text-primary">Edit, Write and Grep</span>
            </>
          }
          description="So the site is projected to disk. Pull it, edit it with the tools the agent already has, push it back — with a three-way merge underneath, because the instance moved while you were working."
        />

        <div className="grid items-start gap-6 lg:grid-cols-2">
          <CodeBlock caption="shio pull --content" code={CONTENT_TREE} highlight={false} />
          <div className="space-y-6">
            <CodeBlock caption="shio/content/acme/launch.md" code={CONTENT_FILE} />
            <div className="rounded-xl border border-border bg-card p-5">
              <h3 className="flex items-center gap-2 text-sm font-bold">
                One address grammar, everywhere <LawChip id="P5" />
              </h3>
              <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
                The same four forms in the CLI, over REST and over MCP. Writes take
                only the first two, because an id is not a place a human can name.
              </p>
              <dl className="mt-4 space-y-2.5">
                {ADDRESSES.map((a) => (
                  <div key={a.form} className="flex flex-col gap-0.5">
                    <dt className="font-mono text-xs font-semibold text-primary">{a.form}</dt>
                    <dd className="text-xs text-muted-foreground">{a.gloss}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
        </div>

        <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
          <LawChip id="P5" /> Stable ordering, path addressing, idempotent writes and
          byte-identical serialization &mdash; so pulling twice produces the same bytes,
          and a diff means somebody changed something.
        </p>
      </Container>
    </section>
  );
}
