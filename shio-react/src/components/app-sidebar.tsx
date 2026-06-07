import {
  IconHome,
  IconFileText,
  IconForms,
  IconShieldCog,
  IconBrandGraphql,
  IconLayersIntersect,
  IconKey,
  IconBuildingSkyscraper,
} from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
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
import { ShLogo } from "@/components/logo/sh-logo"


export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation()
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
        { title: t("sidebar.universalEditor"), url: ROUTES.UNIVERSAL_EDITOR, icon: IconLayersIntersect },
      ],
    },
    {
      label: t("sidebar.administration"),
      items: [
        { title: t("admin.title"), url: ROUTES.ADMIN_ROOT, icon: IconShieldCog },
        { title: t("apiToken.title"), url: ROUTES.ADMIN_TOKENS, icon: IconKey },
        { title: t("tenantAdmin.title"), url: ROUTES.ADMIN_TENANTS, icon: IconBuildingSkyscraper },
        { title: t("graphql.title"), url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql },
      ],
    },
  ], [t])

  return (
    <Sidebar collapsible="icon" side={isMobile ? "right" : "left"} {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={toggleSidebar} className="data-[slot=sidebar-menu-button]:p-1.5!">
              <ShLogo className="size-6!" />
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
      </SidebarFooter>
    </Sidebar>
  )
}
