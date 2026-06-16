import { UserMenu } from "@viglet/viglet-design-system";

import { ROUTES } from "@/app/routes.const";
import type { ShCurrentUser } from "@/models/auth/current-user";

interface HeaderUserMenuProps {
  user: ShCurrentUser;
}

/**
 * Thin wrapper over the design-system UserMenu that wires Shio's logout flow.
 * A full-page navigation to /logout lets the browser follow the backend's
 * redirect to the Keycloak end-session endpoint when Keycloak is enabled
 * (and to /login otherwise). An XHR POST would swallow that redirect, so the
 * browser must drive the logout. Mirrors Turing's logout behavior.
 */
export function HeaderUserMenu({ user }: Readonly<HeaderUserMenuProps>) {
  return (
    <UserMenu
      user={{
        username: user.username,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
      }}
      onSignOut={() => {
        globalThis.location.href = ROUTES.LOGOUT;
      }}
    />
  );
}
