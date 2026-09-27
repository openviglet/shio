import { Loader2 } from "lucide-react"
import { useTranslation } from "react-i18next"
import { cn } from "@/lib/utils"

interface Props {
  /** What is loading, for a screen reader. Defaults to the console's own "Loading…". */
  label?: string
  /** Set when visible words beside it already say it, so the reader does not hear them twice. */
  labelled?: boolean
  className?: string
}

/**
 * SH1053 — the one spinner the console draws by hand, for a region that is waiting.
 *
 * <p>A spinner inside a button is not this: it is the button's `loading` (VDS143), which draws the
 * spinner, sets `aria-busy` and keeps focus where `disabled` would drop it. This is for a spinner
 * standing in for content — a list, a panel, a frame — which used to be an icon and nothing else,
 * so a screen reader heard silence. The icon is hidden and the words are the spinner.</p>
 */
export function Spinner({ label, labelled = false, className }: Readonly<Props>) {
  const { t } = useTranslation()
  return (
    <span aria-busy="true" className="inline-flex items-center">
      <Loader2 aria-hidden="true" className={cn("h-4 w-4 animate-spin", className)} />
      {!labelled && <span className="sr-only">{label ?? t("common.loading")}</span>}
    </span>
  )
}
