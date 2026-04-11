import { Route } from "react-router-dom"
import EditorSitesPage from "../console/universal-editor/editor-sites.page"
import VisualEditorPage from "../console/universal-editor/visual-editor.page"

export const UniversalEditorRoutes = (
  <>
    <Route path="universal-editor" element={<EditorSitesPage />} />
    <Route path="universal-editor/external" element={<VisualEditorPage />} />
    <Route path="universal-editor/:objectId" element={<VisualEditorPage />} />
  </>
)
