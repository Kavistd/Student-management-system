import { ChevronLeft, ChevronRight } from "lucide-react";

/** Page numbers to show: all when few, otherwise first, last and neighbours of the current page. */
function visiblePages(page: number, pageCount: number): (number | "gap")[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, i) => i + 1);
  const pages = new Set([1, pageCount, page - 1, page, page + 1]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= pageCount).sort((a, b) => a - b);
  const result: (number | "gap")[] = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) result.push("gap");
    result.push(p);
  });
  return result;
}

const navButton =
  "inline-flex h-9 items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-blue-600";

/**
 * Footer for a paginated table: a clear "Showing 7–12 of 14 students" summary
 * plus Previous / page numbers / Next. Controls only appear when there is more
 * than one page.
 */
export default function Pagination({
  page,
  pageCount,
  firstItem,
  lastItem,
  total,
  unfilteredTotal,
  noun,
  onPageChange,
}: {
  page: number;
  pageCount: number;
  firstItem: number;
  lastItem: number;
  total: number;
  unfilteredTotal: number;
  noun: string;
  onPageChange: (page: number) => void;
}) {
  const range = firstItem === lastItem ? `${firstItem}` : `${firstItem}–${lastItem}`;
  const label = total === 1 ? noun.replace(/s$/, "") : noun;

  return (
    <div className="flex flex-col gap-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p aria-live="polite">
        Showing <span className="font-semibold text-slate-700">{range}</span> of{" "}
        <span className="font-semibold text-slate-700">{total}</span> {label}
        {total < unfilteredTotal && <span> (filtered from {unfilteredTotal})</span>}
      </p>

      {pageCount > 1 && (
        <nav aria-label={`${noun} pages`} className="flex items-center gap-1.5">
          <button
            type="button"
            className={navButton}
            onClick={() => onPageChange(page - 1)}
            disabled={page === 1}
            aria-label="Previous page"
          >
            <ChevronLeft size={14} aria-hidden />
            <span className="hidden sm:inline">Previous</span>
          </button>

          <span className="px-1 font-semibold text-slate-700 sm:hidden">
            Page {page} of {pageCount}
          </span>

          <ul className="hidden items-center gap-1 sm:flex">
            {visiblePages(page, pageCount).map((item, index) =>
              item === "gap" ? (
                <li key={`gap-${index}`} className="px-1 text-slate-400" aria-hidden>
                  …
                </li>
              ) : (
                <li key={item}>
                  <button
                    type="button"
                    onClick={() => onPageChange(item)}
                    aria-label={`Page ${item}`}
                    aria-current={item === page ? "page" : undefined}
                    className={`h-9 min-w-9 rounded-lg px-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-blue-600 ${
                      item === page
                        ? "bg-blue-600 text-white shadow-sm"
                        : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {item}
                  </button>
                </li>
              )
            )}
          </ul>

          <button
            type="button"
            className={navButton}
            onClick={() => onPageChange(page + 1)}
            disabled={page === pageCount}
            aria-label="Next page"
          >
            <span className="hidden sm:inline">Next</span>
            <ChevronRight size={14} aria-hidden />
          </button>
        </nav>
      )}
    </div>
  );
}
