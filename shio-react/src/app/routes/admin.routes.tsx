import { Navigate, Route } from "react-router-dom"
import AdminRootPage from "../console/admin/admin.root.page"
import AdminUsersListPage from "../console/admin/admin.users.list.page"
import AdminUserPage from "../console/admin/admin.user.page"
import AdminGroupsListPage from "../console/admin/admin.groups.list.page"
import AdminGroupPage from "../console/admin/admin.group.page"
import AdminRolesListPage from "../console/admin/admin.roles.list.page"
import AdminRolePage from "../console/admin/admin.role.page"
import ApiTokenListPage from "../console/admin/api-token/api-token.list.page"
import ApiTokenPage from "../console/admin/api-token/api-token.page"
import TenantListPage from "../console/admin/tenant/tenant.list.page"
import TenantPage from "../console/admin/tenant/tenant.page"
import { ROUTES } from "../routes.const"

export const AdminRoutes = (
  <Route path="admin" element={<AdminRootPage />}>
    <Route index element={<Navigate to={`${ROUTES.ADMIN_ROOT}/users`} replace />} />
    <Route path="users" element={<AdminUsersListPage />} />
    <Route path="users/:userId" element={<AdminUserPage />} />
    <Route path="groups" element={<AdminGroupsListPage />} />
    <Route path="groups/:groupId" element={<AdminGroupPage />} />
    <Route path="roles" element={<AdminRolesListPage />} />
    <Route path="roles/:roleId" element={<AdminRolePage />} />
    <Route path="tokens" element={<ApiTokenListPage />} />
    <Route path="tokens/:tokenId" element={<ApiTokenPage />} />
    <Route path="tenants" element={<TenantListPage />} />
    <Route path="tenants/:tenantId" element={<TenantPage />} />
  </Route>
)
