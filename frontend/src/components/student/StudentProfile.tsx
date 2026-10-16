"use client";

import { useEffect, useRef, useState } from "react";
import { BookOpen, Hash, Mail, Phone, Settings, UserRound } from "lucide-react";

import StudentShell from "@/components/layout/StudentShell";
import AccountSettingsPanel from "@/components/student/AccountSettingsPanel";
import CurrentStudentGate from "@/components/student/CurrentStudentGate";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { useCurrentStudent } from "@/hooks/useCurrentStudent";
import type { Student } from "@/types/student";

function ProfileDetails({ student }: { student: Student }) {
  const initials =
    `${student.firstName.charAt(0)}${student.lastName.charAt(0)}`.toUpperCase() || "S";

  const fields = [
    { label: "Student ID", value: `#${student.id}`, icon: Hash },
    { label: "Email address", value: student.email, icon: Mail },
    { label: "Phone", value: student.phone || "Not provided", icon: Phone },
  ];

  return (
    <div>
      <div className="flex flex-col items-center gap-4 border-b border-slate-100 p-6 text-center sm:flex-row sm:text-left">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-blue-100 text-xl font-bold text-blue-700">
          {initials}
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-900">
            {student.firstName} {student.lastName}
          </h2>
          <p className="mt-1 text-sm text-slate-500">{student.email}</p>
        </div>
      </div>

      <dl className="grid gap-4 p-6 sm:grid-cols-2">
        {fields.map((field) => {
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
        <div className="flex items-start gap-3 rounded-xl bg-slate-50 p-4">
          <BookOpen size={18} className="mt-0.5 shrink-0 text-slate-400" aria-hidden />
          <div>
            <dt className="text-xs font-medium text-slate-500">Course</dt>
            <dd className="mt-1.5">
              {student.course ? (
                <Badge tone="indigo">{student.course}</Badge>
              ) : (
                <span className="text-sm font-semibold text-slate-400">Not assigned</span>
              )}
            </dd>
          </div>
        </div>
      </dl>
    </div>
  );
}

const SETTINGS_ID = "account-settings";

export default function StudentProfile() {
  const current = useCurrentStudent();
  const [showSettings, setShowSettings] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);

  // Bring the panel into view when it is opened from the header button.
  useEffect(() => {
    if (showSettings) {
      settingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [showSettings]);

  return (
    <StudentShell
      eyebrow="My workspace"
      title="My profile"
      description="The personal details on your student record."
      actions={
        <Button
          variant={showSettings ? "primary" : "secondary"}
          onClick={() => setShowSettings((open) => !open)}
          aria-expanded={showSettings}
          aria-controls={SETTINGS_ID}
        >
          <Settings size={16} aria-hidden />
          Account Settings
        </Button>
      }
    >
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center gap-3 border-b border-slate-100 p-5">
          <div className="rounded-xl bg-blue-50 p-2.5 text-blue-600">
            <UserRound size={20} aria-hidden />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-900">Personal information</h2>
            <p className="text-sm text-slate-500">As recorded by your institution</p>
          </div>
        </div>
        <CurrentStudentGate state={current} subject="your profile">
          {(student) => <ProfileDetails student={student} />}
        </CurrentStudentGate>
      </section>

      {showSettings && current.status === "ready" && (
        <div ref={settingsRef}>
          <AccountSettingsPanel
            id={SETTINGS_ID}
            student={current.student}
            onStudentUpdated={current.setStudent}
          />
        </div>
      )}
    </StudentShell>
  );
}
