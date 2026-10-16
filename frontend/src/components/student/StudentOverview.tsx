"use client";

import Link from "next/link";
import {
  ArrowRight,
  BookOpen,
  LockKeyhole,
  ShieldCheck,
  UserRound,
} from "lucide-react";

import StudentShell from "@/components/layout/StudentShell";
import CurrentStudentGate from "@/components/student/CurrentStudentGate";
import Badge from "@/components/ui/Badge";
import StatCard from "@/components/ui/StatCard";
import { useAuthUser } from "@/components/auth/AuthProvider";
import { useCurrentStudent } from "@/hooks/useCurrentStudent";

const shortcuts = [
  {
    label: "My Profile",
    description: "Your details and account settings",
    href: "/student/profile",
    icon: UserRound,
  },
  {
    label: "My Courses",
    description: "Your enrolled course and the course catalog",
    href: "/student/courses",
    icon: BookOpen,
  },
];

export default function StudentOverview() {
  const current = useCurrentStudent();
  const student = current.status === "ready" ? current.student : null;
  const loading = current.status === "loading";
  const account = useAuthUser();
  const signedOutHint = "Available after you sign in";

  return (
    <StudentShell
      eyebrow="My workspace"
      title="Student overview"
      description="Your profile, course, and account at a glance."
    >
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-blue-700 to-indigo-700 p-6 text-white shadow-lg shadow-blue-900/10 sm:p-8">
        <div className="absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10" aria-hidden />
        <div className="relative max-w-2xl">
          <h2 className="text-xl font-bold sm:text-2xl">
            {student ? `Welcome back, ${student.firstName}` : "Welcome to your student portal"}
          </h2>
          <p className="mt-2 text-sm leading-6 text-blue-100">
            View your profile, follow your course, and manage your account from one place.
          </p>
        </div>
      </section>

      {current.status === "signedOut" && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
        >
          <LockKeyhole size={18} className="mt-0.5 shrink-0" aria-hidden />
          <p>
            <span className="font-semibold">Your session has ended.</span> Sign in again to
            load your personal records.
          </p>
        </div>
      )}

      <section aria-label="Summary" className="grid gap-4 md:grid-cols-3">
        <StatCard
          label="Student ID"
          value={student ? `#${student.id}` : null}
          hint={student ? "Your student record number" : signedOutHint}
          icon={UserRound}
          tone="bg-blue-50 text-blue-600"
          loading={loading}
          error={current.status === "error" ? current.error : null}
        />
        <StatCard
          label="Enrolled course"
          value={student ? student.course || "Not assigned" : null}
          hint={student ? "Course on your student record" : signedOutHint}
          icon={BookOpen}
          tone="bg-violet-50 text-violet-600"
          loading={loading}
          error={current.status === "error" ? current.error : null}
        />
        <StatCard
          label="Account access"
          value={account ? "Signed in" : null}
          hint={account ? account.email : signedOutHint}
          icon={ShieldCheck}
          tone="bg-emerald-50 text-emerald-600"
          loading={loading}
          error={null}
        />
      </section>

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-2">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">My details</h2>
              <p className="mt-0.5 text-sm text-slate-500">A summary of your student record</p>
            </div>
            <Link
              href="/student/profile"
              className="text-sm font-semibold text-blue-600 hover:underline"
            >
              View profile
            </Link>
          </div>

          <div className="mt-4">
            <CurrentStudentGate state={current} subject="your details" compact>
              {(me) => (
                <dl className="grid gap-3 sm:grid-cols-2">
                  {[
                    ["Full name", `${me.firstName} ${me.lastName}`],
                    ["Email", me.email],
                    ["Phone", me.phone || "Not provided"],
                  ].map(([term, value]) => (
                    <div key={term} className="rounded-xl bg-slate-50 p-4">
                      <dt className="text-xs font-medium text-slate-500">{term}</dt>
                      <dd className="mt-1 break-words text-sm font-semibold text-slate-800">
                        {value}
                      </dd>
                    </div>
                  ))}
                  <div className="rounded-xl bg-slate-50 p-4">
                    <dt className="text-xs font-medium text-slate-500">Course</dt>
                    <dd className="mt-1.5">
                      {me.course ? (
                        <Badge tone="indigo">{me.course}</Badge>
                      ) : (
                        <span className="text-sm font-semibold text-slate-400">Not assigned</span>
                      )}
                    </dd>
                  </div>
                </dl>
              )}
            </CurrentStudentGate>
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-bold text-slate-900">Quick links</h2>
          <p className="mt-0.5 text-sm text-slate-500">Go to a section of your portal</p>
          <div className="mt-4 space-y-3">
            {shortcuts.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-3 rounded-xl border border-slate-100 p-3.5 transition hover:border-blue-200 hover:bg-blue-50/40 focus-visible:outline-2 focus-visible:outline-blue-600"
                >
                  <div className="rounded-xl bg-blue-50 p-2.5 text-blue-600">
                    <Icon size={18} aria-hidden />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-slate-900">{item.label}</p>
                    <p className="text-xs text-slate-500">{item.description}</p>
                  </div>
                  <ArrowRight size={16} className="text-slate-400" aria-hidden />
                </Link>
              );
            })}
          </div>
        </section>
      </div>
    </StudentShell>
  );
}
