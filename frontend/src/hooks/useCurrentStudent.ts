"use client";

import { useApiResource } from "@/hooks/useApiResource";
import type { ApiError } from "@/lib/apiError";
import type { Student } from "@/types/student";

/**
 * The signed-in student's own record. The backend resolves it from the
 * authenticated identity (the JWT subject), never from an ID in the URL, so
 * the portal never needs to guess an ID or list other students.
 */
const CURRENT_STUDENT_ENDPOINT = "/api/students/me";

export type CurrentStudentState =
  | { status: "signedOut" }
  | { status: "loading" }
  | { status: "error"; error: ApiError; retry: () => void }
  | { status: "ready"; student: Student; setStudent: (student: Student) => void };

export function useCurrentStudent(): CurrentStudentState {
  const resource = useApiResource<Student>(CURRENT_STUDENT_ENDPOINT);

  if (resource.loading) return { status: "loading" };
  if (resource.error) {
    return resource.error.kind === "unauthorized"
      ? { status: "signedOut" }
      : { status: "error", error: resource.error, retry: resource.reload };
  }
  if (resource.data) {
    return { status: "ready", student: resource.data, setStudent: (student) => resource.setData(() => student) };
  }
  return { status: "loading" };
}
