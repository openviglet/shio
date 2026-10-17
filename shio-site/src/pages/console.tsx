import { ArrowRight, Check, TerminalSquare } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { ConsoleSession } from "@/components/console-session";
import { Container, Eyebrow, SectionHeader } from "@/components/brand";
import { Button } from "@/components/ui/button";
import {
  AGENT_LATER,
  CONSOLE_CAPABILITIES,
  CONSOLE_LIMITS,
  LINKS,
} from "@/lib/site-content";

/**
 * The page the site was missing, and the reason the rest of it was being
 * misread.
 *
 * Everything else here argues about protocol quality, which is what makes the
 * product unusual. This page answers a much earlier question ("is this even a
 * CMS I could hand to my team?"), and it answers it by walking the console
 * directory: every capability listed is a screen in `shio-react/src/app/`, and
 * the four that are not get a section of their own rather than a
 * silence. A page that only claimed the good half would be the same mistake in
 * the opposite direction.
 */
export function ConsolePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Viglet Shio without an agent"
          title={
            <>
              It is a CMS first.{" "}
              <span className="text-gradient">The agent is optional.</span>
            </>
          }
          lead={
            <>
              Create the site, shape the content model, write the pages, upload the
              media, publish or schedule it, and add the rest of the team. All in a
              browser, with no terminal, no YAML and no agent anywhere in the
              picture. Nothing on this page needs a capability the console does not
              have.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#tour">
                Six screens, one page live <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#not-here">What the console does not do</a>
            </Button>
          </div>
        </PageHero>

        {/* ── The tour ─────────────────────────────────────────────────── */}
        <section id="tour" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Day one"
              title={
                <>
                  From an empty instance to a published page,{" "}
                  <span className="text-primary">in six screens</span>
                </>
              }
              description="The landing page shows a coding agent doing this same job in six calls. It is the same content model, the same drafts and the same publish act, which is the whole point of showing both."
            />

            <div className="mx-auto max-w-2xl">
              <ConsoleSession />
            </div>

            <p className="mx-auto mt-6 max-w-2xl text-center text-sm leading-relaxed text-muted-foreground">
              Note where the draft appears. A person's write lands in{" "}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">DRAFT</code>{" "}
              exactly as an agent's does, and publishing is a separate, deliberate act
              in both cases.
            </p>
          </Container>
        </section>

        {/* ── What is in the console ───────────────────────────────────── */}
        <section id="capabilities" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="In the browser"
              title="The ordinary job, in full"
              description="Six groups, and every line is a screen in the console rather than a plan. If your team has used a CMS before, none of this will need explaining."
            />

            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {CONSOLE_CAPABILITIES.map((group) => (
                <div key={group.title} className="rounded-2xl border border-border bg-card p-6">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <group.icon className="size-5" />
                  </div>
                  <h3 className="mt-4 font-bold">{group.title}</h3>
                  <ul className="mt-3 space-y-2">
                    {group.items.map((item) => (
                      <li key={item} className="flex gap-2 text-sm leading-relaxed text-muted-foreground">
                        <Check className="mt-0.5 size-4 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </Container>
        </section>

        {/* ── And what is not ──────────────────────────────────────────── */}
        <section id="not-here" className="py-20">
          <Container className="max-w-3xl">
            <div className="mb-10 text-center">
              <Eyebrow>The limits</Eyebrow>
              <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
                Four things that live only{" "}
                <span className="text-primary">on the command line</span>
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
                Each one needs a shell or a browser the instance deliberately does not
                run. None of them is required to operate the CMS.
              </p>
            </div>

            <ol className="space-y-4">
              {CONSOLE_LIMITS.map((limit, i) => (
                <li
                  key={limit.title}
                  className="flex gap-4 rounded-xl border border-border bg-card p-5"
                >
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted font-mono text-sm font-bold text-muted-foreground">
                    {i + 1}
                  </span>
                  <div className="min-w-0">
                    <h3 className="font-bold">{limit.title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                      {limit.body}
                    </p>
                  </div>
                </li>
              ))}
            </ol>

            <p className="mt-8 flex items-start gap-3 rounded-xl border border-border bg-muted/50 p-5 text-sm leading-relaxed text-muted-foreground">
              <TerminalSquare className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
              <span>
                The CLI is <code className="rounded bg-background px-1 py-0.5 font-mono text-xs">npx @viglet/shio</code>{" "}
                and it talks to the same instance over the same API. It is an extra
                door, not a dependency: an instance nobody ever runs it against behaves
                identically.
              </span>
            </p>
          </Container>
        </section>

        {/* ── Adding an agent later ────────────────────────────────────── */}
        <section
          id="agent-later"
          className="relative isolate overflow-hidden bg-[#150e0c] py-20 text-[#f6ece8]"
        >
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="animate-orb-b pointer-events-none absolute -bottom-40 left-1/4 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-25 blur-[80px]" />

          <Container>
            <div className="mx-auto mb-12 max-w-2xl text-center">
              <Eyebrow>Later, if you want it</Eyebrow>
              <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
                What changes the day you{" "}
                <span className="text-gradient">attach an agent</span>
              </h2>
              <p className="mt-3 text-base leading-relaxed text-[#c9b6ae] sm:text-lg">
                Less than you would expect, which is the argument for starting without
                one. No migration, no second store, no content to move.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              {AGENT_LATER.map((item) => (
                <div
                  key={item.title}
                  className="rounded-xl border border-white/10 bg-white/[0.03] p-5"
                >
                  <h3 className="font-bold">{item.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-[#a89189]">{item.body}</p>
                </div>
              ))}
            </div>

            <div className="mt-10 flex flex-wrap justify-center gap-3">
              <Button asChild size="lg">
                <a href="/curator/">
                  What stops an agent write <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
              >
                <a href="/claude-code/">
                  <TerminalSquare className="size-4" />
                  Attach one in a command
                </a>
              </Button>
            </div>
          </Container>
        </section>

        {/* ── Where to go next ─────────────────────────────────────────── */}
        <section className="py-20">
          <Container className="max-w-3xl text-center">
            <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
              Run it, and see whether the console is enough for you
            </h2>
            <p className="mt-3 text-base leading-relaxed text-muted-foreground">
              One container and a database. The installation guide is the same one
              everybody uses, and there is no hosted tier you are being funnelled
              towards.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Button asChild size="lg">
                <a href={LINKS.docs}>
                  Install it <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button asChild size="lg" variant="outline">
                <a href="/#self-host">The self-host section</a>
              </Button>
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
