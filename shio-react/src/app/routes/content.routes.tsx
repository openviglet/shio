import { Route } from "react-router-dom"
import SiteListPage from "../console/content/site.list.page"
import SiteEditPage from "../console/content/site.edit.page"
import SiteNewPage from "../console/content/site.new.page"
import SiteImportPage from "../console/content/site.import.page"
import ObjectChildrenPage from "../console/content/object.children.page"
import FolderEditPage from "../console/content/folder.edit.page"
import FolderNewPage from "../console/content/folder.new.page"
import PostEditPage from "../console/content/post.edit.page"
import PostNewPage from "../console/content/post.new.page"
import PostUnifiedEditPage from "../console/content/post-unified.edit.page"
import PostTypeListPage from "../console/content/post-type.list.page"
import PostTypeEditPage from "../console/content/post-type.edit.page"
import PostTypeNewPage from "../console/content/post-type.new.page"
import PostTypeSelectPage from "../console/content/post-type-select.page"
import SearchPage from "../console/content/search.page"
import PreviewPage from "../console/content/preview.page"

export const ContentRoutes = (
  <>
    <Route path="content" element={<SiteListPage />} />
    <Route path="content/site/new" element={<SiteNewPage />} />
    <Route path="content/site/import" element={<SiteImportPage />} />
    <Route path="content/site/:siteId" element={<SiteEditPage />} />
    <Route path="content/object/:objectId" element={<ObjectChildrenPage />} />
    <Route path="content/folder/:folderId/edit" element={<FolderEditPage />} />
    <Route path="content/folder/:folderId/new-folder" element={<FolderNewPage />} />
    <Route path="content/folder/:folderId/post-type-select" element={<PostTypeSelectPage />} />
    <Route path="content/folder/:folderId/post-type/:postTypeId/new" element={<PostNewPage />} />
    <Route path="content/post/:postId" element={<PostEditPage />} />
    <Route path="content/post-unified/:postId" element={<PostUnifiedEditPage />} />
    <Route path="content/post-type" element={<PostTypeListPage />} />
    <Route path="content/post-type/new" element={<PostTypeNewPage />} />
    <Route path="content/post-type/:postTypeId" element={<PostTypeEditPage />} />
    <Route path="content/search" element={<SearchPage />} />
    <Route path="preview/:objectId" element={<PreviewPage />} />
  </>
)
