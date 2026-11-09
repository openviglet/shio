import { ArrowUpRight } from "lucide-react";
import { Container, Eyebrow } from "@/components/brand";
import { LAWS, LINKS } from "@/lib/site-content";

/**
 * The ten design laws, verbatim from `agents.md`. This is the most distinctive
 * content the product has and the reason the rest of the page can be an
 * argument rather than a feature list: each law that has a section below links
 * to the place it is shown instead of stated.
 */
export function Laws() {
  return (
    <section id="laws" className="border-y border-border bg-muted/40 py-20 text-foreground">

      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>The contract</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Ten laws. A feature that breaks one is{" "}
            <span className="text-primary">wrong even if requested</span>.
          </h2>
          <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
            They are checked into the repository as the rule every contributor and
            every agent works under, and each one below has a section on this page
            that shows it at work.
          </p>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
            They govern how the product is <em>built</em>: what a feature must
            reach before it can ship. None of them is a condition on how you use it: a
            team that never attaches an agent gets every capability the ten laws
            forced into existence.
          </p>
        </div>

        <ol className="grid gap-3 sm:grid-cols-2 lg:grid-cols-2">
          {LAWS.map((law) => {
            const Wrapper = law.anchor ? "a" : "div";
            return (
              <li key={law.id}>
                <Wrapper
                  {...(law.anchor ? { href: law.anchor } : {})}
                  className="group flex h-full gap-4 rounded-xl border border-border bg-background p-5 transition-colors hover:border-[#7c3aed]/40 hover:bg-muted/60"
                >
                  <span className="mt-0.5 shrink-0 self-start rounded-lg border border-[#7c3aed]/35 bg-[#7c3aed]/12 px-2 py-1 font-mono text-sm font-black text-primary">
                    {law.id}
                  </span>
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5 font-bold">
                      {law.title}
                      {law.anchor && (
                        <ArrowUpRight className="size-3.5 shrink-0 text-muted-foreground transition-colors group-hover:text-primary" />
                      )}
                    </span>
                    <span className="mt-1 block text-sm leading-relaxed text-muted-foreground">
                      {law.gloss}
                    </span>
                  </span>
                </Wrapper>
              </li>
            );
          })}
        </ol>

        <p className="mt-8 text-center font-mono text-xs text-muted-foreground">
          <a href={LINKS.laws} className="underline underline-offset-4 hover:text-foreground">
            agents.md
          </a>{" "}
          &middot;{" "}
          <a href={LINKS.spec} className="underline underline-offset-4 hover:text-foreground">
            the concept spec (SH74)
          </a>
        </p>
      </Container>
    </section>
  );
}
