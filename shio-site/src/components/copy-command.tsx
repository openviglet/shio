import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { cn } from "@/lib/utils";

/** A copy-paste command line with a copy button. */
export function CopyCommand({
  command,
  className,
}: {
  command: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);

  const onCopy = () => {
    navigator.clipboard
      ?.writeText(command)
      .then(() => {
        setCopied(true);
        window.setTimeout(() => setCopied(false), 1500);
      })
      .catch(() => {
        /* clipboard blocked: no-op */
      });
  };

  return (
    <div
      className={cn(
        // inline-flex keeps it a compact pill on desktop; max-w-full caps it to
        // the parent so a long command can't push the page wider on mobile; the
        // command itself scrolls horizontally instead (min-w-0 lets it shrink).
        "inline-flex max-w-full items-center gap-3 rounded-lg border border-white/15 bg-black/30 px-4 py-2.5 font-mono text-sm text-[#f0ded6] backdrop-blur",
        className
      )}
    >
      <span className="shrink-0 select-none text-[#ff8a6b]">$</span>
      <code className="min-w-0 overflow-x-auto whitespace-nowrap">{command}</code>
      <button
        type="button"
        onClick={onCopy}
        aria-label="Copy command"
        className="ml-1 shrink-0 rounded-md p-1 text-[#a89189] transition-colors hover:bg-white/10 hover:text-white"
      >
        {copied ? (
          <Check className="size-4 text-emerald-400" />
        ) : (
          <Copy className="size-4" />
        )}
      </button>
    </div>
  );
}
