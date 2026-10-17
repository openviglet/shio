import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow, LawChip } from "@/components/brand";
import { GUARDRAILS } from "@/lib/site-content";

/**
 * The objection every reader arrives with is "what stops it", not "can the
 * agent do it". Answering with the word governance is worth nothing, so this
 * section is six mechanisms and no adjectives. SH536 gives the curator's half a
 * page of its own.
 */
export function CuratorHalf() {
  return (
    <section
      id="curator"
      className="relative isolate overflow-hidden bg-[#150e0c] py-20 text-[#f6ece8]"
    >
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-b pointer-events-none absolute -bottom-40 left-1/4 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-25 blur-[80px]" />

      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>P7 &middot; the curator&rsquo;s half</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            An agent with write access to your site.{" "}
            <span className="text-gradient">What stops it?</span>
          </h2>
          <p className="mt-3 text-base leading-relaxed text-[#c9b6ae] sm:text-lg">
            Six mechanisms, not a policy document. Every one of them is on by
            default, and none of them is a setting an eager script can turn off on
            its way past.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {GUARDRAILS.map((g) => (
            <div
              key={g.title}
              className="rounded-xl border border-white/10 bg-white/[0.03] p-5 transition-colors hover:border-[#7c3aed]/35"
            >
              <div className="flex size-10 items-center justify-center rounded-lg bg-[#7c3aed]/12 text-[#c4b5fd]">
                <g.icon className="size-5" />
              </div>
              <h3 className="mt-4 font-bold">{g.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-[#a89189]">
                {g.description}
              </p>
            </div>
          ))}
        </div>

        <p className="mx-auto mt-10 max-w-3xl text-center text-sm leading-relaxed text-[#a89189]">
          <LawChip id="P7" tone="dark" /> And the boundary that is not a setting at
          all: Shio hosts no model, runs no prompts and holds no provider keys. There
          is no inference inside the content engine to misfire.
        </p>

        <div className="mt-8 flex justify-center">
          <Button
            asChild
            variant="outline"
            className="h-auto max-w-full border-white/20 bg-white/10 whitespace-normal px-5 py-2.5 text-center text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
          >
            <a href="/curator/">
              A curator&rsquo;s Tuesday, and the reason behind each gate
              <ArrowRight className="size-4" />
            </a>
          </Button>
        </div>
      </Container>
    </section>
  );
}
