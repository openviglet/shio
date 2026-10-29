import { ArrowRight, ArrowUpRight } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { Container, LawChip } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { LINKS } from "@/lib/site-content";
import type { FeatureDetail } from "@/lib/feature-content";
import { withBase } from "@/lib/base";
import { showcasePageFor } from "@/lib/showcase";

/**
 * SH539: one component, one record per pillar, one prerendered route each.
 *
 * The blocks alternate their code sample left and right rather than stacking in
 * one column: these pages are read top to bottom by somebody deciding, and a
 * column of identical cards is the shape people scroll past.
 */
/** SH1258: the showcase page that demonstrates this feature, resolved through the SH1250 matrix. */
function ShowcaseLink({ feature }: Readonly<{ feature: string }>) {
  const page = showcasePageFor(feature);
  return (
    <p className="mt-16 max-w-3xl leading-relaxed text-muted-foreground">
      See it running: the showcase site shows this on{" "}
      <a
        href={withBase(`/showcase/#${page.anchor}`)}
        className="font-mono font-semibold text-primary underline-offset-4 hover:underline"
      >
        {page.path === "/" ? "its home page" : page.path}
      </a>
      , one install away on your own instance.
    </p>
  );
}

export function FeaturePage({ detail }: Readonly<{ detail: FeatureDetail }>) {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow={detail.eyebrow}
          title={
            <>
              {detail.title}{" "}
              <span className="text-gradient">{detail.titleAccent}</span>
            </>
          }
          lead={detail.lead}
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={LINKS.docs}>
                Read the docs <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href={withBase("/#laws")}>The ten laws</a>
            </Button>
          </div>
        </PageHero>

        <section className="py-20">
          <Container>
            <div className="space-y-16">
              {detail.blocks.map((block, i) => (
                <article
                  key={block.title}
                  className={
                    block.code
                      ? "grid items-start gap-8 lg:grid-cols-2 [&>*]:min-w-0"
                      : // Not centred: a block with a code sample spans the
                        // container, so centring the ones without it puts two
                        // different left edges down the same page.
                        "max-w-3xl"
                  }
                >
                  <div className={block.code && i % 2 === 1 ? "lg:order-2" : undefined}>
                    <h2 className="flex flex-wrap items-center gap-2 text-2xl font-extrabold tracking-tight">
                      {block.title}
                      {block.law && <LawChip id={block.law} />}
                    </h2>
                    <p className="mt-3 leading-relaxed text-muted-foreground">{block.body}</p>
                  </div>
                  {block.code && (
                    <div className={i % 2 === 1 ? "lg:order-1" : undefined}>
                      <CodeBlock caption={block.code.caption} code={block.code.text} />
                    </div>
                  )}
                </article>
              ))}
            </div>

            <ShowcaseLink feature={detail.showcase} />

            <nav className="mt-16 border-t border-border pt-8">
              <h2 className="text-sm font-bold tracking-wide text-muted-foreground uppercase">
                Next
              </h2>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                {detail.related.map((r) => (
                  <li key={r.href}>
                    <a
                      href={r.href}
                      className="group flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-5 font-semibold transition-colors hover:border-primary/40"
                    >
                      {r.label}
                      <ArrowUpRight className="size-4 shrink-0 text-muted-foreground transition-colors group-hover:text-primary" />
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
