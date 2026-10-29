import { ArrowRight, Check, Layers, ShieldAlert } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { CLONE_BOUNDS, MIXED_PAGE, MODES, PHASES, PROPOSE_OUTPUT } from "@/lib/site-content";
import { cn } from "@/lib/utils";
import { withBase } from "@/lib/base";

/**
 * SH535: the one capability a visitor can test against a URL they already own.
 *
 * The page is ordered by the pipeline, but its centre is the section in the
 * middle: the three modes. Everything before it is machinery and everything after
 * it is proof, and the decision a reviewer actually makes (their look, or a
 * site somebody can maintain) is the thing worth arriving at.
 */
export function ReplicatePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Replication"
          title={
            <>
              Point it at a site you{" "}
              <span className="text-gradient">already have</span>
            </>
          }
          lead={
            <>
              Five commands turn a site that exists into one a curator can edit in
              Viglet Shio. What comes out is a content model derived from the pages
              themselves, with a review step you cannot skip and arithmetic at the
              end that says whether anything was lost.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#pipeline">
                The five commands <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#bounds">What it will not do</a>
            </Button>
          </div>
        </PageHero>

        {/* ── The pipeline ────────────────────────────────────────────── */}
        <section id="pipeline" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="The pipeline"
              title={
                <>
                  Capture, propose, convert,{" "}
                  <span className="text-primary">prove</span>
                </>
              }
              description="Five commands, and each one makes a promise the next depends on. They are separate on purpose: a single button that crawled a stranger's site and wrote content into yours would be the surprise the whole design forbids."
            />

            <ol className="space-y-5">
              {PHASES.map((p, i) => (
                <li key={p.verb} className="rounded-2xl border border-border bg-card p-6">
                  <div className="flex flex-wrap items-center gap-3">
                    <span className="flex size-8 items-center justify-center rounded-full bg-primary/12 font-mono text-sm font-black text-primary">
                      {i + 1}
                    </span>
                    <h3 className="text-lg font-bold">{p.title}</h3>
                  </div>
                  <pre className="mt-4 overflow-x-auto rounded-lg bg-[#17100e] px-4 py-3 font-mono text-[0.78rem] leading-relaxed text-[#ddd6fe]">
                    <code>$ {p.command}</code>
                  </pre>
                  <p className="mt-4 text-sm leading-relaxed text-muted-foreground">{p.what}</p>
                  <p className="mt-4 flex gap-2.5 rounded-lg border border-primary/20 bg-primary/5 p-3 text-sm leading-relaxed">
                    <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                    <span>{p.promise}</span>
                  </p>
                </li>
              ))}
            </ol>
          </Container>
        </section>

        {/* ── The three modes ─────────────────────────────────────────── */}
        <section
          id="modes"
          className="relative isolate overflow-hidden bg-[#150e0c] py-20 text-[#f6ece8]"
        >
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="animate-orb-b pointer-events-none absolute -bottom-40 right-1/4 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-25 blur-[80px]" />

          <Container>
            <div className="mx-auto mb-12 max-w-2xl text-center">
              <span className="inline-block bg-gradient-to-r from-[#c4b5fd] to-[#f5c46b] bg-clip-text font-mono text-xs font-bold tracking-[0.12em] text-transparent uppercase">
                The decision
              </span>
              <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
                Their look, or{" "}
                <span className="text-gradient">a site somebody can maintain</span>
              </h2>
              <p className="mt-3 text-base leading-relaxed text-[#c9b6ae] sm:text-lg">
                Every captured block has three possible futures: keep its look,
                keep its look with editable fields, or rebuild it as content in the
                site&apos;s own theme. The proposal asks you which one per block rather
                than deciding for you. This is the choice the whole pipeline exists to
                put in front of a reviewer.
              </p>
            </div>

            <div className="grid gap-5 md:grid-cols-3">
              {MODES.map((m) => (
                <div
                  key={m.name}
                  className={cn(
                    "rounded-2xl border p-6",
                    m.accent === "authorable"
                      ? "border-[#7c3aed]/40 bg-[#7c3aed]/[0.07]"
                      : "border-white/12 bg-white/[0.03]"
                  )}
                >
                  <h3 className="flex items-center gap-2 text-lg font-bold">
                    <Layers
                      className={cn(
                        "size-4",
                        m.accent === "authorable" ? "text-[#c4b5fd]" : "text-[#a89189]"
                      )}
                    />
                    {m.name}
                  </h3>
                  <dl className="mt-4 space-y-3 text-sm leading-relaxed">
                    <div>
                      <dt className="font-mono text-xs font-bold text-[#c4b5fd]">keeps</dt>
                      <dd className="mt-0.5 text-[#c9b6ae]">{m.keeps}</dd>
                    </div>
                    <div>
                      <dt className="font-mono text-xs font-bold text-[#c4b5fd]">offers</dt>
                      <dd className="mt-0.5 text-[#c9b6ae]">{m.offers}</dd>
                    </div>
                  </dl>
                  <p className="mt-4 border-t border-white/10 pt-4 text-sm leading-relaxed">
                    <span className="font-semibold">Pick it when</span>{" "}
                    <span className="text-[#a89189]">{m.pick}</span>
                  </p>
                </div>
              ))}
            </div>

            <div className="mx-auto mt-8 max-w-3xl rounded-xl border border-[#7c3aed]/25 bg-[#7c3aed]/[0.05] p-5">
              <h3 className="text-sm font-bold">{MIXED_PAGE.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-[#c9b6ae]">{MIXED_PAGE.body}</p>
            </div>

            <div className="mx-auto mt-8 max-w-3xl rounded-xl border border-white/10 bg-white/[0.03] p-5">
              <h3 className="text-sm font-bold">
                Why the look and the model are separate choices
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-[#a89189]">
                Because the one that tried was measured. An authorable replica of a real
                site rendered <span className="font-mono text-[#f6ece8]">1,066,436</span>{" "}
                bytes, <span className="font-mono text-[#f6ece8]">99%</span> of it the
                captured stylesheet: roughly 2,894 rules meeting nineteen class
                attributes, with not one source class surviving. Carrying the source's
                classes through recovers about seven of the twenty-two that style a single
                heading; the rest sit on wrapper elements a generated layout does not
                have, so it buys typography and no layout. Translating them into a stable
                contract would mean a design system per replicated site. So
                the <em>promise</em> changed instead of the implementation being patched:
                keep the source&apos;s look or rebuild it as content, rather than one mode
                half-doing both. Annotated fidelity sits on the first side: it adds
                editable fields to the kept markup and does not try to rebuild it.
              </p>
            </div>
          </Container>
        </section>

        {/* ── The proposal ────────────────────────────────────────────── */}
        <section id="proposal" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="The artefact in the middle"
              title={
                <>
                  A candidate is a{" "}
                  <span className="text-primary">signature, not a block</span>
                </>
              }
              description="Which is what makes reviewing a site affordable instead of a chore: the same hero on sixty pages is one decision, not sixty."
            />

            <div className="grid items-start gap-8 lg:grid-cols-[1.05fr_0.95fr] [&>*]:min-w-0">
              <CodeBlock caption="the proposal" code={PROPOSE_OUTPUT} />

              <div className="space-y-4">
                <div className="rounded-xl border border-border bg-card p-5">
                  <h3 className="text-sm font-bold">It costs the same at four pages and sixty</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    Measured, and printed on every run: four pages of four blocks costs
                    about <span className="font-mono text-foreground">294</span> tokens
                    and sixty pages costs about{" "}
                    <span className="font-mono text-foreground">295</span>. What grows is
                    a path per page, not a candidate per page, and the per-block
                    implementation an author naturally writes would have produced 240
                    candidates for the same site. <LawChip id="P3" />
                  </p>
                </div>

                <div className="rounded-xl border border-border bg-card p-5">
                  <h3 className="text-sm font-bold">
                    Two kinds of loss, because they are two conversations
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    <span className="font-mono text-foreground">discards:</span> is what
                    the section cannot hold: an{" "}
                    <span className="font-mono">&lt;iframe&gt;</span>, an{" "}
                    <span className="font-mono">onclick</span>, an id an anchor targets.
                    That is damage, and a reviewer should weigh it.{" "}
                    <span className="font-mono text-foreground">replaces:</span> is the
                    count of source classes being set aside, which is the mode's contract
                    rather than damage. Conflating them made every line read as an
                    argument for fidelity on every block.
                  </p>
                </div>

                <div className="rounded-xl border border-border bg-card p-5">
                  <h3 className="text-sm font-bold">
                    The stylesheet is read as tokens, not just concatenated
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    A source's CSS <em>contains</em> its palette, its type scale and its
                    spacing rhythm, so a conversion that only appends the file throws all
                    of it away, and the replica then looks right and cannot be
                    changed. Eight groups are lifted into the theme's design tokens, and
                    the report says which ones are real: a declared custom
                    property is editable, while a literal repeated twenty-three
                    times is the palette by observation and changes nothing until somebody
                    rewires the CSS.{" "}
                    <a
                      href={withBase("/features/rendering/")}
                      className="font-medium text-primary underline underline-offset-4"
                    >
                      How tokens work &rarr;
                    </a>
                  </p>
                </div>

                <div className="rounded-xl border border-border bg-card p-5">
                  <h3 className="text-sm font-bold">The loss is computed, never listed</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    It is the complement of what the fields consumed, and the only
                    definition that cannot <em>under</em>-report, and under-reporting is
                    the one failure this artefact must not have.
                  </p>
                </div>
              </div>
            </div>
          </Container>
        </section>

        {/* ── The bounds ──────────────────────────────────────────────── */}
        <section id="bounds" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="The bounds"
              title={
                <>
                  What it refuses to do, and{" "}
                  <span className="text-primary">why that is the point</span>
                </>
              }
              description="A crawler you cannot bound is a crawler nobody should run against somebody else's site. Each of these is recorded in the artefact rather than in whoever ran the command."
            />

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {CLONE_BOUNDS.map((b) => (
                <div key={b.title} className="rounded-xl border border-border bg-card p-5">
                  <h3 className="flex items-start gap-2 text-sm font-bold">
                    <ShieldAlert className="mt-0.5 size-4 shrink-0 text-primary" />
                    {b.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {b.body}
                  </p>
                </div>
              ))}
            </div>
          </Container>
        </section>

        {/* ── What it is not ──────────────────────────────────────────── */}
        <section id="not" className="py-20">
          <Container className="max-w-3xl">
            <SectionHeader eyebrow="Expectations" title="Three things this is not" />
            <div className="space-y-4">
              {[
                {
                  t: "It is not a mirror.",
                  d: "A mirror is bytes. What comes out here is a content model (post types with typed fields, sections a curator edits, a theme and the scripts), which is why the review step exists at all.",
                },
                {
                  t: "It is not a pixel comparison.",
                  d: "shio diff is arithmetic over declared and produced facts. If you want pixels, shio snapshot is that and says so, and it hands the picture to a person, because that is the one judgement a digest cannot make.",
                },
                {
                  t: "It is not a migration button.",
                  d: "There is a review you cannot skip and a plan file that applies nothing until you run the next verb. What it displaces is a migration project; what it is not is a migration that happens to you.",
                },
              ].map((x) => (
                <div key={x.t} className="rounded-xl border border-border bg-card p-5">
                  <p className="font-bold">{x.t}</p>
                  <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{x.d}</p>
                </div>
              ))}
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
