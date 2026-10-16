"use client";

import { useMemo, useState } from "react";

/** Records shown per page in the admin tables. */
export const PAGE_SIZE = 6;

/**
 * Client-side pagination for an already filtered list. The requested page is
 * clamped to the last page, so deleting or filtering never leaves an empty page.
 * Call `setPage(1)` when search or filters change.
 */
export function usePagination<T>(items: T[], pageSize = PAGE_SIZE) {
  const [requestedPage, setPage] = useState(1);

  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  const page = Math.min(requestedPage, pageCount);
  const start = (page - 1) * pageSize;

  const pageItems = useMemo(() => items.slice(start, start + pageSize), [items, start, pageSize]);

  return {
    page,
    pageCount,
    pageItems,
    setPage,
    total: items.length,
    firstItem: items.length === 0 ? 0 : start + 1,
    lastItem: start + pageItems.length,
  };
}
