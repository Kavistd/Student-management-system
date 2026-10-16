"use client";

import { useCallback, useState, type FormEvent, type ReactNode } from "react";
import { AtSign, KeyRound, LoaderCircle, Mail, PencilLine, ShieldCheck } from "lucide-react";

import { useAuthUser } from "@/components/auth/AuthProvider";
import Button from "@/components/ui/Button";
import FeedbackBanner, { type Feedback } from "@/components/ui/FeedbackBanner";
import { TextField } from "@/components/ui/FormField";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import { changePassword } from "@/lib/auth";
import { PHONE_HINT, PHONE_PATTERN } from "@/lib/contactNumber";
import type { ProfileUpdatePayload, Student } from "@/types/student";

function SettingsSection({
  icon: Icon,
  title,
  description,
  children,
}: {
  icon: typeof KeyRound;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="p-5">
      <div className="mb-4 flex items-start gap-3">
        <div className="rounded-xl bg-slate-100 p-2.5 text-slate-500">
          <Icon size={18} aria-hidden />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
          <p className="mt-0.5 text-sm text-slate-500">{description}</p>
        </div>
      </div>
      {children}
    </section>
  );
}

function ContactDetailsForm({
  student,
  onSaved,
}: {
  student: Student;
  onSaved: (student: Student) => void;
}) {
  const [firstName, setFirstName] = useState(student.firstName);
  const [lastName, setLastName] = useState(student.lastName);
  const [phone, setPhone] = useState(student.phone ?? "");
  const [phoneError, setPhoneError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const dismiss = useCallback(() => setFeedback(null), []);

  const unchanged =
    firstName.trim() === student.firstName &&
    lastName.trim() === student.lastName &&
    phone.trim() === (student.phone ?? "");

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFeedback(null);
    if (!PHONE_PATTERN.test(phone.trim())) {
      setPhoneError(PHONE_HINT);
      return;
    }

    const payload: ProfileUpdatePayload = {
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      phone: phone.trim(),
    };
    setBusy(true);
    try {
      const response = await api.put<Student>("/api/students/me", payload);
      setFirstName(response.data.firstName);
      setLastName(response.data.lastName);
      setPhone(response.data.phone ?? "");
      onSaved(response.data);
      setFeedback({ type: "success", message: "Your contact details were saved." });
    } catch (err) {
      setFeedback({ type: "error", message: toApiError(err).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <FeedbackBanner feedback={feedback} onDismiss={dismiss} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="First name"
          required
          autoComplete="given-name"
          maxLength={255}
          value={firstName}
          onChange={(e) => setFirstName(e.target.value)}
        />
        <TextField
          label="Last name"
          required
          autoComplete="family-name"
          maxLength={255}
          value={lastName}
          onChange={(e) => setLastName(e.target.value)}
        />
        <TextField
          label="Contact number"
          required
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          placeholder="e.g. +94 77 123 4567"
          value={phone}
          error={phoneError}
          onChange={(e) => {
            setPhone(e.target.value);
            setPhoneError(undefined);
          }}
        />
      </div>
      <div className="flex justify-end">
        <Button type="submit" disabled={busy || unchanged}>
          {busy && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
          Save contact details
        </Button>
      </div>
    </form>
  );
}

function ChangePasswordForm() {
  const account = useAuthUser();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<{ current?: string; next?: string; confirm?: string }>({});
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const dismiss = useCallback(() => setFeedback(null), []);

  function validate() {
    const found: typeof errors = {};
    if (!currentPassword) found.current = "Enter your current password.";
    if (newPassword.length < 8) {
      found.next = "New password must be at least 8 characters.";
    } else if (new TextEncoder().encode(newPassword).length > 72) {
      found.next = "New password must be at most 72 UTF-8 bytes. Unicode characters may use multiple bytes.";
    } else if (newPassword === currentPassword) {
      found.next = "New password must be different from your current password.";
    }
    if (confirmPassword !== newPassword) found.confirm = "Passwords do not match.";
    return found;
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setFeedback(null);
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setBusy(true);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setFeedback({
        type: "success",
        message: "Your password was changed. Any other devices have been signed out.",
      });
    } catch (err) {
      setFeedback({ type: "error", message: toApiError(err).message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <FeedbackBanner feedback={feedback} onDismiss={dismiss} />
      {/* Lets password managers associate the new password with this account. */}
      <input type="hidden" name="username" autoComplete="username" value={account?.email ?? ""} readOnly />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2 sm:max-w-[calc(50%-0.5rem)]">
          <TextField
            label="Current password"
            type="password"
            required
            autoComplete="current-password"
            value={currentPassword}
            error={errors.current}
            onChange={(e) => {
              setCurrentPassword(e.target.value);
              setErrors((prev) => ({ ...prev, current: undefined }));
            }}
          />
        </div>
        <TextField
          label="New password"
          type="password"
          required
          autoComplete="new-password"
          hint="At least 8 characters."
          value={newPassword}
          error={errors.next}
          onChange={(e) => {
            setNewPassword(e.target.value);
            setErrors((prev) => ({ ...prev, next: undefined }));
          }}
        />
        <TextField
          label="Confirm new password"
          type="password"
          required
          autoComplete="new-password"
          value={confirmPassword}
          error={errors.confirm}
          onChange={(e) => {
            setConfirmPassword(e.target.value);
            setErrors((prev) => ({ ...prev, confirm: undefined }));
          }}
        />
      </div>
      <div className="flex justify-end">
        <Button
          type="submit"
          disabled={busy || !currentPassword || !newPassword || !confirmPassword}
        >
          {busy && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
          Change password
        </Button>
      </div>
    </form>
  );
}

/** The signed-in account's details, contact-details editing and password change, shown inside My Profile. */
export default function AccountSettingsPanel({
  id,
  student,
  onStudentUpdated,
}: {
  id: string;
  student: Student;
  onStudentUpdated: (student: Student) => void;
}) {
  const account = useAuthUser();

  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="scroll-mt-24 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
    >
      <div className="flex items-center gap-3 border-b border-slate-100 p-5">
        <div className="rounded-xl bg-emerald-50 p-2.5 text-emerald-600">
          <ShieldCheck size={20} aria-hidden />
        </div>
        <div>
          <h2 id={`${id}-title`} className="text-base font-bold text-slate-900">
            Account settings
          </h2>
          <p className="text-sm text-slate-500">The sign-in account linked to your student record</p>
        </div>
      </div>

      {account && (
        <dl className="grid gap-4 border-b border-slate-100 p-5 sm:grid-cols-2">
          {[
            { label: "Username", value: account.username, icon: AtSign },
            { label: "Email address (used to sign in)", value: account.email, icon: Mail },
          ].map((field) => {
            const Icon = field.icon;
            return (
              <div key={field.label} className="flex items-start gap-3 rounded-xl bg-slate-50 p-4">
                <Icon size={18} className="mt-0.5 shrink-0 text-slate-400" aria-hidden />
                <div className="min-w-0">
                  <dt className="text-xs font-medium text-slate-500">{field.label}</dt>
                  <dd className="mt-1 break-words text-sm font-semibold text-slate-800">{field.value}</dd>
                </div>
              </div>
            );
          })}
        </dl>
      )}

      <div className="divide-y divide-slate-100">
        <SettingsSection
          icon={PencilLine}
          title="Update contact details"
          description="Your name and contact number as they appear on your student record."
        >
          <ContactDetailsForm student={student} onSaved={onStudentUpdated} />
        </SettingsSection>

        <SettingsSection
          icon={KeyRound}
          title="Change password"
          description="Confirm your current password, then choose a new one."
        >
          <ChangePasswordForm />
        </SettingsSection>
      </div>
    </section>
  );
}
