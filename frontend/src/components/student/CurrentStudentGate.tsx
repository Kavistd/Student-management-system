import type { ReactNode } from "react";
import { LockKeyhole } from "lucide-react";

import { ErrorState, LoadingState } from "@/components/ui/StatePanel";
import type { CurrentStudentState } from "@/hooks/useCurrentStudent";
import type { Student } from "@/types/student";

export function SignedOutState({ subject, compact = false }: { subject: string; compact?: boolean }) {
  return (
    <div
      role="status"
      className={`flex flex-col items-center text-center ${compact ? "p-6" : "p-12"}`}
    >
      <div className="rounded-2xl bg-amber-50 p-4 text-amber-600">
        <LockKeyhole size={compact ? 24 : 30} aria-hidden />
      </div>
      <h3 className="mt-4 font-semibold text-slate-800">Sign in to view {subject}</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
        Your session has ended. Sign in again to see this information; it is only loaded
        for your own account.
      </p>
    </div>
  );
}

/** Renders `children` with the current student, or the matching state panel. */
export default function CurrentStudentGate({
  state,
  subject,
  compact,
  children,
}: {
  state: CurrentStudentState;
  subject: string;
  compact?: boolean;
  children: (student: Student) => ReactNode;
}) {
  switch (state.status) {
    case "loading":
      return <LoadingState label={`Loading ${subject}…`} />;
    case "signedOut":
      return <SignedOutState subject={subject} compact={compact} />;
    case "error":
      return <ErrorState error={state.error} onRetry={state.retry} compact={compact} />;
    case "ready":
      return <>{children(state.student)}</>;
  }
}
