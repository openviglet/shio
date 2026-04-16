import axios from "axios";
import { UserMenu } from "@viglet/viglet-design-system";

import type { ShCurrentUser } from "@/models/auth/current-user";

interface HeaderUserMenuProps {
  user: ShCurrentUser;
}

/**
 * Thin wrapper over the design-system UserMenu that wires Shio's logout flow.
 * Shio uses Spring Boot native authentication — logout posts to /logout
 * and redirects to the login page.
 */
export function HeaderUserMenu({ user }: Readonly<HeaderUserMenuProps>) {
  const handleSignOut = async () => {
    await axios.post("/logout", {}).catch(() => {});
    globalThis.location.href = "/login";
  };

  return (
    <UserMenu
      user={{
        username: user.username,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
      }}
      onSignOut={handleSignOut}
    />
  );
}
