
"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import axios from "axios";
import {
  Activity,
  ArrowRight,
  BookOpen,
  GraduationCap,
  LayoutDashboard,
  Menu,
  RefreshCw,
  ShieldCheck,
  Users,
  X,
} from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import api from "@/lib/api";
import type { Student } from "@/types/student";
import type { Course } from "@/types/course";

type OverviewData = {
  students: Student[];
  courses: Course[];
  users: unknown[];
};

const navigation = [
  { label: "Overview", href: "/admin/dashboard", icon: LayoutDashboard },
  { label: "Students", href: "/students", icon: Users },
  { label: "Courses", href: "/courses", icon: BookOpen },
];

export default function AdminOverview() {
  const [data, setData] = useState<OverviewData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [students, courses, users] = await Promise.all([
        api.get<Student[]>("/api/students"),
        api.get<Course[]>("/api/courses"),
        api.get<unknown[]>("/api/users"),
      ]);

      setData({
        students: students.data,
        courses: courses.data,
        users: users.data,
      });
    } catch (err) {
      setData(null);
      setError(
        axios.isAxiosError(err)
          ? "Unable to load records. Backend authentication may be required."
          : "Unable to load dashboard information."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const metrics = [
    {
      label: "Total Students",
      value: data?.students.length,
      icon: GraduationCap,
      iconStyle: "bg-blue-50 text-blue-600",
    },
    {
      label: "Total Courses",
      value: data?.courses.length,
      icon: BookOpen,
      iconStyle: "bg-violet-50 text-violet-600",
    },
    {
      label: "Registered Users",
      value: data?.users.length,
      icon: Users,
      iconStyle: "bg-emerald-50 text-emerald-600",
    },
  ];

  const chartData = [
    { name: "Students", count: data?.students.length ?? 0 },
    { name: "Courses", count: data?.courses.length ?? 0 },
    { name: "Users", count: data?.users.length ?? 0 },
  ];

  return (
    <div className="min-h-screen bg-slate-50 lg:flex">
      <aside className="hidden w-64 shrink-0 flex-col bg-slate-950 px-5 py-7 text-white lg:flex">
        <Link href="/" className="flex items-center gap-3 px-2">
          <div className="rounded-xl bg-blue-600 p-2.5">
            <GraduationCap size={24} />
          </div>
          <div>
            <p className="text-lg font-bold">StudentMS</p>
            <p className="text-xs text-slate-400">Administration</p>
          </div>
        </Link>

        <p className="mb-4 mt-12 px-3 text-xs font-semibold uppercase tracking-widest text-slate-500">
          Management
        </p>

        <nav className="space-y-2">
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition ${
                  item.href === "/admin/dashboard"
                    ? "bg-blue-600 text-white"
                    : "text-slate-300 hover:bg-slate-800 hover:text-white"
                }`}
              >
                <Icon size={19} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto rounded-2xl border border-slate-700 bg-slate-900 p-4">
          <ShieldCheck className="mb-3 text-blue-400" size={25} />
          <p className="text-sm font-semibold">Admin Workspace</p>
          <p className="mt-2 text-xs leading-5 text-slate-400">
            Role-based access controls will be enabled with JWT authentication.
          </p>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 lg:px-9">
          <div className="flex items-center gap-3">
            <button
              className="rounded-lg p-2 text-slate-700 lg:hidden"
              aria-label="Toggle navigation"
              onClick={() => setMenuOpen(!menuOpen)}
            >
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
            <div>
              <h1 className="text-lg font-bold text-slate-900">Admin Overview</h1>
              <p className="text-xs text-slate-500">Academic management center</p>
            </div>
          </div>

          <Link
            href="/"
            className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Home
          </Link>
        </header>

        {menuOpen && (
          <nav className="space-y-1 border-b border-slate-200 bg-white p-4 lg:hidden">
            {navigation.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className="block rounded-lg px-4 py-3 text-sm font-medium text-slate-700 hover:bg-blue-50"
              >
                {item.label}
              </Link>
            ))}
          </nav>
        )}

        <main className="mx-auto max-w-7xl space-y-7 p-5 lg:p-9">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-blue-600">ADMINISTRATION</p>
              <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
                Academic Overview
              </h2>
              <p className="mt-2 text-sm text-slate-500">
                Monitor students, courses, and registered accounts.
              </p>
            </div>

            <button
              onClick={() => void loadOverview()}
              disabled={loading}
              className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
            >
              <RefreshCw size={17} className={loading ? "animate-spin" : ""} />
              Refresh
            </button>
          </div>

          <section className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-700 to-indigo-700 p-8 text-white shadow-lg shadow-blue-100">
            <div className="absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10" />
            <div className="relative">
              <div className="inline-flex items-center gap-2 rounded-full bg-white/15 px-4 py-2 text-xs font-semibold">
                <Activity size={15} />
                Academic Administration
              </div>
              <h3 className="mt-5 text-2xl font-bold md:text-3xl">
                Manage your institution with clarity.
              </h3>
              <p className="mt-3 max-w-xl text-sm leading-7 text-blue-100">
                Explore academic records and keep essential information
                organized in one central workspace.
              </p>
            </div>
          </section>

          {error && (
            <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
              {error} Dashboard statistics will be available after authentication is connected.
            </div>
          )}

          <section className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
            {metrics.map((metric) => {
              const Icon = metric.icon;
              return (
                <div key={metric.label} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <div className={`inline-flex rounded-xl p-3 ${metric.iconStyle}`}>
                    <Icon size={24} />
                  </div>
                  <p className="mt-6 text-sm font-medium text-slate-500">{metric.label}</p>
                  <p className="mt-2 text-3xl font-bold text-slate-900">
                    {loading ? "..." : error ? "Pending" : metric.value ?? "—"}
                  </p>
                  <p className="mt-2 text-xs text-slate-400">Database records</p>
                </div>
              );
            })}
          </section>

          <section className="grid gap-6 xl:grid-cols-5">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm xl:col-span-3">
              <div className="mb-7">
                <h3 className="text-lg font-bold text-slate-900">Records Overview</h3>
                <p className="mt-1 text-sm text-slate-500">
                  Current student, course, and user totals
                </p>
              </div>

              <div className="h-72">
                {loading ? (
                  <div className="flex h-full items-center justify-center text-sm text-slate-500">
                    Loading records...
                  </div>
                ) : error ? (
                  <div className="flex h-full items-center justify-center text-sm text-slate-500">
                    Analytics unavailable
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="name" />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar dataKey="count" fill="#2563eb" radius={[8, 8, 0, 0]} maxBarSize={60} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm xl:col-span-2">
              <h3 className="text-lg font-bold text-slate-900">Management Shortcuts</h3>
              <p className="mt-1 text-sm text-slate-500">
                Quickly access academic modules
              </p>

              <div className="mt-7 space-y-4">
                {[
                  {
                    label: "Student Records",
                    description: "Browse registered students",
                    href: "/students",
                    icon: Users,
                  },
                  {
                    label: "Course Catalog",
                    description: "Browse available courses",
                    href: "/courses",
                    icon: BookOpen,
                  },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className="flex items-center gap-4 rounded-xl border border-slate-100 p-4 transition hover:border-blue-200 hover:bg-blue-50/40"
                    >
                      <div className="rounded-xl bg-blue-50 p-3 text-blue-600">
                        <Icon size={21} />
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-semibold text-slate-900">{item.label}</p>
                        <p className="mt-1 text-xs text-slate-500">{item.description}</p>
                      </div>
                      <ArrowRight size={18} className="text-slate-400" />
                    </Link>
                  );
                })}
              </div>
            </div>
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-100 p-6">
              <div>
                <h3 className="text-lg font-bold text-slate-900">Student Directory</h3>
                <p className="mt-1 text-sm text-slate-500">Recent student records</p>
              </div>
              <Link href="/students" className="text-sm font-semibold text-blue-600 hover:underline">
                View All
              </Link>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full min-w-[550px] text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-4">Student</th>
                    <th className="px-6 py-4">Email</th>
                    <th className="px-6 py-4">Course</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {loading ? (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">Loading students...</td></tr>
                  ) : error ? (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">Student records unavailable</td></tr>
                  ) : data?.students.length ? (
                    data.students.slice(0, 5).map((student) => (
                      <tr key={student.id} className="hover:bg-slate-50">
                        <td className="px-6 py-4 font-semibold text-slate-900">
                          {student.firstName} {student.lastName}
                        </td>
                        <td className="px-6 py-4 text-slate-600">{student.email}</td>
                        <td className="px-6 py-4 text-slate-600">{student.course || "Not assigned"}</td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">No student records found</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <p className="text-center text-xs text-slate-400">
            StudentMS Admin Workspace · Authentication integration pending
          </p>
        </main>
      </div>
    </div>
  );
}
