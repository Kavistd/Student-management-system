
"use client";

import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import Link from "next/link";
import {
  ArrowRight,
  BookOpen,
  GraduationCap,
  RefreshCw,
  TrendingUp,
  Users,
  UserRoundPlus,
  Layers3,
  Activity,
  AlertCircle,
} from "lucide-react";
import {
  BarChart,
  Bar,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import Sidebar from "@/components/layout/Sidebar";
import Navbar from "@/components/layout/Navbar";
import api from "@/lib/api";
import type { Student } from "@/types/student";
import type { Course } from "@/types/course";

type DashboardData = {
  students: Student[];
  courses: Course[];
  userCount: number;
};

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDashboard = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [students, courses, users] = await Promise.all([
        api.get<Student[]>("/api/students"),
        api.get<Course[]>("/api/courses"),
        api.get<unknown[]>("/api/users"),
      ]);

      setData({
        students: students.data,
        courses: courses.data,
        userCount: users.data.length,
      });
    } catch (err) {
      setError(
        axios.isAxiosError(err)
          ? "Unable to load dashboard data. Please check the backend connection."
          : "An unexpected error occurred."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchDashboard();
  }, [fetchDashboard]);

  const stats = [
    {
      title: "Total Students",
      value: data?.students.length,
      icon: Users,
      color: "bg-blue-50 text-blue-600",
    },
    {
      title: "Active Courses",
      value: data?.courses.length,
      icon: BookOpen,
      color: "bg-violet-50 text-violet-600",
    },
    {
      title: "Registered Users",
      value: data?.userCount,
      icon: GraduationCap,
      color: "bg-emerald-50 text-emerald-600",
    },
    {
      title: "Course Catalog",
      value: data?.courses.length,
      icon: Layers3,
      color: "bg-orange-50 text-orange-600",
    },
  ];

  const chartData = [
    { name: "Students", total: data?.students.length ?? 0 },
    { name: "Courses", total: data?.courses.length ?? 0 },
    { name: "Users", total: data?.userCount ?? 0 },
  ];

  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col">
        <Navbar />

        <main className="flex-1 space-y-8 p-5 md:p-8 xl:p-10">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="mb-2 text-sm font-semibold text-blue-600">
                ACADEMIC OVERVIEW
              </p>
              <h1 className="text-3xl font-bold tracking-tight text-slate-900">
                Dashboard Overview
              </h1>
              <p className="mt-2 text-slate-500">
                Welcome to your academic management workspace.
              </p>
            </div>

            <button
              onClick={() => void fetchDashboard()}
              disabled={loading}
              className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
            >
              <RefreshCw size={17} className={loading ? "animate-spin" : ""} />
              Refresh Data
            </button>
          </div>

          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-700 via-blue-600 to-indigo-600 p-7 text-white shadow-lg shadow-blue-100 md:p-9">
            <div className="absolute -right-16 -top-24 h-72 w-72 rounded-full bg-white/10" />
            <div className="absolute right-24 bottom-0 h-32 w-32 rounded-full bg-white/10" />

            <div className="relative max-w-2xl">
              <span className="rounded-full bg-white/15 px-4 py-1.5 text-xs font-semibold">
                STUDENT MANAGEMENT SYSTEM
              </span>
              <h2 className="mt-5 text-2xl font-bold md:text-3xl">
                Manage your academic operations with confidence.
              </h2>
              <p className="mt-3 text-sm leading-7 text-blue-100">
                View student records, organize courses, and monitor
                your institution's data from one central dashboard.
              </p>

              <div className="mt-6 flex flex-wrap gap-3">
                <Link
                  href="/students"
                  className="inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-blue-700 transition hover:bg-blue-50"
                >
                  View Students <ArrowRight size={16} />
                </Link>
                <Link
                  href="/courses"
                  className="inline-flex items-center gap-2 rounded-xl border border-white/30 bg-white/10 px-5 py-3 text-sm font-semibold text-white transition hover:bg-white/20"
                >
                  Explore Courses
                </Link>
              </div>
            </div>
          </div>

          {error && (
            <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              <AlertCircle size={19} />
              <span className="flex-1">{error}</span>
              <button onClick={() => void fetchDashboard()} className="font-semibold underline">
                Try Again
              </button>
            </div>
          )}

          <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => {
              const Icon = stat.icon;

              return (
                <div
                  key={stat.title}
                  className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:-translate-y-1 hover:shadow-md"
                >
                  <div className="flex items-start justify-between">
                    <div className={`rounded-xl p-3 ${stat.color}`}>
                      <Icon size={23} />
                    </div>
                    <TrendingUp size={18} className="text-slate-300" />
                  </div>
                  <p className="mt-6 text-sm font-medium text-slate-500">
                    {stat.title}
                  </p>
                  <h3 className="mt-2 text-3xl font-bold text-slate-900">
                    {loading ? "..." : error ? "—" : stat.value ?? "—"}
                  </h3>
                  <p className="mt-2 text-xs text-slate-400">
                    Current database records
                  </p>
                </div>
              );
            })}
          </div>

          <div className="grid gap-6 xl:grid-cols-5">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm xl:col-span-3">
              <div className="mb-7 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-bold text-slate-900">
                    System Overview
                  </h3>
                  <p className="mt-1 text-sm text-slate-500">
                    Comparison of registered records
                  </p>
                </div>
                <Activity className="text-blue-600" size={22} />
              </div>

              <div className="h-72 w-full">
                {loading ? (
                  <div className="flex h-full items-center justify-center text-sm text-slate-500">
                    Loading analytics...
                  </div>
                ) : error ? (
                  <div className="flex h-full items-center justify-center text-sm text-slate-500">
                    Analytics unavailable.
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="name" />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar dataKey="total" fill="#2563eb" radius={[8, 8, 0, 0]} maxBarSize={65} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm xl:col-span-2">
              <div className="mb-6">
                <h3 className="text-lg font-bold text-slate-900">
                  Quick Actions
                </h3>
                <p className="mt-1 text-sm text-slate-500">
                  Navigate to management sections
                </p>
              </div>

              <div className="space-y-4">
                {[
                  {
                    title: "Student Directory",
                    description: "View and manage student records",
                    href: "/students",
                    icon: UserRoundPlus,
                    color: "bg-blue-50 text-blue-600",
                  },
                  {
                    title: "Course Management",
                    description: "Explore available academic courses",
                    href: "/courses",
                    icon: BookOpen,
                    color: "bg-violet-50 text-violet-600",
                  },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <Link
                      key={item.title}
                      href={item.href}
                      className="flex items-center gap-4 rounded-xl border border-slate-100 p-4 transition hover:border-blue-200 hover:bg-slate-50"
                    >
                      <div className={`rounded-xl p-3 ${item.color}`}>
                        <Icon size={22} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <h4 className="text-sm font-semibold text-slate-900">
                          {item.title}
                        </h4>
                        <p className="mt-1 text-xs text-slate-500">
                          {item.description}
                        </p>
                      </div>
                      <ArrowRight size={17} className="text-slate-400" />
                    </Link>
                  );
                })}
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-100 p-6">
              <div>
                <h3 className="text-lg font-bold text-slate-900">
                  Student Directory Preview
                </h3>
                <p className="mt-1 text-sm text-slate-500">
                  Current student records
                </p>
              </div>
              <Link href="/students" className="text-sm font-semibold text-blue-600 hover:underline">
                View All
              </Link>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full min-w-[550px] text-left text-sm">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-6 py-4 font-semibold">Student</th>
                    <th className="px-6 py-4 font-semibold">Email</th>
                    <th className="px-6 py-4 font-semibold">Course</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {loading ? (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">Loading students...</td></tr>
                  ) : error ? (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">Unable to load students.</td></tr>
                  ) : data?.students.length ? (
                    data.students.slice(0, 5).map((student) => (
                      <tr key={student.id} className="hover:bg-slate-50">
                        <td className="px-6 py-4 font-semibold text-slate-900">
                          {student.firstName} {student.lastName}
                        </td>
                        <td className="px-6 py-4 text-slate-600">{student.email}</td>
                        <td className="px-6 py-4 text-slate-600">{student.course || "—"}</td>
                      </tr>
                    ))
                  ) : (
                    <tr><td colSpan={3} className="px-6 py-8 text-center text-slate-500">No students available.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
