import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { useUnifiedEditor } from "@/contexts/unified-editor.context"
import { Sparkles } from "lucide-react"

/**
 * Global preference pill that routes both create and edit flows through
 * the unified post model API (/api/v2/post-unified) or the legacy
 * ShPostAttr-based API. Persisted in localStorage via UnifiedEditorContext.
 */
export function UnifiedEditorToggle() {
  const { enabled, setEnabled } = useUnifiedEditor()

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex items-center gap-1.5 px-2 h-8 rounded-md border bg-background/50 hover:bg-muted transition-colors">
          <Sparkles
            className={`h-3.5 w-3.5 transition-colors ${enabled ? "text-orange-500" : "text-muted-foreground"}`}
          />
          <Label htmlFor="unified-editor-toggle" className="text-xs cursor-pointer select-none hidden lg:inline">
            Unified
          </Label>
          <Switch
            id="unified-editor-toggle"
            checked={enabled}
            onCheckedChange={setEnabled}
            className="scale-75"
          />
        </div>
      </TooltipTrigger>
      <TooltipContent side="bottom">
        {enabled
          ? "Using the unified post model (JSON payload + sh_post_unified)"
          : "Using the legacy post API (shPostAttrs EAV tables)"}
      </TooltipContent>
    </Tooltip>
  )
}
