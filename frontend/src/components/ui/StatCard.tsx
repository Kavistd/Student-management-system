import type { BookOpen } from "lucide-react";

import type { ApiError } from "@/lib/apiError";

export default function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  tone,
  loading,
  error,
}: {
  label: string;
  value: number | string | null | undefined;
  hint: string;
  icon: typeof BookOpen;
  tone: string;
  loading: boolean;
  error: ApiError | null;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <div className={`rounded-xl p-2.5 ${tone}`}>
          <Icon size={20} aria-hidden />
        </div>
      </div>
      <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
        {loading ? (
          <span className="inline-block h-8 w-16 animate-pulse rounded-lg bg-slate-100">
            <span className="sr-only">Loading</span>
          </span>
        ) : error || value === null || value === undefined ? (
          <span className="text-slate-300" aria-label="Unavailable">—</span>
        ) : (
          value
        )}
      </p>
      <p className="mt-1 text-xs text-slate-400">
        {error && !loading
          ? error.kind === "unauthorized"
            ? "Requires sign-in"
            : error.kind === "forbidden"
              ? "Not permitted for your account"
              : "Could not be loaded"
          : hint}
      </p>
    </div>
  );
}
