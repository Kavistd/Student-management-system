"use client";

import { useCallback, useEffect, useState } from "react";

import api from "@/lib/api";
import { toApiError, type ApiError } from "@/lib/apiError";

interface ResourceState<T> {
  data: T | null;
  error: ApiError | null;
  loading: boolean;
}

/**
 * Loads a GET endpoint through the shared Axios instance.
 * Pass `null` as the path to skip the request entirely (the resource stays
 * empty and not loading). `setData` lets callers apply the result of a
 * successful mutation without refetching the whole list.
 */
export function useApiResource<T>(path: string | null) {
  const [state, setState] = useState<ResourceState<T>>({
    data: null,
    error: null,
    loading: path !== null,
  });
  const [requestId, setRequestId] = useState(0);

  useEffect(() => {
    if (path === null) return;
    const controller = new AbortController();

    api
      .get<T>(path, { signal: controller.signal })
      .then((response) => {
        setState({ data: response.data, error: null, loading: false });
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setState({ data: null, error: toApiError(err), loading: false });
      });

    return () => controller.abort();
  }, [path, requestId]);

  const reload = useCallback(() => {
    if (path === null) return;
    setState((current) => ({ ...current, loading: true, error: null }));
    setRequestId((id) => id + 1);
  }, [path]);

  const setData = useCallback((update: (current: T) => T) => {
    setState((current) =>
      current.data === null ? current : { ...current, data: update(current.data) }
    );
  }, []);

  return { ...state, reload, setData };
}
