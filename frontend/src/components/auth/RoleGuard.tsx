"use client";

import { useEffect, type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ShieldAlert } from "lucide-react";

import { useAuth } from "@/components/auth/AuthProvider";
import { ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { homePathFor } from "@/lib/auth";
import type { UserRole } from "@/types/user";

const areaNames: Record<UserRole, string> = {
  ADMIN: "administrators",
  STUDENT: "students",
};

function FullPage({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white shadow-sm">
        {children}
      </div>
    </div>
  );
}

/**
 * Renders its children only for a signed-in user with the given role, so the
 * pages' data requests never fire without a session. This is a UX layer; the
 * backend enforces the same rules on every request.
 */
export default function RoleGuard({ role, children }: { role: UserRole; children: ReactNode }) {
  const { state, reload } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (state.status === "unauthenticated") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [state.status, router, pathname]);

  if (state.status === "loading" || state.status === "unauthenticated") {
    return (
      <FullPage>
        <LoadingState
          label={state.status === "loading" ? "Checking your session…" : "Redirecting to sign in…"}
        />
      </FullPage>
    );
  }

  if (state.status === "error") {
    return (
      <FullPage>
        <ErrorState error={state.error} onRetry={reload} />
      </FullPage>
    );
  }

  if (state.user.role !== role) {
    return (
      <FullPage>
        <div role="alert" className="flex flex-col items-center p-10 text-center">
          <div className="rounded-2xl bg-red-50 p-4 text-red-600">
            <ShieldAlert size={30} aria-hidden />
          </div>
          <h1 className="mt-4 font-semibold text-slate-800">This area is for {areaNames[role]}</h1>
          <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">
            You&apos;re signed in as {state.user.email}, which doesn&apos;t have access to this page.
          </p>
          <Link
            href={homePathFor(state.user.role)}
            className="mt-5 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700"
          >
            Go to my dashboard
          </Link>
        </div>
      </FullPage>
    );
  }

  return <>{children}</>;
}
