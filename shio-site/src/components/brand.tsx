import * as React from "react";
import { cn } from "@/lib/utils";
import { VigletLogo } from "@/components/viglet-logo";

/** The Viglet Shio cube mark plus the wordmark. */
export function Logo({
  className,
  showWord = true,
  size = 32,
}: {
  className?: string;
  showWord?: boolean;
  size?: number;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-extrabold", className)}>
      <VigletLogo identifier="shio" size={size} />
      {showWord && <span>Viglet Shio</span>}
    </span>
  );
}

/** Centered max-width container. */
export function Container({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("mx-auto w-full max-w-6xl px-6", className)} {...props} />;
}

/**
 * Horizontally-scrollable table wrapper for mobile, with a right-edge fade that
 * hints "more columns →" on small screens. Pair it with a `sticky left-0` first
 * column so the row label stays put while the value columns scroll.
 *
 * The wrapper is `overflow-hidden` and that is load-bearing, not tidiness.
 * Measured at 375px: the inner `overflow-x-auto` box lays out at 327 and scrolls
 * a 608px table correctly, and the wrapper still reported `scrollWidth` 527 —
 * which made the whole document scroll sideways by 176px. It is not the sticky
 * column (removing it changes nothing); some of the scroll container's own
 * overflow reaches the ancestor's scrollable area. Clipping here costs nothing:
 * the fade is `absolute right-0` inside these same bounds.
 */
export function TableScroll({
  className,
  fadeClassName = "from-background",
  children,
}: {
  className?: string;
  fadeClassName?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="relative overflow-hidden">
      <div className={cn("overflow-x-auto", className)}>{children}</div>
      <div
        aria-hidden
        className={cn(
          "pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l to-transparent sm:hidden",
          fadeClassName
        )}
      />
    </div>
  );
}

/**
 * Gradient eyebrow above a heading. Monospaced on purpose — it is the site's
 * one persistent nod to the surface the product is actually operated from.
 */
export function Eyebrow({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-block bg-gradient-to-r from-[#5b21b6] to-[#f59e0b] bg-clip-text font-mono text-xs font-bold tracking-[0.12em] text-transparent uppercase">
      {children}
    </span>
  );
}

/** Centered section header: eyebrow + title + description. */
export function SectionHeader({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: React.ReactNode;
  description?: React.ReactNode;
}) {
  return (
    <div className="mx-auto mb-12 max-w-2xl text-center">
      <Eyebrow>{eyebrow}</Eyebrow>
      <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">{title}</h2>
      {description && (
        <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
          {description}
        </p>
      )}
    </div>
  );
}

/**
 * The law chip — `P4`, `P7`. Every section on the landing page is an instance of
 * one of the ten, and saying which is what keeps the page an argument instead of
 * a feature list.
 */
export function LawChip({
  id,
  className,
  tone = "light",
}: {
  id: string;
  className?: string;
  tone?: "light" | "dark";
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md border px-1.5 py-0.5 font-mono text-[0.7rem] font-bold",
        tone === "dark"
          ? "border-[#7c3aed]/40 bg-[#7c3aed]/10 text-[#c4b5fd]"
          : "border-primary/30 bg-primary/10 text-primary",
        className
      )}
    >
      {id}
    </span>
  );
}
