import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CLI_VERBS, LINKS, MCP_RESOURCES, MCP_TOOLS } from "@/lib/site-content";

export function Surface() {
  return (
    <section
      id="surface"
      className="relative isolate overflow-hidden border-y border-border bg-muted/40 py-20"
    >
      <Container>
        <SectionHeader
          eyebrow="P1 + P10 &middot; the surface"
          title={
            <>
              Eleven tools, six resources,{" "}
              <span className="text-primary">and no second contract</span>
            </>
          }
          description="MCP is a shape over the delivery API and the console services, never a parallel semantic. Whatever the agent can do here it can also do from the CLI and over REST, in that order, by law."
        />

        <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr] [&>*]:min-w-0">
          <div className="rounded-2xl border border-border bg-card p-6">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="font-mono text-sm font-bold">POST /mcp</h3>
              <span className="font-mono text-xs text-muted-foreground">
                JSON-RPC 2.0 &middot; revision 2025-06-18
              </span>
            </div>

            <ul className="mt-5 grid gap-2 sm:grid-cols-2">
              {MCP_TOOLS.map((t) => (
                <li
                  key={t.name}
                  className="rounded-lg border border-border bg-background/60 px-3 py-2"
                >
                  <p className="font-mono text-xs font-bold text-primary">{t.name}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{t.gloss}</p>
                </li>
              ))}
            </ul>

            <h4 className="mt-6 text-xs font-bold tracking-wide text-muted-foreground uppercase">
              Resources
            </h4>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {MCP_RESOURCES.map((r) => (
                <li
                  key={r}
                  className="rounded-md border border-border bg-background/60 px-2 py-1 font-mono text-[0.7rem] text-foreground"
                >
                  {r}
                </li>
              ))}
            </ul>

            <p className="mt-5 border-t border-border pt-4 text-xs leading-relaxed text-muted-foreground">
              <LawChip id="P10" /> Hand-rolled rather than pulled from a vendor SDK,
              and the reason is written down with a date on it, and the premise is
              re-checked on a schedule and a test fails the build when it expires.
              <span className="ml-1">
                <a
                  href={LINKS.mcpSpec}
                  className="underline underline-offset-4 hover:text-foreground"
                >
                  modelcontextprotocol.io
                </a>
              </span>
            </p>
          </div>

          <div className="rounded-2xl border border-border bg-card p-6">
            <h3 className="font-mono text-sm font-bold">
              $ shio <span className="text-muted-foreground">&lt;verb&gt;</span>
            </h3>
            <p className="mt-1 text-xs text-muted-foreground">
              The same capabilities where an agent already has a shell.
            </p>
            <dl className="mt-5 space-y-3">
              {CLI_VERBS.map((v) => (
                <div key={v.verb}>
                  <dt className="font-mono text-xs font-bold text-primary">{v.verb}</dt>
                  <dd className="mt-0.5 text-xs text-muted-foreground">{v.gloss}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-5 border-t border-border pt-4 text-xs leading-relaxed text-muted-foreground">
              <LawChip id="P1" /> A capability the console has and the agent does not
              is an unshipped feature. Where something lives elsewhere, the
              manifest says so by name instead of reporting{" "}
              <span className="font-mono">false</span> and letting a client guess.
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
