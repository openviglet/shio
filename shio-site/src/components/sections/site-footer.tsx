import { Container, Logo } from "@/components/brand";
import { LINKS } from "@/lib/site-content";
import { reopenConsent } from "@/lib/analytics";
import { withBase } from "@/lib/base";

const COLUMNS: { heading: string; links: { label: string; href: string }[] }[] = [
  {
    heading: "Ways to use it",
    links: [
      { label: "In the console, by hand", href: withBase("/console/") },
      { label: "With a coding agent", href: withBase("/claude-code/") },
      { label: "As files in your repository", href: withBase("/features/content-as-files/") },
      { label: "Through the delivery API", href: withBase("/features/delivery/") },
      { label: "All four, side by side", href: withBase("/#paths") },
      { label: "Who runs it, and how", href: withBase("/#use-cases") },
    ],
  },
  {
    heading: "The product",
    links: [
      { label: "What the console does", href: withBase("/console/#capabilities") },
      { label: "What stops an agent write", href: withBase("/curator/") },
      { label: "The ten laws", href: withBase("/#laws") },
      { label: "One call, and its price", href: withBase("/#one-call") },
      { label: "MCP + CLI surface", href: withBase("/#surface") },
      { label: "Rendering and design tokens", href: withBase("/features/rendering/") },
      { label: "Blueprints", href: withBase("/#blueprints") },
      { label: "Replicate a site", href: withBase("/replicate/") },
      { label: "Compare", href: withBase("/compare/") },
      { label: "Self-host", href: withBase("/#self-host") },
    ],
  },
  {
    heading: "Open and inspectable",
    links: [
      { label: "Documentation", href: LINKS.docs },
      { label: "Source", href: LINKS.repo },
      { label: "Roadmap", href: LINKS.roadmap },
      { label: "Changelog", href: LINKS.changelog },
      { label: "npm", href: LINKS.npm },
      { label: "Viglet Turing", href: LINKS.turing },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="bg-[#0f0a09] text-[#a89189]">
      <Container className="py-14">
        <div className="grid gap-8 sm:grid-cols-2 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <Logo className="text-[#f6ece8]" />
            <p className="mt-3 max-w-xs text-sm leading-relaxed">
              An open-source, agent-ready CMS with four ways in: a full console, a
              coding agent, files in your repository, and the delivery API. Same
              content, same drafts, same publish button.
            </p>
          </div>
          {COLUMNS.map((col) => (
            <div key={col.heading}>
              <h3 className="text-xs font-bold tracking-[0.06em] text-[#f6ece8] uppercase">
                {col.heading}
              </h3>
              <ul className="mt-4 space-y-2.5">
                {col.links.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      target={link.href.startsWith("http") ? "_blank" : undefined}
                      rel={link.href.startsWith("http") ? "noreferrer" : undefined}
                      className="text-sm transition-colors hover:text-[#f6ece8]"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-12 flex flex-col justify-between gap-2 border-t border-[#241a17] pt-6 text-xs text-[#7a6660] sm:flex-row">
          <span>
            &copy; {new Date().getFullYear()} Viglet Shio &middot;{" "}
            <a href={LINKS.license} target="_blank" rel="noreferrer" className="hover:text-[#f6ece8]">
              Apache 2.0
            </a>
          </span>
          <span className="space-x-3">
            <a href={LINKS.viglet} target="_blank" rel="noreferrer" className="hover:text-[#f6ece8]">
              viglet.org
            </a>
            <a href={LINKS.docs} className="hover:text-[#f6ece8]">
              Docs
            </a>
            <a href={withBase("/llms.txt")} className="font-mono hover:text-[#f6ece8]">
              llms.txt
            </a>
            <button type="button" onClick={reopenConsent} className="hover:text-[#f6ece8]">
              Cookie settings
            </button>
          </span>
        </div>
      </Container>
    </footer>
  );
}
