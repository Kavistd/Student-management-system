import type { ReactNode } from "react";
import {
  CircleAlert,
  Inbox,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
  ShieldAlert,
  WifiOff,
} from "lucide-react";

import type { ApiError } from "@/lib/apiError";

export function LoadingState({ label }: { label: string }) {
  return (
    <div
      role="status"
      className="flex items-center justify-center gap-3 p-12 text-sm text-slate-500"
    >
      <LoaderCircle size={20} className="animate-spin text-blue-600" aria-hidden />
      {label}
    </div>
  );
}

export function EmptyState({
  icon: Icon = Inbox,
  title,
  description,
  action,
}: {
  icon?: typeof Inbox;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center p-12 text-center">
      <div className="rounded-2xl bg-slate-100 p-4 text-slate-400">
        <Icon size={30} aria-hidden />
      </div>
      <h3 className="mt-4 font-semibold text-slate-800">{title}</h3>
      <p className="mt-2 max-w-sm text-sm text-slate-500">{description}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

const errorPresentation = {
  unauthorized: {
    icon: LockKeyhole,
    title: "Session ended",
    tone: "bg-amber-50 text-amber-600",
  },
  forbidden: {
    icon: ShieldAlert,
    title: "Access denied",
    tone: "bg-red-50 text-red-600",
  },
  network: {
    icon: WifiOff,
    title: "Server unreachable",
    tone: "bg-slate-100 text-slate-500",
  },
  default: {
    icon: CircleAlert,
    title: "Something went wrong",
    tone: "bg-red-50 text-red-600",
  },
} as const;

export function ErrorState({
  error,
  onRetry,
  compact = false,
}: {
  error: ApiError;
  onRetry?: () => void;
  compact?: boolean;
}) {
  const view =
    error.kind === "unauthorized" || error.kind === "forbidden" || error.kind === "network"
      ? errorPresentation[error.kind]
      : errorPresentation.default;
  const Icon = view.icon;

  return (
    <div
      role="alert"
      className={`flex flex-col items-center text-center ${compact ? "p-6" : "p-12"}`}
    >
      <div className={`rounded-2xl p-4 ${view.tone}`}>
        <Icon size={compact ? 24 : 30} aria-hidden />
      </div>
      <h3 className="mt-4 font-semibold text-slate-800">{view.title}</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
        {error.message}
      </p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-5 inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          <RefreshCw size={16} aria-hidden />
          Try again
        </button>
      )}
    </div>
  );
}
