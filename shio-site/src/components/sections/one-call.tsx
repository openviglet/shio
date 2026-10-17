import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { TOKEN_BUDGETS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

// Kept under ~52 columns: this renders in a half-width figure that scrolls
// rather than wraps, and a line past the fold reads as truncated.
const CONTEXT_CALL = `# One call. The model, the sitemap,
# the conventions and the write vocabulary,
# plus a digest to skip it next time.

GET /api/v2/agent/context?include=all,manifest,ops

# Or, from a client that only speaks MCP:
shio://context`;

/** The widest bar sets the scale, so it is a chart nobody can misread as a percentage. */
const MAX = Math.max(...TOKEN_BUDGETS.ceilings.map((c) => c.tokens));

export function OneCall() {
  return (
    <section id="one-call" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="P2 + P3 &middot; discovery and its price"
          title={
            <>
              Discovery is a feature,{" "}
              <span className="text-primary">and it has a price</span>
            </>
          }
          description="Most CMSs make an agent crawl a REST surface to learn what exists. Shio answers that in one call, and the cost of every answer is a ceiling the build refuses to exceed."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[0.95fr_1.05fr] [&>*]:min-w-0">
          <div>
            <CodeBlock caption="the whole discovery session" code={CONTEXT_CALL} />
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
              The response carries a <span className="font-mono text-foreground">digest</span>.
              Session N+1 sends it back and is told nothing changed, which turns
              "re-read the context" from a decision an agent has to make into a free
              default. <LawChip id="P2" />
            </p>

            {/* The P2 frame: one call against the session it replaces. */}
            <div className="mt-6 rounded-2xl border border-border bg-card p-6">
              <h3 className="text-sm font-bold">
                The same job, over an API designed for a browser
              </h3>
              <p className="mt-1 text-xs text-muted-foreground">
                Each task is benchmarked twice: once on the agent surface, once on the
                id-keyed REST path an agent would otherwise have to drive. The build
                asserts the agent path stays <em>at least</em> this much cheaper.
              </p>
              {/* Two header rows rather than one: a single "vs REST" column
                  printing "3x / 2x" makes the reader go to a footnote to learn
                  which number is which, and a table that needs a key is a table
                  that is not doing its job. */}
              <table className="mt-4 w-full text-sm">
                <thead>
                  <tr className="text-[0.65rem] tracking-wide text-muted-foreground/70 uppercase">
                    <th />
                    <th className="pb-1 text-center font-semibold" colSpan={2}>
                      On the agent surface
                    </th>
                    <th
                      className="pb-1 text-center font-semibold text-primary/70"
                      colSpan={2}
                    >
                      Cheaper than REST by
                    </th>
                  </tr>
                  <tr className="border-b border-border text-xs text-muted-foreground">
                    <th className="pb-2 text-left font-semibold">Task</th>
                    <th className="pb-2 text-right font-semibold">Calls</th>
                    <th className="pb-2 text-right font-semibold">Tokens</th>
                    <th className="pb-2 text-right font-semibold">Calls</th>
                    <th className="pb-2 text-right font-semibold">Tokens</th>
                  </tr>
                </thead>
                <tbody>
                  {TOKEN_BUDGETS.tasks.map((t) => (
                    <tr key={t.label} className="border-b border-border/60 last:border-0">
                      <td className="py-2 pr-3 font-medium">{t.label}</td>
                      <td className="py-2 text-right font-mono tabular-nums">{t.calls}</td>
                      <td className="py-2 pr-3 text-right font-mono tabular-nums">
                        {t.tokens.toLocaleString("en-US")}
                      </td>
                      <td className="py-2 text-right font-mono font-bold text-primary tabular-nums">
                        {t.callRatio}&times;
                      </td>
                      <td className="py-2 text-right font-mono font-bold text-primary tabular-nums">
                        {t.tokenRatio}&times;
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
                Those last two are floors the suite enforces, not the best number
                anyone has seen. A falling ratio means the baseline improved or
                the protocol regressed, and both are worth knowing.
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-border bg-card p-6">
            <h3 className="text-sm font-bold">The ceiling on what a call may cost</h3>
            <p className="mt-1 text-xs text-muted-foreground">
              Not a best case: a limit. A response that grows past one of these
              fails the build the same way a broken test does.
            </p>

            <ul className="mt-6 space-y-5">
              {TOKEN_BUDGETS.ceilings.map((c) => (
                <li key={c.key}>
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-sm font-semibold">{c.label}</span>
                    <span className="font-mono text-sm font-bold text-primary tabular-nums">
                      {c.tokens.toLocaleString("en-US")}
                    </span>
                  </div>
                  <div
                    className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-muted"
                    role="img"
                    aria-label={`${c.label}: a ceiling of ${c.tokens} tokens`}
                  >
                    <div
                      className={cn("h-full rounded-full bg-gradient-to-r from-[#5b21b6] to-[#f59e0b]")}
                      style={{ width: `${Math.max(2, (c.tokens / MAX) * 100)}%` }}
                    />
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">{c.note}</p>
                  <p className="mt-0.5 font-mono text-[0.65rem] text-muted-foreground/70">
                    {c.key}
                  </p>
                </li>
              ))}
            </ul>

            <p className="mt-6 border-t border-border pt-4 text-xs leading-relaxed text-muted-foreground">
              <LawChip id="P3" /> Every number on this page is generated at build time
              from{" "}
              <span className="font-mono text-foreground">{TOKEN_BUDGETS.source}</span>,
              the one file the test suite asserts against. A renamed key fails
              this build rather than quietly dropping a bar, because a figure on a
              marketing page that has drifted from the build is worse than no figure.
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
