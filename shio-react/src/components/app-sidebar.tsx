import {
  IconHome,
  IconFileText,
  IconForms,
  IconShieldCog,
  IconSettings,
} from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { useCurrentUser } from "@/contexts/user.context"
import { Layers } from "lucide-react"
import { ModeToggleSidebar } from "@/components/mode-toggle-sidebar"

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const { toggleSidebar, state, isMobile } = useSidebar()
  const isCollapsed = state === "collapsed"

  const navGroups = React.useMemo(() => [
    {
      items: [
        { title: t("sidebar.dashboard"), url: ROUTES.HOME, icon: IconHome },
      ],
    },
    {
      label: t("sidebar.content"),
      items: [
        { title: t("content.sites"), url: `${ROUTES.CONSOLE}/content`, icon: IconFileText },
        { title: t("content.postTypes"), url: ROUTES.POST_TYPE_ROOT, icon: IconForms },
      ],
    },
    {
      label: t("sidebar.administration"),
      items: [
        { title: t("admin.title"), url: ROUTES.ADMIN_ROOT, icon: IconShieldCog },
        { title: t("config.title"), url: ROUTES.CONFIG_ROOT, icon: IconSettings },
      ],
    },
  ], [t])

  return (
    <Sidebar collapsible="icon" side={isMobile ? "right" : "left"} {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={toggleSidebar} className="data-[slot=sidebar-menu-button]:p-1.5!">
              <Layers className="size-6!" />
              {!isCollapsed && (
                <div className="grid flex-1 text-left leading-tight">
                  <span className="text-sm font-bold tracking-tight">{t("sidebar.brandName")}</span>
                  <span className="text-[10px] text-muted-foreground">{t("sidebar.brandTagline")}</span>
                </div>
              )}
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain groups={navGroups} />
      </SidebarContent>
      <SidebarFooter>
        <ModeToggleSidebar />
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}
