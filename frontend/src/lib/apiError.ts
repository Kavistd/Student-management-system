import axios from "axios";

export type ApiErrorKind =
  | "unauthorized"
  | "forbidden"
  | "rateLimited"
  | "network"
  | "validation"
  | "conflict"
  | "notFound"
  | "server"
  | "unknown";

export interface ApiError {
  kind: ApiErrorKind;
  status?: number;
  message: string;
}

/**
 * Pulls a human-readable message out of the backend's error bodies.
 * Supports the API's code/message/field-errors objects and legacy error bodies.
 */
function extractMessage(data: unknown): string | null {
  if (typeof data === "string" && data.trim()) return data.trim();

  if (data && typeof data === "object") {
    const body = data as Record<string, unknown>;

    if (body.errors && typeof body.errors === "object" && !Array.isArray(body.errors)) {
      const messages = Object.values(body.errors).filter(
        (value): value is string => typeof value === "string" && value.trim().length > 0
      );
      if (messages.length) return messages.join(" ");
    }

    if (Array.isArray(body.errors) && body.errors.length > 0) {
      const messages = body.errors
        .map((item) =>
          item && typeof item === "object"
            ? (item as Record<string, unknown>).defaultMessage
            : item
        )
        .filter((value): value is string => typeof value === "string");
      if (messages.length) return messages.join(" ");
    }

    for (const key of ["message", "detail", "error"]) {
      const value = body[key];
      if (typeof value === "string" && value.trim()) return value.trim();
    }
  }

  return null;
}

export function toApiError(err: unknown): ApiError {
  if (!axios.isAxiosError(err)) {
    return { kind: "unknown", message: "An unexpected error occurred." };
  }

  if (!err.response) {
    return {
      kind: "network",
      message:
        "Unable to reach the server. Check that the backend is running and try again.",
    };
  }

  const status = err.response.status;
  const data: unknown = err.response.data;
  const message = extractMessage(data);

  if (status === 401) {
    return {
      kind: "unauthorized",
      status,
      message: "Your session has ended. Please sign in again to continue.",
    };
  }

  if (status === 403) {
    return {
      kind: "forbidden",
      status,
      message: message ?? "Your account doesn't have permission to access this.",
    };
  }

  if (status === 429) {
    return {
      kind: "rateLimited",
      status,
      message: message ?? "Too many attempts. Please wait a few minutes and try again.",
    };
  }

  if (status === 400) {
    return {
      kind: "validation",
      status,
      message: message ?? "Some fields are invalid. Please review and try again.",
    };
  }

  if (status === 404) {
    return {
      kind: "notFound",
      status,
      message: message ?? "The requested record no longer exists.",
    };
  }

  if (status === 409) {
    const code =
      data && typeof data === "object"
        ? (data as Record<string, unknown>).code
        : undefined;
    const fallback =
      code === "DUPLICATE_RECORD"
        ? message ?? "A record with these details already exists."
        : message ?? "This record conflicts with an existing one.";
    return { kind: "conflict", status, message: fallback };
  }

  return {
    kind: status >= 500 ? "server" : "unknown",
    status,
    message:
      status >= 500
        ? "The server could not complete the request. Please try again later."
        : message ?? `Request failed with status ${status}.`,
  };
}
