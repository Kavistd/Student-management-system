"use client";

import { useId, useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";

import Button from "@/components/ui/Button";
import Dialog from "@/components/ui/Dialog";
import { TextField } from "@/components/ui/FormField";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import type { Student, StudentPayload } from "@/types/student";

type FormValues = Record<keyof StudentPayload, string>;
type FormErrors = Partial<Record<keyof StudentPayload, string>>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function toFormValues(student: Student): FormValues {
  return {
    firstName: student.firstName ?? "",
    lastName: student.lastName ?? "",
    email: student.email ?? "",
    phone: student.phone ?? "",
    course: student.course ?? "",
  };
}

/** Mirrors the constraints declared on the backend's StudentRequest. */
function validate(values: FormValues): FormErrors {
  const errors: FormErrors = {};
  if (!values.firstName.trim()) errors.firstName = "First name is required";
  if (!values.lastName.trim()) errors.lastName = "Last name is required";
  if (!values.email.trim()) errors.email = "Email is required";
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = "Invalid email address";
  return errors;
}

function toPayload(values: FormValues): StudentPayload {
  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    email: values.email.trim(),
    phone: values.phone.trim() || null,
    course: values.course.trim() || null,
  };
}

/**
 * The form body is keyed by the student being edited, so opening the dialog
 * for a different record always starts from fresh values.
 */
function StudentForm({
  student,
  courseSuggestions,
  onCancel,
  onSaved,
}: {
  student: Student;
  courseSuggestions: string[];
  onCancel: () => void;
  onSaved: (student: Student) => void;
}) {
  const [values, setValues] = useState<FormValues>(() => toFormValues(student));
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const listId = useId();

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
      const response = await api.put<Student>(`/api/students/${student.id}`, payload);
      onSaved(response.data);
    } catch (err) {
      setSubmitError(toApiError(err).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="First name"
          required
          autoComplete="off"
          value={values.firstName}
          onChange={update("firstName")}
          error={errors.firstName}
        />
        <TextField
          label="Last name"
          required
          autoComplete="off"
          value={values.lastName}
          onChange={update("lastName")}
          error={errors.lastName}
        />
      </div>
      <TextField
        label="Email address"
        type="email"
        required
        autoComplete="off"
        value={values.email}
        onChange={update("email")}
        readOnly={student.linkedAccount}
        hint={student.linkedAccount ? "Linked account email is read-only." : "Contact email for this unlinked legacy record."}
        error={errors.email}
      />
      <TextField
        label="Phone"
        type="tel"
        autoComplete="off"
        value={values.phone}
        onChange={update("phone")}
        hint="Optional"
      />
      <TextField
        label="Course"
        list={courseSuggestions.length ? listId : undefined}
        autoComplete="off"
        value={values.course}
        onChange={update("course")}
        hint={
          courseSuggestions.length
            ? "Optional. Choose from the course catalog or type a value."
            : "Optional"
        }
      />
      {courseSuggestions.length > 0 && (
        <datalist id={listId}>
          {courseSuggestions.map((name) => (
            <option key={name} value={name} />
          ))}
        </datalist>
      )}

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
          Save changes
        </Button>
      </div>
    </form>
  );
}

export default function StudentFormDialog({
  student,
  courseSuggestions,
  onClose,
  onSaved,
}: {
  student: Student;
  courseSuggestions: string[];
  onClose: () => void;
  onSaved: (student: Student) => void;
}) {
  return (
    <Dialog
      open
      title="Edit student"
      description={`Update the record for ${student.firstName} ${student.lastName}.`}
      onClose={onClose}
    >
      <StudentForm
        key={student.id}
        student={student}
        courseSuggestions={courseSuggestions}
        onCancel={onClose}
        onSaved={onSaved}
      />
    </Dialog>
  );
}
