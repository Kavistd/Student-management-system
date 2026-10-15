import axios from "axios";

import api from "@/lib/api";
import type { UserRole } from "@/types/user";

/** Mirrors AuthUserResponse. Tokens never reach JavaScript; they live in HttpOnly cookies. */
export interface AuthUser {
  id: number;
  username: string;
  email: string;
  role: UserRole;
}

export interface RegisterPayload {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  password: string;
}

export async function signIn(email: string, password: string, rememberMe: boolean) {
  const response = await api.post<AuthUser>("/api/auth/login", { email, password, rememberMe });
  return response.data;
}

export async function signOut() {
  await api.post("/api/auth/logout");
}

export async function register(payload: RegisterPayload) {
  await api.post("/api/auth/register", payload);
}

/** Returns the signed-in user, or null when there is no valid session. */
export async function fetchCurrentUser(): Promise<AuthUser | null> {
  try {
    const response = await api.get<AuthUser>("/api/auth/me");
    return response.data;
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 401) return null;
    throw err;
  }
}

const AREA_PREFIXES: Record<UserRole, string[]> = {
  ADMIN: ["/admin/", "/students", "/courses"],
  STUDENT: ["/student/"],
};

export function homePathFor(role: UserRole) {
  return role === "ADMIN" ? "/admin/dashboard" : "/student/dashboard";
}

/**
 * Where to go after sign-in. Only same-site paths inside the user's own area
 * are honoured, so a crafted `?next=` can't redirect off-site or into the
 * wrong portal.
 */
export function postLoginPath(role: UserRole, next: string | null) {
  if (
    next &&
    next.startsWith("/") &&
    !next.startsWith("//") &&
    !next.includes("\\") &&
    AREA_PREFIXES[role].some((prefix) => next === prefix.replace(/\/$/, "") || next.startsWith(prefix))
  ) {
    return next;
  }
  return homePathFor(role);
}
