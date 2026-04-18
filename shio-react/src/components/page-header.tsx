import { Separator } from "@/components/ui/separator"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useCurrentUser } from "@/contexts/user.context"
import { useDiscovery } from "@/hooks/use-discovery"
import { IconChevronLeft } from "@tabler/icons-react"
import React, { useState } from "react"
import { useTranslation } from "react-i18next"
import { NavLink, useNavigate } from "react-router-dom"
import { LanguageSwitcher, VigletAppSwitcher } from "@viglet/viglet-design-system"
import { HeaderUserMenu } from "./header-user-menu"
import { ModeToggle } from "./mode-toggle"
import { UnifiedEditorToggle } from "./unified-editor-toggle"
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from "./ui/breadcrumb"

interface MyComponentProps { turIcon?: React.ElementType; title: string; urlBase?: string }

export const PageHeader: React.FC<MyComponentProps> = ({ turIcon: TurIcon, title, urlBase }) => {
  const { items } = useBreadcrumb()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const discovery = useDiscovery()
  const { user } = useCurrentUser()
  const [appSwitcherOpen, setAppSwitcherOpen] = useState(false)
  const lastItem = items.length > 0 ? items[items.length - 1] : null
  const parentItem = items.length > 1 ? items[items.length - 2] : null

  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1 hidden md:flex" />
        <Separator orientation="vertical" className="mx-2 data-[orientation=vertical]:h-4 hidden md:block" />
        <div className="hidden md:flex items-center gap-1">
          {TurIcon && (
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-linear-to-br from-orange-500/10 to-amber-500/10 dark:from-orange-500/20 dark:to-amber-500/20 ring-1 ring-orange-500/10 dark:ring-orange-400/10 mr-1">
              <TurIcon className="size-4.5! text-orange-600 dark:text-orange-400" />
            </div>
          )}
          {urlBase ? (
            <Breadcrumb>
              <BreadcrumbList>
                {items.map((item, index) => (
                  <React.Fragment key={item.label}>
                    {index > 0 && <BreadcrumbSeparator />}
                    <BreadcrumbItem>
                      {item.href ? <BreadcrumbLink asChild><NavLink to={item.href}>{item.label}</NavLink></BreadcrumbLink> : <BreadcrumbPage>{item.label}</BreadcrumbPage>}
                    </BreadcrumbItem>
                  </React.Fragment>
                ))}
              </BreadcrumbList>
            </Breadcrumb>
          ) : <h1 className="text-base font-semibold">{title}</h1>}
        </div>
        <div className="flex md:hidden items-center gap-2 min-w-0 flex-1">
          {parentItem?.href && (
            <button type="button" title={t("common.goBack")} onClick={() => navigate(parentItem.href!)} className="shrink-0 flex items-center justify-center size-8 rounded-lg hover:bg-muted transition-colors">
              <IconChevronLeft className="size-4" />
            </button>
          )}
          {TurIcon && (
            <div className="flex shrink-0 h-7 w-7 items-center justify-center rounded-lg bg-linear-to-br from-orange-500/10 to-amber-500/10 dark:from-orange-500/20 dark:to-amber-500/20 ring-1 ring-orange-500/10 dark:ring-orange-400/10">
              <TurIcon className="size-3.5! text-orange-600 dark:text-orange-400" />
            </div>
          )}
          <span className="text-sm font-semibold truncate">{lastItem?.label ?? title}</span>
        </div>
        <div className="ml-auto flex items-center gap-1">
          <div className="hidden md:flex items-center gap-1">
            <UnifiedEditorToggle />
            <LanguageSwitcher />
            <ModeToggle />
            {discovery?.multiTenant && (
              <VigletAppSwitcher
                open={appSwitcherOpen}
                onToggle={() => setAppSwitcherOpen((o) => !o)}
                activeProduct="shio"
                /* SidebarInset variant="inset" adds m-2 (8px) margin +
                   header is 48px → total offset from viewport top is 56px */
                headerHeight={56}
              />
            )}
            {user && <HeaderUserMenu user={user} />}
          </div>
          <SidebarTrigger className="md:hidden" />
        </div>
      </div>
    </header>
  )
}
