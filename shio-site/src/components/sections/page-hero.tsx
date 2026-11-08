import type { ReactNode } from "react";
import { Container, Eyebrow } from "@/components/brand";

/**
 * The compact band every page that is not the landing page opens on.
 *
 * It exists so a sub-page is recognisably the same site without repeating the
 * landing hero's weight: the same surface and type, and no room for a demo.
 * `children` is for the one artefact the page leads with (a command to copy, a
 * pair of buttons), and most pages pass nothing.
 *
 * SH1370: the surface follows the theme, as the hub's does. It was a permanent
 * dark band with a grid and an animated orb, which in light mode put a dark
 * block on top of every light page.
 */
export function PageHero({
  eyebrow,
  title,
  lead,
  children,
}: {
  eyebrow: string;
  title: ReactNode;
  lead: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section id="top" className="border-b border-border bg-background py-14 text-foreground sm:py-16">
      <Container className="max-w-3xl">
        <Eyebrow>{eyebrow}</Eyebrow>
        <h1 className="mt-3 text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
          {title}
        </h1>
        <p className="mt-5 text-lg leading-relaxed text-muted-foreground">{lead}</p>
        {children && <div className="mt-7">{children}</div>}
      </Container>
    </section>
  );
}
