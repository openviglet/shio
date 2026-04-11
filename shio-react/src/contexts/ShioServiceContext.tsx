import { ShSiteService } from "@/services/site/site.service"
import { ShFolderService } from "@/services/folder/folder.service"
import { ShPostService } from "@/services/post/post.service"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSearchService } from "@/services/search/search.service"
import { ShWidgetService } from "@/services/widget/widget.service"
import { ShUniversalEditorService } from "@/services/universal-editor/universal-editor.service"
import React from "react"

interface ShioServiceContextType {
  siteService: ShSiteService
  folderService: ShFolderService
  postService: ShPostService
  postTypeService: ShPostTypeService
  searchService: ShSearchService
  widgetService: ShWidgetService
  universalEditorService: ShUniversalEditorService
}

const ShioServiceContext = React.createContext<ShioServiceContextType | null>(null)

export function ShioServiceProvider({ children }: { children: React.ReactNode }) {
  const services = React.useMemo(() => ({
    siteService: new ShSiteService(),
    folderService: new ShFolderService(),
    postService: new ShPostService(),
    postTypeService: new ShPostTypeService(),
    searchService: new ShSearchService(),
    widgetService: new ShWidgetService(),
    universalEditorService: new ShUniversalEditorService(),
  }), [])

  return <ShioServiceContext value={services}>{children}</ShioServiceContext>
}

export function useShioService(): ShioServiceContextType {
  const ctx = React.useContext(ShioServiceContext)
  if (!ctx) throw new Error("useShioService must be used within ShioServiceProvider")
  return ctx
}
