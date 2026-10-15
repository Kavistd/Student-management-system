"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
  ArrowRight,
  BookOpen,
  GraduationCap,
  LockKeyhole,
  RefreshCw,
  UserCog,
  UserRoundX,
  Users,
} from "lucide-react";

import AdminShell from "@/components/layout/AdminShell";
import ChartCard from "@/components/admin/ChartCard";
import { countBy } from "@/components/admin/CountBarChart";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import StatCard from "@/components/ui/StatCard";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { useAuthUser } from "@/components/auth/AuthProvider";
import { useApiResource } from "@/hooks/useApiResource";
import type { Course } from "@/types/course";
import type { Student } from "@/types/student";
import type { User } from "@/types/user";

const shortcuts = [
  {
    label: "Manage students",
    description: "Add, edit, and remove student records",
    href: "/students",
    icon: Users,
  },
  {
    label: "Manage courses",
    description: "Maintain the course catalog",
    href: "/courses",
    icon: BookOpen,
  },
  {
    label: "User accounts",
    description: "Review registered accounts and roles",
    href: "/admin/users",
    icon: UserCog,
  },
];

export default function AdminOverview() {
  const students = useApiResource<Student[]>("/api/students");
  const courses = useApiResource<Course[]>("/api/courses");
  const users = useApiResource<User[]>("/api/users");
  const account = useAuthUser();

  const resources = [students, courses, users];
  const anyLoading = resources.some((resource) => resource.loading);
  const allDenied = resources.every(
    (resource) => resource.error?.kind === "unauthorized" || resource.error?.kind === "forbidden"
  );

  const reloadAll = () => resources.forEach((resource) => resource.reload());

  const studentsPerCourse = useMemo(
    () => countBy(students.data ?? [], (student) => student.course?.trim() || "Not assigned"),
    [students.data]
  );

  const courseDurations = useMemo(
    () =>
      countBy(
        courses.data ?? [],
        (course) =>
          course.durationMonths != null
            ? `${course.durationMonths} month${course.durationMonths === 1 ? "" : "s"}`
            : "Not specified"
      ),
    [courses.data]
  );

  const accountsByRole = useMemo(
    () => countBy(users.data ?? [], (user) => (user.role === "ADMIN" ? "Admin" : "Student")),
    [users.data]
  );

  const unassignedCount = students.data?.filter((student) => !student.course?.trim()).length;

  const recentStudents = useMemo(
    () => [...(students.data ?? [])].sort((a, b) => b.id - a.id).slice(0, 5),
    [students.data]
  );

  return (
    <AdminShell
      eyebrow="Administration"
      title="Dashboard overview"
      description="Live totals and breakdowns from your student, course, and account records."
      actions={
        <Button variant="secondary" onClick={reloadAll} disabled={anyLoading}>
          <RefreshCw size={16} className={anyLoading ? "animate-spin" : ""} aria-hidden />
          Refresh data
        </Button>
      }
    >
      <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-blue-700 to-indigo-700 p-6 text-white shadow-lg shadow-blue-900/10 sm:p-8">
        <div className="absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10" aria-hidden />
        <div className="relative max-w-2xl">
          <h2 className="text-xl font-bold sm:text-2xl">Welcome back, {account?.username ?? "Administrator"}</h2>
          <p className="mt-2 text-sm leading-6 text-blue-100">
            Manage student records, keep the course catalog current, and review registered
            accounts from one workspace.
          </p>
        </div>
      </section>

      {allDenied && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
        >
          <LockKeyhole size={18} className="mt-0.5 shrink-0" aria-hidden />
          <p>
            <span className="font-semibold">Admin data couldn&apos;t be loaded.</span> Your
            session may have ended or your account lacks administrator access. Sign in again
            with an administrator account.
          </p>
        </div>
      )}

      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Total students"
          value={students.data?.length}
          hint="Student records in the database"
          icon={GraduationCap}
          tone="bg-blue-50 text-blue-600"
          loading={students.loading}
          error={students.error}
        />
        <StatCard
          label="Courses"
          value={courses.data?.length}
          hint="Courses in the catalog"
          icon={BookOpen}
          tone="bg-violet-50 text-violet-600"
          loading={courses.loading}
          error={courses.error}
        />
        <StatCard
          label="User accounts"
          value={users.data?.length}
          hint="Registered login accounts"
          icon={UserCog}
          tone="bg-emerald-50 text-emerald-600"
          loading={users.loading}
          error={users.error}
        />
        <StatCard
          label="Without a course"
          value={unassignedCount}
          hint="Students with no course assigned"
          icon={UserRoundX}
          tone="bg-amber-50 text-amber-600"
          loading={students.loading}
          error={students.error}
        />
      </section>

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <ChartCard
            title="Students per course"
            description="Grouped by the course recorded on each student"
            loading={students.loading}
            error={students.error}
            data={studentsPerCourse}
            valueLabel="Students"
            emptyMessage="Add student records to see how they are distributed across courses."
          />
        </div>
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-bold text-slate-900">Quick actions</h2>
          <p className="mt-0.5 text-sm text-slate-500">Jump to a management area</p>
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

      <div className="grid gap-6 lg:grid-cols-2">
        <ChartCard
          title="Course durations"
          description="Number of courses by programme length"
          loading={courses.loading}
          error={courses.error}
          data={courseDurations}
          valueLabel="Courses"
          emptyMessage="Add courses to the catalog to see their durations."
        />
        <ChartCard
          title="Accounts by role"
          description="Registered login accounts grouped by role"
          loading={users.loading}
          error={users.error}
          data={accountsByRole}
          valueLabel="Accounts"
          emptyMessage="No login accounts have been registered yet."
        />
      </div>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between gap-4 border-b border-slate-100 p-5">
          <div>
            <h2 className="text-base font-bold text-slate-900">Latest student records</h2>
            <p className="mt-0.5 text-sm text-slate-500">The five most recently added students</p>
          </div>
          <Link href="/students" className="text-sm font-semibold text-blue-600 hover:underline">
            View all
          </Link>
        </div>

        {students.loading ? (
          <LoadingState label="Loading students…" />
        ) : students.error ? (
          <ErrorState error={students.error} onRetry={students.reload} compact />
        ) : recentStudents.length === 0 ? (
          <EmptyState
            icon={Users}
            title="No students yet"
            description="Student records will appear here once they are added."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold">Student</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Email</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Course</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {recentStudents.map((student) => (
                  <tr key={student.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3.5 font-semibold text-slate-900">
                      {student.firstName} {student.lastName}
                    </td>
                    <td className="px-5 py-3.5 text-slate-600">{student.email}</td>
                    <td className="px-5 py-3.5">
                      {student.course ? (
                        <Badge tone="indigo">{student.course}</Badge>
                      ) : (
                        <span className="text-slate-400">Not assigned</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </AdminShell>
  );
}
