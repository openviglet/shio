import { useEffect, useState } from "react";
import { ArrowRight, ArrowUpRight, ChevronDown, Menu, Terminal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ModeToggle } from "@/components/mode-toggle";
import { Container, Logo } from "@/components/brand";
import { LINKS } from "@/lib/site-content";
import { cn } from "@/lib/utils";
import { withBase } from "@/lib/base";

// Hrefs are root-anchored ("/#…") so they keep working from any future page:
// the browser navigates home and scrolls to the section.
const NAV_LINKS = [{ label: "Docs", href: LINKS.docs }];

/**
 * The groups are the four ways in, plus the pages that are about the product
 * rather than about a path. They used to be "for the agent" and "for the
 * curator", which sorted two thirds of the site under a role most visitors do
 * not have yet, and put the console, the thing a first-time reader is looking
 * for, in the same drawer as a page about what stops an agent write.
 */
const NAV_GROUPS = [
  {
    label: "Ways to use it",
    items: [
      { label: "In the console, by hand", href: withBase("/console/") },
      { label: "With a coding agent", href: withBase("/claude-code/") },
      { label: "As files in your repository", href: withBase("/features/content-as-files/") },
      { label: "Through the delivery API", href: withBase("/features/delivery/") },
      { label: "All four, side by side", href: withBase("/#paths") },
    ],
  },
  {
    label: "The product",
    items: [
      { label: "What the console does", href: withBase("/console/#capabilities") },
      { label: "What stops an agent write", href: withBase("/curator/") },
      { label: "One call, and its price", href: withBase("/#one-call") },
      { label: "MCP + CLI surface", href: withBase("/#surface") },
      { label: "Errors that instruct", href: withBase("/#errors") },
      { label: "Proof without a screenshot", href: withBase("/#verify") },
      { label: "Rendering and design tokens", href: withBase("/features/rendering/") },
      { label: "Replicate an existing site", href: withBase("/replicate/") },
    ],
  },
  {
    label: "Decide",
    items: [
      { label: "Who runs it, and how", href: withBase("/#use-cases") },
      { label: "The ten design laws", href: withBase("/#laws") },
      { label: "Compare", href: withBase("/compare/") },
      { label: "Self-host", href: withBase("/#self-host") },
    ],
  },
];

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  return (
    <header
      className={cn(
        "sticky top-0 z-50 backdrop-blur-md transition-colors",
        scrolled
          ? "border-b border-border bg-background/80"
          : "border-b border-transparent bg-background/60"
      )}
    >
      <Container className="flex h-16 items-center gap-4">
        <div className="flex shrink-0 items-center gap-2.5">
          <a href={withBase("/")} className="text-base" aria-label="Viglet Shio home">
            <Logo className="hidden sm:inline-flex" />
            <Logo showWord={false} className="sm:hidden" />
          </a>
          <a
            href={LINKS.viglet}
            className="hidden items-center gap-1 rounded-full border border-border/60 px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:border-border hover:text-foreground sm:inline-flex"
          >
            part of viglet.org
            <ArrowUpRight className="size-3" />
          </a>
        </div>

        <nav className="ml-4 hidden items-center gap-5 lg:flex xl:ml-6 xl:gap-6">
          {NAV_LINKS.map((item) => (
            <a
              key={item.label}
              href={item.href}
              className="text-sm font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground"
            >
              {item.label}
            </a>
          ))}
          {/*
            The differentiator gets a pill of its own rather than a dropdown row.
            It used to read "Claude Code first", which was the site's whole
            positioning problem in two words: true of the build order, and read
            as a prerequisite. It now says what it is, a plugin you can install,
            and the console sits above it in the first group.
          */}
          <a
            href={withBase("/claude-code/")}
            className="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 font-mono text-sm font-bold whitespace-nowrap text-primary transition-colors hover:border-primary/50 hover:bg-primary/15"
          >
            <Terminal className="size-3.5" />
            Claude Code plugin
          </a>
          {NAV_GROUPS.map((group) => (
            <DropdownMenu key={group.label}>
              <DropdownMenuTrigger className="group flex items-center gap-1 text-sm font-semibold whitespace-nowrap text-muted-foreground outline-hidden transition-colors hover:text-foreground data-[state=open]:text-foreground">
                {group.label}
                <ChevronDown className="size-3.5 transition-transform group-data-[state=open]:rotate-180" />
              </DropdownMenuTrigger>
              {/* Radix returns focus to the trigger on close, and that focus()
                  scrolls the top-of-page trigger back into view, cancelling the
                  native hash jump to the section just clicked. */}
              <DropdownMenuContent
                align="start"
                className="min-w-52"
                onCloseAutoFocus={(e) => e.preventDefault()}
              >
                {group.items.map((item) => (
                  <DropdownMenuItem key={item.label} asChild>
                    <a href={item.href} className="w-full cursor-pointer font-medium">
                      {item.label}
                    </a>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <Button asChild size="sm">
            <a href={LINKS.docs}>
              Get started
              <ArrowRight className="size-4" />
            </a>
          </Button>
          <ModeToggle />
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            aria-expanded={menuOpen}
            className="flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </Container>

      {menuOpen && (
        <nav className="border-t border-border bg-background/95 backdrop-blur-md lg:hidden">
          <Container className="flex flex-col py-3">
            {NAV_LINKS.map((item) => (
              <a
                key={item.label}
                href={item.href}
                onClick={closeMenu}
                className="rounded-lg px-2 py-2.5 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
              >
                {item.label}
              </a>
            ))}
            {NAV_GROUPS.map((group) => (
              <div key={group.label} className="mt-2 border-t border-border/60 pt-2">
                <p className="px-2 py-1 text-[0.7rem] font-bold tracking-wide text-muted-foreground/70 uppercase">
                  {group.label}
                </p>
                {group.items.map((item) => (
                  <a
                    key={item.label}
                    href={item.href}
                    onClick={closeMenu}
                    className="block rounded-lg px-2 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                  >
                    {item.label}
                  </a>
                ))}
              </div>
            ))}
            <a
              href={LINKS.viglet}
              onClick={closeMenu}
              className="mt-3 inline-flex w-fit items-center gap-1 px-2 text-xs font-semibold text-muted-foreground transition-colors hover:text-foreground"
            >
              part of viglet.org
              <ArrowUpRight className="size-3" />
            </a>
          </Container>
        </nav>
      )}
    </header>
  );
}
