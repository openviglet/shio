import { Layers } from "lucide-react"

export function AppFooter() {
  return (
    <footer className="mt-auto">
      <div className="mx-6 border-t" />
      <div className="px-6 py-3 flex items-center justify-center gap-2 flex-wrap">
        <Layers className="size-4 opacity-50" />
        <span className="text-xs text-muted-foreground/70">Viglet Shio CMS</span>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://viglet.com/shio" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">Docs</a>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://github.com/openviglet/shio" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">GitHub</a>
      </div>
    </footer>
  )
}
