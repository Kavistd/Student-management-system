"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { SESSION_EXPIRED_EVENT } from "@/lib/api";
import { toApiError, type ApiError } from "@/lib/apiError";
import * as auth from "@/lib/auth";
import type { AuthUser } from "@/lib/auth";

export type AuthState =
  | { status: "loading" }
  | { status: "authenticated"; user: AuthUser }
  | { status: "unauthenticated" }
  | { status: "error"; error: ApiError };

interface AuthContextValue {
  state: AuthState;
  signIn: (email: string, password: string, rememberMe: boolean) => Promise<AuthUser>;
  signOut: () => Promise<void>;
  reload: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Restores the session on load by asking the backend who is signed in.
 * The HttpOnly cookies are the only credential; nothing is kept in storage.
 */
export default function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const [checkId, setCheckId] = useState(0);

  useEffect(() => {
    let active = true;
    auth
      .fetchCurrentUser()
      .then((user) => {
        if (!active) return;
        setState(user ? { status: "authenticated", user } : { status: "unauthenticated" });
      })
      .catch((err: unknown) => {
        if (active) setState({ status: "error", error: toApiError(err) });
      });
    return () => {
      active = false;
    };
  }, [checkId]);

  useEffect(() => {
    const onExpired = () => setState({ status: "unauthenticated" });
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const reload = useCallback(() => {
    setState({ status: "loading" });
    setCheckId((id) => id + 1);
  }, []);

  const signIn = useCallback(async (email: string, password: string, rememberMe: boolean) => {
    const user = await auth.signIn(email, password, rememberMe);
    setState({ status: "authenticated", user });
    return user;
  }, []);

  const signOut = useCallback(async () => {
    await auth.signOut();
    setState({ status: "unauthenticated" });
  }, []);

  const value = useMemo(
    () => ({ state, signIn, signOut, reload }),
    [state, signIn, signOut, reload]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}

/** The signed-in user; only call inside a RoleGuard-protected subtree. */
export function useAuthUser(): AuthUser | null {
  const { state } = useAuth();
  return state.status === "authenticated" ? state.user : null;
}
