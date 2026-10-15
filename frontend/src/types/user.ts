export type UserRole = "ADMIN" | "STUDENT";

/** Mirrors UserResponse — the backend never returns password hashes. */
export interface User {
  id: number;
  username: string;
  email: string;
  role: UserRole;
}
