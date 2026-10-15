import { Pencil, Trash2 } from "lucide-react";

export default function RowActions({
  itemLabel,
  onEdit,
  onDelete,
}: {
  itemLabel: string;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="flex items-center justify-end gap-1">
      <button
        type="button"
        onClick={onEdit}
        aria-label={`Edit ${itemLabel}`}
        title="Edit"
        className="rounded-lg p-2 text-slate-500 transition hover:bg-blue-50 hover:text-blue-700 focus-visible:outline-2 focus-visible:outline-blue-600"
      >
        <Pencil size={16} aria-hidden />
      </button>
      <button
        type="button"
        onClick={onDelete}
        aria-label={`Delete ${itemLabel}`}
        title="Delete"
        className="rounded-lg p-2 text-slate-500 transition hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-red-600"
      >
        <Trash2 size={16} aria-hidden />
      </button>
    </div>
  );
}
