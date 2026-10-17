import type { ReactNode } from "react";
import { Container, Eyebrow } from "@/components/brand";

/**
 * The compact dark band every page that is not the landing page opens on.
 *
 * It exists so a sub-page is recognisably the same site without repeating the
 * landing hero's weight: same surface, same grid, one orb instead of two, and
 * no room for a demo. `children` is for the one artefact the page leads with
 * (a command to copy, a pair of buttons), and most pages pass nothing.
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
    <section
      id="top"
      className="relative isolate overflow-hidden bg-[#150e0c] py-16 text-[#f6ece8] sm:py-20"
    >
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-a pointer-events-none absolute -top-40 left-1/3 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-40 blur-[70px]" />

      <Container className="max-w-3xl">
        <Eyebrow>{eyebrow}</Eyebrow>
        <h1 className="mt-3 text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
          {title}
        </h1>
        <p className="mt-5 text-lg leading-relaxed text-[#c9b6ae]">{lead}</p>
        {children && <div className="mt-7">{children}</div>}
      </Container>
    </section>
  );
}
