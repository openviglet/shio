import { Container, SectionHeader } from "@/components/brand";
import { PATHS, USE_CASES } from "@/lib/site-content";

const PATH_LABEL: Record<string, string> = Object.fromEntries(
  PATHS.map((p) => [p.id, p.tag])
);

/**
 * Six deployments, each tagged with the doors it actually walks through.
 *
 * The tags are read out of `PATHS` rather than written again here, so a path
 * that is renamed cannot leave a scenario labelled with a name that no longer
 * exists. It is the same rule the rest of this site's copy is held to, applied to the
 * one place where two lists could silently disagree.
 */
export function UseCases() {
  return (
    <section id="use-cases" className="bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Who runs it, and how"
          title={
            <>
              Six shapes a real deployment takes.{" "}
              <span className="text-primary">Only three involve an agent.</span>
            </>
          }
          description="The first one is the case a headline about coding agents accidentally argues you out of, so it goes first: a team that wants a CMS, and gets one."
        />

        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {USE_CASES.map((uc) => (
            <article
              key={uc.title}
              className="flex flex-col rounded-2xl border border-border bg-card p-6"
            >
              <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <uc.icon className="size-5" />
              </div>
              <h3 className="mt-4 font-bold">{uc.title}</h3>
              <p className="mt-1 text-xs font-medium text-muted-foreground/90">{uc.who}</p>
              <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                {uc.body}
              </p>
              <ul className="mt-4 flex flex-wrap gap-1.5">
                {uc.paths.map((id) => (
                  <li
                    key={id}
                    className="rounded-md border border-primary/25 bg-primary/5 px-2 py-0.5 font-mono text-[0.68rem] font-semibold text-primary"
                  >
                    {PATH_LABEL[id] ?? id}
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </Container>
    </section>
  );
}
