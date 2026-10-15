import type { ReactNode } from "react";

const tones = {
  blue: "bg-blue-50 text-blue-700 ring-blue-600/15",
  indigo: "bg-indigo-50 text-indigo-700 ring-indigo-600/15",
  emerald: "bg-emerald-50 text-emerald-700 ring-emerald-600/15",
  amber: "bg-amber-50 text-amber-800 ring-amber-600/20",
  slate: "bg-slate-100 text-slate-600 ring-slate-500/15",
} as const;

export type BadgeTone = keyof typeof tones;

export default function Badge({
  tone = "slate",
  children,
}: {
  tone?: BadgeTone;
  children: ReactNode;
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${tones[tone]}`}
    >
      {children}
    </span>
  );
}
