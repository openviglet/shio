// viglet-ds-allow-duplicate NavMain -- the package's NavItem is {title, url, icon} and has no
// field for SH207's unread-shaped count, which is the whole reason this one exists: the review
// queue's badge, and the `badgeLabel` a reader who cannot see a number in a corner depends on.
// This is exactly the case §LIV.9 predicted — "the moment a component needs one prop it does not
// have, the cheapest path is a local copy" — so it is written down rather than left to be found.
import { SidebarGroup, SidebarGroupLabel, SidebarMenu, SidebarMenuBadge, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import type { Icon } from "@tabler/icons-react"
import { NavLink, useLocation } from "react-router-dom"

interface NavItem {
  title: string
  url: string
  icon: Icon
  /**
   * SH207 — an unread-shaped count, absent when there is nothing to say.
   *
   * Absent rather than `0`: a badge that is always there is a badge nobody reads, and this one
   * has to be read. Zero is the state a curator has to be able to ignore safely, and the way to
   * make that true is to not draw it.
   */
  badge?: number
  /** What the badge means, for a reader who cannot see a number in a corner. */
  badgeLabel?: string
}

interface NavGroup {
  label?: string
  items: NavItem[]
}

export function NavMain({ groups }: { groups: NavGroup[] }) {
  const { pathname } = useLocation()

  return (
    <>
      {groups.map((group, gi) => (
        <SidebarGroup key={gi}>
          {group.label && <SidebarGroupLabel>{group.label}</SidebarGroupLabel>}
          <SidebarMenu>
            {group.items.map((item) => (
              <SidebarMenuItem key={item.url}>
                <SidebarMenuButton asChild isActive={pathname.startsWith(item.url)} tooltip={item.title}>
                  <NavLink to={item.url}>
                    <item.icon className="size-6!" />
                    <span>{item.title}</span>
                  </NavLink>
                </SidebarMenuButton>
                {item.badge ? (
                  <SidebarMenuBadge aria-label={item.badgeLabel}>{item.badge}</SidebarMenuBadge>
                ) : null}
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarGroup>
      ))}
    </>
  )
}
