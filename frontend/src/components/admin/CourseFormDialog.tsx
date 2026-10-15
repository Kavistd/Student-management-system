"use client";

import { useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";

import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import { TextAreaField, TextField } from "@/components/ui/FormField";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import type { Course, CoursePayload } from "@/types/course";

type FormValues = Record<keyof CoursePayload, string>;
type FormErrors = Partial<Record<keyof CoursePayload, string>>;

function toFormValues(course: Course | null): FormValues {
  return {
    courseCode: course?.courseCode ?? "",
    courseName: course?.courseName ?? "",
    description: course?.description ?? "",
    durationMonths: course?.durationMonths != null ? String(course.durationMonths) : "",
  };
}

/** Mirrors the constraints declared on the backend's CourseRequest. */
function validate(values: FormValues): FormErrors {
  const errors: FormErrors = {};
  if (!values.courseCode.trim()) errors.courseCode = "Course code is required";
  if (!values.courseName.trim()) errors.courseName = "Course name is required";
  const duration = values.durationMonths.trim();
  if (duration && (!/^\d+$/.test(duration) || Number(duration) < 1)) {
    errors.durationMonths = "Duration must be a whole number of at least 1 month";
  }
  return errors;
}

function toPayload(values: FormValues): CoursePayload {
  const duration = values.durationMonths.trim();
  return {
    courseCode: values.courseCode.trim(),
    courseName: values.courseName.trim(),
    description: values.description.trim() || null,
    durationMonths: duration ? Number(duration) : null,
  };
}

function CourseForm({
  course,
  onCancel,
  onSaved,
}: {
  course: Course | null;
  onCancel: () => void;
  onSaved: (course: Course) => void;
}) {
  const [values, setValues] = useState<FormValues>(() => toFormValues(course));
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const update = (field: keyof FormValues) => (event: { target: { value: string } }) => {
    setValues((current) => ({ ...current, [field]: event.target.value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validate(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSaving(true);
    setSubmitError(null);
    try {
      const payload = toPayload(values);
      const response = course
        ? await api.put<Course>(`/api/courses/${course.id}`, payload)
        : await api.post<Course>("/api/courses", payload);
      onSaved(response.data);
    } catch (err) {
      setSubmitError(toApiError(err).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
        <TextField
          label="Course code"
          required
          autoComplete="off"
          value={values.courseCode}
          onChange={update("courseCode")}
          error={errors.courseCode}
          hint="Must be unique"
        />
        <TextField
          label="Course name"
          required
          autoComplete="off"
          value={values.courseName}
          onChange={update("courseName")}
          error={errors.courseName}
        />
      </div>
      <TextAreaField
        label="Description"
        value={values.description}
        onChange={update("description")}
        hint="Optional"
      />
      <TextField
        label="Duration (months)"
        type="number"
        inputMode="numeric"
        min={1}
        step={1}
        value={values.durationMonths}
        onChange={update("durationMonths")}
        error={errors.durationMonths}
        hint="Optional"
      />

      {submitError && (
        <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
          {submitError}
        </p>
      )}

      <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" disabled={saving}>
          {saving && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
          {course ? "Save changes" : "Add course"}
        </Button>
      </div>
    </form>
  );
}

export default function CourseFormDialog({
  open,
  course,
  onClose,
  onSaved,
}: {
  open: boolean;
  course: Course | null;
  onClose: () => void;
  onSaved: (course: Course, created: boolean) => void;
}) {
  return (
    <Dialog
      open={open}
      title={course ? "Edit course" : "Add course"}
      description={course ? `Update ${course.courseCode} — ${course.courseName}.` : "Add a course to the catalog."}
      onClose={onClose}
    >
      <CourseForm
        key={course?.id ?? "new"}
        course={course}
        onCancel={onClose}
        onSaved={(saved) => onSaved(saved, !course)}
      />
    </Dialog>
  );
}
