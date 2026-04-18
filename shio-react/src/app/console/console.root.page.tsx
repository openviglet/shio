import { AppFooter } from "@/components/app-footer"
import { AppSidebar } from "@/components/app-sidebar"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { UserProvider } from "@/contexts/user.context"
import React from "react"
import { Outlet } from "react-router-dom"

export default function ConsoleRootPage() {
  return (
    <UserProvider>
      <SidebarProvider
        defaultOpen={false}
        style={{
          "--sidebar-width": "calc(var(--spacing) * 72)",
          "--header-height": "calc(var(--spacing) * 12)",
        } as React.CSSProperties}
        className="min-h-svh"
      >
        <AppSidebar variant="inset" />
        <SidebarInset>
          <Outlet />
          <AppFooter />
        </SidebarInset>
      </SidebarProvider>
    </UserProvider>
  )
}
