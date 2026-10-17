import { Fragment } from "react";
import { ArrowRight, Check, Minus, ThumbsUp, X } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { Container, LawChip, SectionHeader, TableScroll } from "@/components/brand";
import { Button } from "@/components/ui/button";
import {
  COMPARE_COLUMNS,
  COMPARE_GROUPS,
  OBJECTIONS,
  RIVALS,
} from "@/lib/site-content";
import { cn } from "@/lib/utils";

/** A cell is a verdict or a short phrase; only three values get an icon. */
function Cell({ value, strong }: { value: string; strong?: boolean }) {
  if (value === "yes") {
    return (
      <span
        className={cn(
          "inline-flex items-center",
          strong ? "text-primary" : "text-emerald-600 dark:text-emerald-400"
        )}
      >
        <Check className="size-4" aria-hidden />
        <span className="sr-only">yes</span>
      </span>
    );
  }
  if (value === "no") {
    return (
      <span className="inline-flex items-center text-muted-foreground/50">
        <X className="size-4" aria-hidden />
        <span className="sr-only">no</span>
      </span>
    );
  }
  if (value === "partial") {
    return (
      <span className="inline-flex items-center text-amber-600 dark:text-amber-400">
        <Minus className="size-4" aria-hidden />
        <span className="sr-only">partial</span>
      </span>
    );
  }
  return (
    <span className="text-[0.7rem] leading-tight font-medium text-muted-foreground">
      {value}
    </span>
  );
}

/**
 * SH534: "agent-native" is a category claim, and a category claim with nothing
 * to falsify it is marketing.
 *
 * Two halves, and the second is what makes the first readable. The matrix is
 * grouped by law rather than by feature area, so a reader can see that the
 * column of ticks is not a feature list but one design position applied ten
 * times. Then every rival gets a paragraph saying what it does better and
 * when to pick it instead, because a table where one column wins every row is a
 * table nobody believes.
 */
export function ComparePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Compare"
          title={
            <>
              Every CMS has an API.{" "}
              <span className="text-gradient">Few were designed for the caller.</span>
            </>
          }
          lead={
            <>
              Compared on content types and delivery APIs, Viglet Shio and its
              alternatives tie, because everyone has those. The axis where they differ is
              agent-operability, and every row below is a question with an
              answer you can test rather than a feature you can be told about.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#matrix">
                The matrix <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#wins">Where Shio loses</a>
            </Button>
          </div>
        </PageHero>

        {/* ── The matrix ──────────────────────────────────────────────── */}
        <section id="matrix" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="The matrix"
              title={
                <>
                  Ten laws, turned into{" "}
                  <span className="text-primary">eighteen questions</span>
                </>
              }
              description="Grouped by the law each one comes from, so what you are looking at reads as one design position applied repeatedly rather than as a feature list that happens to be long."
            />

            <TableScroll className="rounded-xl border border-border bg-card">
              <table className="w-full min-w-[52rem] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="sticky left-0 z-10 bg-card px-4 py-3 text-left font-semibold">
                      Capability
                    </th>
                    {COMPARE_COLUMNS.map((col, i) => (
                      <th
                        key={col}
                        className={cn(
                          "px-3 py-3 text-center font-semibold",
                          // The tinted column runs the height of the table, so
                          // the header carries it too or the stripe starts late.
                          i === 0
                            ? "bg-primary/5 text-primary"
                            : "text-muted-foreground"
                        )}
                      >
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {COMPARE_GROUPS.map((group) => (
                    <Fragment key={group.title}>
                      <tr className="border-b border-border bg-muted/50">
                        <th
                          colSpan={COMPARE_COLUMNS.length + 1}
                          className="px-4 py-2.5 text-left"
                        >
                          <span className="flex items-center gap-2 text-xs font-bold tracking-wide uppercase">
                            {group.law !== "none" && <LawChip id={group.law} />}
                            {group.title}
                          </span>
                        </th>
                      </tr>
                      {group.rows.map((row) => (
                        <tr key={row.feature} className="border-b border-border/60">
                          <td className="sticky left-0 z-10 bg-card px-4 py-3 font-medium">
                            {row.feature}
                          </td>
                          {row.cells.map((cell, i) => (
                            <td
                              key={COMPARE_COLUMNS[i]}
                              className={cn(
                                "px-3 py-3 text-center",
                                i === 0 && "bg-primary/5"
                              )}
                            >
                              <Cell value={cell} strong={i === 0} />
                            </td>
                          ))}
                        </tr>
                      ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </TableScroll>

            <div className="mx-auto mt-6 max-w-3xl rounded-xl border border-border bg-muted/40 p-5 text-sm leading-relaxed text-muted-foreground">
              <p>
                <span className="font-semibold text-foreground">
                  How to read the other columns.
                </span>{" "}
                They say whether the product <em>documents the property as a guarantee</em>,
                not whether some path to it exists. An export command is not a file
                projection with a three-way merge, and an MCP server in front of an API
                designed for a browser is not a protocol designed for an agent. Where the
                answer is "you can build it", the cell says so.
              </p>
              <p className="mt-3">
                This reflects 2026.3, so verify against current vendor documentation
                before deciding. If a row here is wrong,{" "}
                <span className="font-medium text-foreground">
                  it is a bug and we want the correction.
                </span>
              </p>
            </div>
          </Container>
        </section>

        {/* ── Where each one wins ─────────────────────────────────────── */}
        <section id="wins" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="Where they win"
              title={
                <>
                  Four products Shio{" "}
                  <span className="text-primary">does not beat</span>
                </>
              }
              description="A table where one column wins every row is a table nobody believes. Here is what each alternative does better, and the case where picking it over Shio is the right call."
            />

            <div className="grid gap-5 sm:grid-cols-2">
              {RIVALS.map((r) => (
                <div key={r.name} className="rounded-2xl border border-border bg-card p-6">
                  <h3 className="flex items-center gap-2 text-lg font-bold">
                    <ThumbsUp className="size-4 text-primary" />
                    {r.name}
                  </h3>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                    {r.wins}
                  </p>
                  <p className="mt-4 border-t border-border pt-4 text-sm leading-relaxed">
                    <span className="font-semibold">Pick it instead when</span>{" "}
                    <span className="text-muted-foreground">{r.chooseIt}</span>
                  </p>
                </div>
              ))}
            </div>

            <div className="mx-auto mt-8 max-w-3xl rounded-xl border border-primary/25 bg-primary/5 p-5">
              <h3 className="text-sm font-bold">And the case against Shio, stated plainly</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                If nobody on the project is going to drive it with an agent, most of what
                this page argues is worth nothing to you: you are buying a
                self-hosted CMS with a smaller ecosystem than the four above and
                a JVM to run. The protocol is the product. Judge it on whether that is the
                thing you need.
              </p>
            </div>
          </Container>
        </section>

        {/* ── Objections ──────────────────────────────────────────────── */}
        <section id="objections" className="py-20">
          <Container className="max-w-3xl">
            <SectionHeader
              eyebrow="Objections"
              title="Six objections, answered"
            />
            <dl className="space-y-6">
              {OBJECTIONS.map((o) => (
                <div key={o.q} className="rounded-xl border border-border bg-card p-6">
                  <dt className="font-bold">{o.q}</dt>
                  <dd className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {o.a}
                  </dd>
                </div>
              ))}
            </dl>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
