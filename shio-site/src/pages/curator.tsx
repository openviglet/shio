import { ArrowRight, MousePointerClick } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { Container, Eyebrow, LawChip, SectionHeader } from "@/components/brand";
import { Button } from "@/components/ui/button";
import { CURATOR_DAY, GATES, LIFECYCLE, ROLES } from "@/lib/site-content";

/**
 * SH536: the site showed only the agent, which reads as a CMS with no editor.
 *
 * Written for the person who will be asked to approve the agent's work, not for
 * the engineer who wires it up: a Tuesday first, mechanisms second, and the
 * product's three-role shape last, in plain words. The objection this page
 * answers is "what stops it", never "can the agent do it".
 */
export function CuratorPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="The curator’s half"
          title={
            <>
              An agent wrote nine pages last night.{" "}
              <span className="text-gradient">Nobody has seen them yet.</span>
            </>
          }
          lead={
            <>
              In Viglet Shio a coding agent can do the work while a person decides
              what readers see. What enforces that is a set of mechanisms, on by
              default, that an eager script cannot switch off on its way past.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#day">
                A curator's Tuesday <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#gates">The six gates</a>
            </Button>
          </div>
        </PageHero>

        {/* ── A day ───────────────────────────────────────────────────── */}
        <section id="day" className="py-20">
          <Container className="max-w-3xl">
            <SectionHeader
              eyebrow="What it looks like"
              title={
                <>
                  Half an hour on a{" "}
                  <span className="text-primary">Tuesday morning</span>
                </>
              }
              description="What a curator does with the work an agent did overnight, from the review queue to the publish."
            />

            <ol className="relative space-y-6 border-l border-border pl-8">
              {CURATOR_DAY.map((m) => (
                <li key={m.when} className="relative">
                  <span className="absolute -left-[2.28rem] top-1 flex size-4 items-center justify-center rounded-full border-2 border-primary bg-background" />
                  <p className="font-mono text-xs font-bold tracking-wide text-primary uppercase">
                    {m.when}
                  </p>
                  <h3 className="mt-1 font-bold">{m.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                    {m.body}
                  </p>
                </li>
              ))}
            </ol>
          </Container>
        </section>

        {/* ── The lifecycle ───────────────────────────────────────────── */}
        <section id="lifecycle" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="The lifecycle"
              title="Five states, and only one of them is public"
              description="A published post is an immutable snapshot sitting beside the editable draft rather than an entry in a long history, which is why the diff, the restore and the preview are cheap enough to be always available."
            />

            <ol className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              {LIFECYCLE.map((s, i) => (
                <li
                  key={s.state}
                  className="flex flex-col rounded-xl border border-border bg-card p-5"
                >
                  <span className="font-mono text-xs font-bold text-muted-foreground">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <h3 className="mt-1 font-mono text-sm font-bold text-primary">
                    {s.state}
                  </h3>
                  <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
                    {s.gloss}
                  </p>
                </li>
              ))}
            </ol>
          </Container>
        </section>

        {/* ── Editing on the page ─────────────────────────────────────── */}
        <section id="editing" className="py-20">
          <Container>
            <div className="grid items-center gap-10 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
              <div>
                <Eyebrow>On the page itself</Eyebrow>
                <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
                  Fix the headline{" "}
                  <span className="text-primary">where you can see it</span>
                </h2>
                <p className="mt-4 text-base leading-relaxed text-muted-foreground">
                  The renderer annotates what it renders, so the visual editor knows
                  which field on which post produced the words under the cursor. You
                  click the headline on the page and you are editing the headline,
                  not hunting for it in a form that looks nothing like the
                  result.
                </p>
                <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
                  It works on the built-in renderer and on a front end of your own: a
                  published bridge package makes any page editable once it emits the
                  same annotations. And it edits the <em>draft</em>, so looking at a
                  page and changing it are the same act with the same safety as every
                  other write. <LawChip id="P7" />
                </p>
              </div>

              <div className="rounded-2xl border border-border bg-card p-6">
                <div className="flex items-center gap-2 border-b border-border pb-3">
                  <span className="flex gap-1.5" aria-hidden>
                    <span className="size-2.5 rounded-full bg-[#7c3aed]/60" />
                    <span className="size-2.5 rounded-full bg-[#f5c46b]/60" />
                    <span className="size-2.5 rounded-full bg-[#7dd3a0]/60" />
                  </span>
                  <span className="font-mono text-xs text-muted-foreground">
                    /preview/acme/launch
                  </span>
                  <span className="ml-auto rounded-full bg-amber-500/15 px-2 py-0.5 font-mono text-[0.65rem] font-bold text-amber-700 uppercase dark:text-amber-400">
                    draft
                  </span>
                </div>
                <div className="space-y-3 pt-5">
                  <div className="relative rounded-lg border-2 border-dashed border-primary/50 bg-primary/5 p-3">
                    <span className="absolute -top-2.5 left-2 rounded bg-primary px-1.5 py-0.5 font-mono text-[0.6rem] font-bold text-primary-foreground">
                      hero.heading
                    </span>
                    <p className="text-lg font-extrabold">Launching Shio 2026.3</p>
                    <MousePointerClick className="absolute -right-1 -bottom-1 size-5 text-primary" />
                  </div>
                  <div className="rounded-lg border border-dashed border-border p-3">
                    <p className="text-sm text-muted-foreground">
                      An open-source headless CMS with four ways in: the console, an
                      agent, files in a repository, and the delivery API.
                    </p>
                  </div>
                  <div className="rounded-lg border border-dashed border-border p-3">
                    <div className="h-16 rounded bg-muted" />
                  </div>
                </div>
              </div>
            </div>
          </Container>
        </section>

        {/* ── The gates ───────────────────────────────────────────────── */}
        <section
          id="gates"
          className="relative isolate overflow-hidden bg-[#150e0c] py-20 text-[#f6ece8]"
        >
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="animate-orb-a pointer-events-none absolute -top-32 left-1/4 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-30 blur-[80px]" />

          <Container>
            <div className="mx-auto mb-12 max-w-2xl text-center">
              <span className="inline-block bg-gradient-to-r from-[#ff8a6b] to-[#f5c46b] bg-clip-text font-mono text-xs font-bold tracking-[0.12em] text-transparent uppercase">
                The six gates
              </span>
              <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
                Each one is a mechanism, and{" "}
                <span className="text-gradient">each one has a reason</span>
              </h2>
              <p className="mt-3 text-base leading-relaxed text-[#c9b6ae] sm:text-lg">
                The reason is the interesting half. A rule with no argument behind it
                is a rule somebody will turn off the first time it is inconvenient.
              </p>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              {GATES.map((g) => (
                <div
                  key={g.title}
                  className="rounded-xl border border-white/10 bg-white/[0.03] p-6"
                >
                  <div className="flex size-10 items-center justify-center rounded-lg bg-[#7c3aed]/12 text-[#c4b5fd]">
                    <g.icon className="size-5" />
                  </div>
                  <h3 className="mt-4 font-bold">{g.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-[#c9b6ae]">
                    {g.mechanism}
                  </p>
                  <p className="mt-3 border-t border-white/10 pt-3 text-sm leading-relaxed text-[#a89189]">
                    <span className="font-semibold text-[#c4b5fd]">Because</span>{" "}
                    {g.because}
                  </p>
                </div>
              ))}
            </div>
          </Container>
        </section>

        {/* ── The shape, plainly ──────────────────────────────────────── */}
        <section id="shape" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="In plain words"
              title="Three seats, and you can hold all of them"
              description="Who does what on an instance, and which seat may stay empty."
            />

            <div className="grid gap-6 md:grid-cols-3">
              {ROLES.map((role) => (
                <div key={role.actor} className="rounded-2xl border border-border bg-card p-6">
                  <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <role.icon className="size-5" />
                  </div>
                  <h3 className="mt-4 text-lg font-bold">
                    {role.actor}{" "}
                    <span className="font-mono text-sm font-semibold text-primary">
                      {role.verb}
                    </span>
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {role.description}
                  </p>
                </div>
              ))}
            </div>

            <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
              The first seat is the one that is never empty, and this page is what it
              is holding. The second can stay empty forever:{" "}
              <a href="/console/" className="font-semibold text-primary underline-offset-4 hover:underline">
                an instance with no agent attached
              </a>{" "}
              is an ordinary CMS, with the same review queue, the same previews and the
              same publish act described above.
            </p>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
