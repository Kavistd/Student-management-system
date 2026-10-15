"use client";

import { AtSign, KeyRound, Mail, PencilLine, ShieldCheck } from "lucide-react";

import StudentShell from "@/components/layout/StudentShell";
import { useAuthUser } from "@/components/auth/AuthProvider";
import SignOutButton from "@/components/auth/SignOutButton";
import Badge from "@/components/ui/Badge";

/**
 * Account options without a student-facing backend endpoint yet. They are
 * shown as unavailable instead of rendering controls that would not work.
 */
const accountOptions = [
  {
    title: "Change password",
    description:
      "Update the password you use to sign in. Requires a self-service password endpoint on the backend.",
    icon: KeyRound,
  },
  {
    title: "Update contact details",
    description:
      "Edit your phone number or email address. Until this is available, your administrator can update them for you.",
    icon: PencilLine,
  },
];

export default function StudentSettings() {
  const account = useAuthUser();

  return (
    <StudentShell
      eyebrow="My workspace"
      title="Account settings"
      description="Your sign-in details and security options."
    >
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center gap-3 border-b border-slate-100 p-5">
          <div className="rounded-xl bg-emerald-50 p-2.5 text-emerald-600">
            <ShieldCheck size={20} aria-hidden />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-900">Sign-in details</h2>
            <p className="text-sm text-slate-500">The account linked to your student record</p>
          </div>
        </div>
        {account && (
          <dl className="grid gap-4 p-5 sm:grid-cols-2">
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
                    <dd className="mt-1 break-words text-sm font-semibold text-slate-800">
                      {field.value}
                    </dd>
                  </div>
                </div>
              );
            })}
          </dl>
        )}
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Sign out</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            Ends your session on this device and revokes its refresh token.
          </p>
        </div>
        <SignOutButton className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-60" />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-5">
          <h2 className="text-base font-bold text-slate-900">Account options</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            These options will be enabled as the supporting backend features are added.
          </p>
        </div>
        <ul className="divide-y divide-slate-100">
          {accountOptions.map((option) => {
            const Icon = option.icon;
            return (
              <li key={option.title} className="flex flex-col gap-3 p-5 sm:flex-row sm:items-start">
                <div className="w-fit rounded-xl bg-slate-100 p-2.5 text-slate-500">
                  <Icon size={18} aria-hidden />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-slate-900">{option.title}</p>
                  <p className="mt-0.5 text-sm leading-6 text-slate-500">{option.description}</p>
                </div>
                <div className="sm:pt-0.5">
                  <Badge tone="slate">Not available yet</Badge>
                </div>
              </li>
            );
          })}
        </ul>
      </section>
    </StudentShell>
  );
}
