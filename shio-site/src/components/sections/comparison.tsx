import { ArrowRight, Check, Minus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, SectionHeader, TableScroll } from "@/components/brand";
import { COMPARISON, COMPARISON_COLUMNS } from "@/lib/site-content";
import { withBase } from "@/lib/base";

function Cell({ value }: { value: string }) {
  if (value === "yes")
    return (
      <span className="inline-flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400">
        <Check className="size-4" aria-hidden />
        <span className="sr-only">yes</span>
      </span>
    );
  if (value === "no")
    return (
      <span className="inline-flex items-center gap-1 text-muted-foreground/60">
        <X className="size-4" aria-hidden />
        <span className="sr-only">no</span>
      </span>
    );
  if (value === "n/a")
    return (
      <span className="inline-flex items-center gap-1 text-muted-foreground/50">
        <Minus className="size-4" aria-hidden />
        <span className="sr-only">not applicable</span>
      </span>
    );
  return <span className="text-xs font-medium text-muted-foreground">{value}</span>;
}

export function Comparison() {
  return (
    <section id="compare" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Where it differs"
          title={
            <>
              Every CMS has an API.{" "}
              <span className="text-primary">Few were designed for the caller.</span>
            </>
          }
          description="The axis is agent-operability, not feature count. On everything else (an ecosystem, hosted scale, an editor everyone already knows) the alternatives are strong, and the full comparison names where each one wins."
        />

        <TableScroll fadeClassName="from-muted/40" className="rounded-xl border border-border bg-card">
          <table className="w-full min-w-[38rem] border-collapse text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="sticky left-0 bg-card px-4 py-3 text-left font-semibold">
                  Capability
                </th>
                <th className="px-4 py-3 text-center font-bold text-primary">
                  {COMPARISON_COLUMNS.shio}
                </th>
                <th className="px-4 py-3 text-center font-semibold text-muted-foreground">
                  {COMPARISON_COLUMNS.headless}
                </th>
                <th className="px-4 py-3 text-center font-semibold text-muted-foreground">
                  {COMPARISON_COLUMNS.diy}
                </th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON.map((row) => (
                <tr key={row.feature} className="border-b border-border/60 last:border-0">
                  <td className="sticky left-0 bg-card px-4 py-3 font-medium">
                    {row.feature}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Cell value={row.shio} />
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Cell value={row.headless} />
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Cell value={row.diy} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableScroll>

        <p className="mx-auto mt-6 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
          The difference in one line: everywhere else an agent is a client of an
          API designed for a browser, and the protocol quality it gets is whatever
          fell out.
        </p>
        <div className="mt-6 flex justify-center">
          <Button
            asChild
            variant="outline"
            className="h-auto max-w-full whitespace-normal px-5 py-2.5 text-center"
          >
            <a href={withBase("/compare/")}>
              The full matrix, named products, and where Shio loses
              <ArrowRight className="size-4" />
            </a>
          </Button>
        </div>
      </Container>
    </section>
  );
}
