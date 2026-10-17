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
    <section id="laws" className="relative isolate overflow-hidden bg-[#150e0c] py-20 text-[#f6ece8]">
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />

      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>The contract</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Ten laws. A feature that breaks one is{" "}
            <span className="text-gradient">wrong even if requested</span>.
          </h2>
          <p className="mt-3 text-base leading-relaxed text-[#c9b6ae] sm:text-lg">
            They are checked into the repository as the rule every contributor and
            every agent works under, and each one below has a section on this page
            that shows it at work.
          </p>
          <p className="mt-3 text-sm leading-relaxed text-[#a89189]">
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
                  className="group flex h-full gap-4 rounded-xl border border-white/10 bg-white/[0.03] p-5 transition-colors hover:border-[#7c3aed]/40 hover:bg-white/[0.06]"
                >
                  <span className="mt-0.5 shrink-0 self-start rounded-lg border border-[#7c3aed]/35 bg-[#7c3aed]/12 px-2 py-1 font-mono text-sm font-black text-[#c4b5fd]">
                    {law.id}
                  </span>
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5 font-bold">
                      {law.title}
                      {law.anchor && (
                        <ArrowUpRight className="size-3.5 shrink-0 text-[#8d7b74] transition-colors group-hover:text-[#c4b5fd]" />
                      )}
                    </span>
                    <span className="mt-1 block text-sm leading-relaxed text-[#a89189]">
                      {law.gloss}
                    </span>
                  </span>
                </Wrapper>
              </li>
            );
          })}
        </ol>

        <p className="mt-8 text-center font-mono text-xs text-[#8d7b74]">
          <a href={LINKS.laws} className="underline underline-offset-4 hover:text-[#f6ece8]">
            agents.md
          </a>{" "}
          &middot;{" "}
          <a href={LINKS.spec} className="underline underline-offset-4 hover:text-[#f6ece8]">
            the concept spec (SH74)
          </a>
        </p>
      </Container>
    </section>
  );
}
