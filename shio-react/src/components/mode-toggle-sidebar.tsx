import { Moon, Sun } from "lucide-react"
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import { useTheme } from "@/components/theme-provider"

export function ModeToggleSidebar() {
  const { theme, setTheme } = useTheme()
  const isDark = theme === "dark"
  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <SidebarMenuButton onClick={() => setTheme(isDark ? "light" : "dark")} tooltip={isDark ? "Light mode" : "Dark mode"}>
          {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
          <span>{isDark ? "Light mode" : "Dark mode"}</span>
        </SidebarMenuButton>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
