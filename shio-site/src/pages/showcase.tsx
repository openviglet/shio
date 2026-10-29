import { Fragment } from "react";
import { ArrowRight } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { ShowcaseGallery } from "@/components/sections/showcase";
import { Container, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { CopyCommand } from "@/components/copy-command";
import { Button } from "@/components/ui/button";
import {
  SHOWCASE_CLI,
  SHOWCASE_CONSOLE_STEPS,
  SHOWCASE_RUN,
  SHOWCASE_URL,
} from "@/lib/site-content";
import { SITE_PAGES, TOUR_PAGES, type ShowcasePage as Page } from "@/lib/showcase";

/**
 * SH1258: the showcase, for someone deciding whether Shio is worth an afternoon.
 *
 * Install first, because the site is the proof and nothing here replaces looking at it.
 * Then every page the package carries, with the features each one demonstrates, all of it
 * read from the SH1250 matrix, so the depth pages can link here by feature and a page the
 * package drops cannot stay listed.
 */
export function ShowcasePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="The showcase"
          title={
            <>
              A finished site to <span className="text-gradient">take apart</span>
            </>
          }
          lead={
            <>
              Quilhara is a fictional trail magazine and guided-trip outfitter, built
              with Viglet Shio in English and Brazilian Portuguese. Install it on your
              own instance and every page is yours to read, edit, publish and break,
              including the tour pages that explain how it was made.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#install">
                Install it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#tour">What each page shows</a>
            </Button>
          </div>
        </PageHero>

        <section id="install" className="py-20">
          <Container>
            <ShowcaseGallery className="mb-16" />
            <SectionHeader
              eyebrow="Install"
              title={
                <>
                  Two ways in, <span className="text-primary">one result</span>
                </>
              }
              description="A fresh instance lists the showcase in its marketplace with nothing configured. Start one:"
            />
            <div className="mb-10 flex justify-center">
              <CopyCommand command={SHOWCASE_RUN} />
            </div>

            <div className="grid items-start gap-8 lg:grid-cols-2 [&>*]:min-w-0">
              <div className="rounded-2xl border border-border bg-card p-6">
                <h3 className="text-lg font-bold">In the console</h3>
                <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-muted-foreground">
                  {SHOWCASE_CONSOLE_STEPS.map((step) => (
                    <li key={step}>{step}</li>
                  ))}
                </ol>
              </div>
              <div className="rounded-2xl border border-border bg-card p-6">
                <h3 className="text-lg font-bold">From a terminal</h3>
                <CodeBlock className="mt-4" code={SHOWCASE_CLI} />
              </div>
            </div>

            <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
              Then open <code className="font-mono">{SHOWCASE_URL}</code>. Installing
              writes nothing to your content until the apply, and the apply plans before
              it writes.
            </p>
          </Container>
        </section>

        <section id="tour" className="border-t border-border py-20">
          <Container>
            <SectionHeader
              eyebrow="The tour"
              title="How the site was built"
              description="Each tour page explains one part of the work and links to the pages that show it."
            />
            <PageList pages={TOUR_PAGES} />
          </Container>
        </section>

        <section id="pages" className="border-t border-border py-20">
          <Container>
            <SectionHeader
              eyebrow="The site"
              title="What a visitor reads"
              description="The magazine itself. Where a page is the clearest place to see a feature, the feature is named under it."
            />
            <PageList pages={SITE_PAGES} />
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}

function PageList({ pages }: Readonly<{ pages: Page[] }>) {
  return (
    <ul className="grid gap-4 md:grid-cols-2 [&>*]:min-w-0">
      {pages.map((page) => {
        const shown = [...page.features, ...page.tools, ...page.surfaces];
        return (
          <li
            key={page.path}
            id={page.anchor}
            className="scroll-mt-24 rounded-xl border border-border bg-card p-5"
          >
            <code className="font-mono text-sm font-bold text-primary">{page.path}</code>
            <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{page.summary}</p>
            {shown.length > 0 && (
              <p className="mt-3 flex flex-wrap gap-1.5">
                {/* The space between chips is invisible in a flex row and keeps the
                    Markdown twin from running the names together. */}
                {shown.map((name, i) => (
                  <Fragment key={name}>
                    {i > 0 && " "}
                    <code className="rounded-md bg-muted px-1.5 py-0.5 font-mono text-[0.7rem] text-foreground">
                      {name}
                    </code>
                  </Fragment>
                ))}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
