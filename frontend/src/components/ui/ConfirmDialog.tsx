"use client";

import type { ReactNode } from "react";
import { LoaderCircle } from "lucide-react";

import Dialog from "@/components/ui/Dialog";
import Button from "@/components/ui/Button";

export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  busy: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} title={title} onClose={busy ? () => {} : onClose} size="sm">
      <div className="text-sm leading-6 text-slate-600">{children}</div>

      {error && (
        <p role="alert" className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="danger" onClick={onConfirm} disabled={busy}>
          {busy && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
