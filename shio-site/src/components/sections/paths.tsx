import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { PATHS } from "@/lib/site-content";

/**
 * The four doors, directly under the hero: the section the rest of the page is
 * now read through.
 *
 * It sits above the roles, the laws and every depth section on purpose. A
 * visitor deciding whether this product is for them makes that decision in the
 * first screenful, and the answer they used to get was "only if you have an
 * agent". Four cards, each naming its audience and its honest prerequisite, is
 * the shortest correction that is also true.
 */
export function Paths() {
  return (
    <section id="paths" className="border-b border-border py-20">
      <Container>
        <SectionHeader
          eyebrow="Four ways in"
          title={
            <>
              Pick the way you already work.{" "}
              <span className="text-primary">It is the same CMS underneath.</span>
            </>
          }
          description="These are not tiers, and not a migration path you commit to once. They are four surfaces over one content engine (the same posts, the same drafts, the same publish act), and a site can be worked on through all four in the same week."
        />

        <div className="grid gap-5 md:grid-cols-2">
          {PATHS.map((path) => (
            <div
              key={path.id}
              className="flex flex-col rounded-2xl border border-border bg-card p-6 transition-colors hover:border-primary/40"
            >
              <div className="flex items-start gap-4">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <path.icon className="size-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="text-lg font-bold">{path.label}</h3>
                  <p className="mt-0.5 text-sm text-muted-foreground">{path.audience}</p>
                </div>
              </div>

              <p className="mt-4 flex-1 text-sm leading-relaxed text-muted-foreground">
                {path.body}
              </p>

              <p className="mt-4 text-xs font-semibold text-foreground">
                What it takes:{" "}
                <span className="font-normal text-muted-foreground">{path.needs}</span>
              </p>

              <ul className="mt-3 flex flex-wrap gap-1.5">
                {path.surface.map((s) => (
                  <li
                    key={s}
                    className="rounded-md border border-border bg-muted px-2 py-0.5 font-mono text-[0.7rem] font-medium text-muted-foreground"
                  >
                    {s}
                  </li>
                ))}
              </ul>

              <a
                href={path.start.href}
                className="group mt-5 inline-flex items-center gap-1 text-sm font-semibold text-primary"
              >
                {path.start.label}
                <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
              </a>
            </div>
          ))}
        </div>

        <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
          The design laws below say every capability lands on the agent surface first.
          That is a rule about the order things are <em>built</em> in, so nothing ends up
          reachable only by a mouse. It has never been a rule about who is allowed
          to operate the CMS.
        </p>
      </Container>
    </section>
  );
}
