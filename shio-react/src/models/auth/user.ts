export type ShUser = {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  password?: string;
  admin?: boolean;
  /** SH1238 — when this person last signed in to the console; absent if they never have. */
  lastLogin?: string | null;
};
