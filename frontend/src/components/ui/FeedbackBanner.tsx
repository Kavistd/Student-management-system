"use client";

import { useEffect } from "react";
import { CircleAlert, CircleCheck, X } from "lucide-react";

export type Feedback = { type: "success" | "error"; message: string };

/** Inline, auto-dismissing status message announced to screen readers. */
export default function FeedbackBanner({
  feedback,
  onDismiss,
}: {
  feedback: Feedback | null;
  onDismiss: () => void;
}) {
  useEffect(() => {
    if (feedback?.type !== "success") return;
    const timer = window.setTimeout(onDismiss, 5000);
    return () => window.clearTimeout(timer);
  }, [feedback, onDismiss]);

  return (
    <div aria-live="polite" className="empty:hidden">
      {feedback && (
        <div
          className={`flex items-start gap-3 rounded-xl border p-4 text-sm ${
            feedback.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-red-200 bg-red-50 text-red-800"
          }`}
        >
          {feedback.type === "success" ? (
            <CircleCheck size={18} className="mt-0.5 shrink-0" aria-hidden />
          ) : (
            <CircleAlert size={18} className="mt-0.5 shrink-0" aria-hidden />
          )}
          <p className="flex-1">{feedback.message}</p>
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss message"
            className="rounded p-0.5 opacity-70 hover:opacity-100"
          >
            <X size={16} aria-hidden />
          </button>
        </div>
      )}
    </div>
  );
}
