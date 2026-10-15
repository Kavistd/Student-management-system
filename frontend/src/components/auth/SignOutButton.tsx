"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LoaderCircle, LogOut } from "lucide-react";

import { useAuth } from "@/components/auth/AuthProvider";
import { toApiError } from "@/lib/apiError";

/** Revokes the session on the server, then returns to the sign-in page. */
export default function SignOutButton({
  className,
  onSignedOut,
}: {
  className: string;
  onSignedOut?: () => void;
}) {
  const { signOut } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setBusy(true);
    setError(null);
    try {
      await signOut();
      onSignedOut?.();
      router.replace("/login");
    } catch (err) {
      setError(toApiError(err).message);
      setBusy(false);
    }
  }

  return (
    <div>
      <button type="button" onClick={() => void handleClick()} disabled={busy} className={className}>
        {busy ? (
          <LoaderCircle size={18} className="animate-spin" aria-hidden />
        ) : (
          <LogOut size={18} aria-hidden />
        )}
        {busy ? "Signing out…" : "Sign out"}
      </button>
      {error && (
        <p role="alert" className="mt-2 px-1 text-xs text-red-400">
          Sign-out failed: {error}
        </p>
      )}
    </div>
  );
}
