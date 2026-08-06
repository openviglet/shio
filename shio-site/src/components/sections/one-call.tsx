import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { MEASURES } from "@/lib/site-content";

const CONTEXT_CALL = `# One call. The model, the sitemap, the conventions,
# and the write vocabulary — with a digest to skip it next time.

GET /api/v2/agent/context?include=all,manifest,ops

# Or, from a client that only speaks MCP:
shio://context`;

/** The widest bar sets the scale — a chart nobody can misread as a percentage. */
const MAX = Math.max(...MEASURES.map((m) => m.tokens));

export function OneCall() {
  return (
    <section id="one-call" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="P2 + P3 — discovery and its price"
          title={
            <>
              Discovery is a feature,{" "}
              <span className="text-primary">and it has a price tag</span>
            </>
          }
          description="Most CMSs make an agent crawl a REST surface to learn what exists. Shio answers that in one call — and the cost of every answer is a number the build enforces."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[0.95fr_1.05fr]">
          <div>
            <CodeBlock caption="the whole discovery session" code={CONTEXT_CALL} />
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
              The response carries a <span className="font-mono text-foreground">digest</span>.
              Session N+1 sends it back and is told nothing changed, which turns
              "re-read the context" from a decision an agent has to make into a free
              default. <LawChip id="P2" />
            </p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-6">
            <h3 className="text-sm font-bold">What a call costs, in tokens</h3>
            <p className="mt-1 text-xs text-muted-foreground">
              Each figure has a ceiling in the test suite. A response that grows past
              it fails the build the same way a broken test does.
            </p>

            <ul className="mt-6 space-y-5">
              {MEASURES.map((m) => (
                <li key={m.label}>
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-sm font-semibold">{m.label}</span>
                    <span className="font-mono text-sm font-bold text-primary tabular-nums">
                      {m.tokens.toLocaleString("en-US")}
                    </span>
                  </div>
                  <div
                    className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-muted"
                    role="img"
                    aria-label={`${m.label}: ${m.tokens} tokens`}
                  >
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-[#d94328] to-[#f59e0b]"
                      style={{ width: `${Math.max(2, (m.tokens / MAX) * 100)}%` }}
                    />
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">{m.note}</p>
                </li>
              ))}
            </ul>

            <p className="mt-6 border-t border-border pt-4 text-xs leading-relaxed text-muted-foreground">
              <LawChip id="P3" /> A terse response that omits something the agent has
              to ask for twice is a regression, not a win &mdash; so the benchmark
              measures the whole task, not the single response.
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
