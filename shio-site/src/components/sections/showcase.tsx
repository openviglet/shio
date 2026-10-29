import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { SHOWCASE_CLI } from "@/lib/site-content";
import { SITE_PAGES, TOUR_PAGES } from "@/lib/showcase";
import { withBase } from "@/lib/base";

/**
 * SH1258: the landing page's pointer to a finished site, so a visitor sees what the
 * surfaces described above add up to before reading about each one.
 */
export function Showcase() {
  return (
    <section id="showcase" className="border-y border-border bg-muted/30 py-20">
      <Container>
        <SectionHeader
          eyebrow="The showcase"
          title={
            <>
              A finished site,{" "}
              <span className="text-primary">one install away</span>
            </>
          }
          description={`Quilhara is a fictional trail magazine and guided-trip outfitter, in English and Brazilian Portuguese: ${SITE_PAGES.length} pages a visitor reads, and ${TOUR_PAGES.length} tour pages that say how each part was built and which feature it shows.`}
        />

        <div className="grid items-start gap-8 lg:grid-cols-2 [&>*]:min-w-0">
          <ul className="space-y-3 text-sm leading-relaxed text-muted-foreground">
            {TOUR_PAGES.map((page) => (
              <li key={page.path} className="flex gap-3">
                <code className="shrink-0 font-mono text-xs font-semibold text-primary">
                  {page.path}
                </code>
                <span>{page.summary}</span>
              </li>
            ))}
          </ul>
          <div className="space-y-4">
            <CodeBlock caption="From a terminal" code={SHOWCASE_CLI} />
            <Button asChild size="lg">
              <a href={withBase("/showcase/")}>
                Both install paths, and every page <ArrowRight className="size-4" />
              </a>
            </Button>
          </div>
        </div>
      </Container>
    </section>
  );
}
