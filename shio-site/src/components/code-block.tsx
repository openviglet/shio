import { type ReactNode } from "react";
import { cn } from "@/lib/utils";

// Lightweight, dependency-free, SSR-safe syntax highlight. A deterministic
// string → token mapping, so the prerendered markup and the client hydration
// match exactly (no shiki / react-syntax-highlighter; this site stays
// dependency-light on purpose). It covers the few languages used in the
// snippets here: bash, json, yaml, tsx, and front-mattered Markdown.
//
// The `(?<!:)` before `//` keeps URLs (https://…) out of the comment class;
// `#` covers bash and YAML.
//
// Two bounds on the string class, both from real output on this site. A single
// quote is only an opening quote when it does NOT follow a word character;
// otherwise the apostrophe in "the source's appearance" opens a string that
// runs to the next apostrophe several lines down, and a paragraph of prose in a
// terminal snippet renders as one green literal. And neither quote may span a
// newline: these snippets have no multi-line literals, so stopping at the line
// end bounds the damage of an unbalanced quote to the line that has it.
const SYNTAX_RE =
  /(#[^\n]*|(?<!:)\/\/[^\n]*)|("(?:[^"\\\n]|\\.)*"|(?<![\w'])'(?:[^'\\\n]|\\.)*')|\b(true|false|null|const|let|function|return|import|from|export|async|await|new)\b|(\b\d+(?:\.\d+)?\b)/g;

// index 0..3 mirrors the regex capture groups: comment, string, keyword, number.
const TOKEN_CLASS = [
  // A comment is dim by convention, but not below ~5:1 on #17100e: half the
  // snippets on this page put the explanation *in* the comment.
  "text-[#a89189] italic",
  "text-[#7dd3a0]",
  "text-[#ddd6fe]",
  "text-[#f5c46b]",
];

function highlightCode(code: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let last = 0;
  let key = 0;
  SYNTAX_RE.lastIndex = 0;
  let m: RegExpExecArray | null = SYNTAX_RE.exec(code);
  while (m !== null) {
    if (m.index > last) nodes.push(code.slice(last, m.index));
    let gi = 3;
    if (m[1]) gi = 0;
    else if (m[2]) gi = 1;
    else if (m[3]) gi = 2;
    nodes.push(
      <span key={key++} className={TOKEN_CLASS[gi]}>
        {m[0]}
      </span>
    );
    last = m.index + m[0].length;
    m = SYNTAX_RE.exec(code);
  }
  if (last < code.length) nodes.push(code.slice(last));
  return nodes;
}

export function CodeBlock({
  caption,
  code,
  className,
  highlight = true,
}: Readonly<{
  caption?: string;
  code: string;
  className?: string;
  /** Off for a file tree, where the token classes only add noise. */
  highlight?: boolean;
}>) {
  return (
    <figure
      className={cn(
        "overflow-hidden rounded-xl border border-black/10 bg-[#17100e] text-[#ede2dd] shadow-lg dark:border-white/10",
        className
      )}
    >
      {caption && (
        <figcaption className="flex items-center gap-2 border-b border-white/10 px-4 py-2 font-mono text-xs font-semibold text-[#b39c94]">
          <span className="flex gap-1.5" aria-hidden>
            <span className="size-2 rounded-full bg-[#7c3aed]/70" />
            <span className="size-2 rounded-full bg-[#f5c46b]/70" />
            <span className="size-2 rounded-full bg-[#7dd3a0]/70" />
          </span>
          {caption}
        </figcaption>
      )}
      <pre className="overflow-x-auto px-4 py-4 text-[0.82rem] leading-relaxed">
        <code className="font-mono">{highlight ? highlightCode(code) : code}</code>
      </pre>
    </figure>
  );
}
