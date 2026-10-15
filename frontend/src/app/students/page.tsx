
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  GraduationCap,
  RefreshCw,
  Search,
  Users,
} from "lucide-react";

import api from "@/lib/api";
import type { Student } from "@/types/student";

export default function StudentDirectory() {
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const fetchStudents = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await api.get<Student[]>("/api/students");
      setStudents(response.data);
    } catch (err) {
      setStudents([]);
      if (axios.isAxiosError(err)) {
        setError(
          err.response?.status === 401 || err.response?.status === 403
            ? "Student records require administrator authentication."
            : err.response?.data?.message ||
                "Unable to load students. Please check the backend connection."
        );
      } else {
        setError("An unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchStudents();
  }, [fetchStudents]);

  const filteredStudents = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) return students;

    return students.filter((student) =>
      [
        student.firstName,
        student.lastName,
        student.email,
        student.phone,
        student.course,
        String(student.id),
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query))
    );
  }, [students, search]);

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-5 lg:px-8">
          <Link href="/admin/dashboard" className="flex items-center gap-3">
            <div className="rounded-xl bg-blue-600 p-2.5 text-white">
              <GraduationCap size={23} />
            </div>
            <div>
              <p className="text-lg font-extrabold text-slate-900">StudentMS</p>
              <p className="text-xs text-slate-500">Administration</p>
            </div>
          </Link>

          <Link
            href="/admin/dashboard"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            <ArrowLeft size={17} />
            Admin Overview
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl space-y-7 px-5 py-9 lg:px-8">
        <div className="flex flex-wrap items-center justify-between gap-5">
          <div>
            <p className="text-sm font-bold uppercase tracking-widest text-blue-600">
              Academic Administration
            </p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
              Student Directory
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              Browse and find student records from your academic workspace.
            </p>
          </div>

          <button
            type="button"
            onClick={() => void fetchStudents()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={17} className={loading ? "animate-spin" : ""} />
            Refresh Records
          </button>
        </div>

        <section className="grid gap-5 sm:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="inline-flex rounded-xl bg-blue-50 p-3 text-blue-600">
              <Users size={24} />
            </div>
            <p className="mt-5 text-sm font-medium text-slate-500">
              Total Students
            </p>
            <p className="mt-2 text-3xl font-bold text-slate-900">
              {loading ? "..." : error ? "Pending" : students.length}
            </p>
            <p className="mt-2 text-xs text-slate-400">
              Registered student records
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="inline-flex rounded-xl bg-violet-50 p-3 text-violet-600">
              <BookOpen size={24} />
            </div>
            <p className="mt-5 text-sm font-medium text-slate-500">
              Search Results
            </p>
            <p className="mt-2 text-3xl font-bold text-slate-900">
              {loading ? "..." : error ? "Pending" : filteredStudents.length}
            </p>
            <p className="mt-2 text-xs text-slate-400">
              Students matching your search
            </p>
          </div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-5 border-b border-slate-100 p-6">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                All Students
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Search by name, email, student ID, phone, or course.
              </p>
            </div>

            <div className="relative w-full sm:w-80">
              <Search
                size={19}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search students..."
                aria-label="Search students"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-sm text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          </div>

          {loading ? (
            <div role="status" className="flex items-center justify-center gap-3 p-16 text-sm text-slate-500">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
              Loading student records...
            </div>
          ) : error ? (
            <div role="alert" className="flex flex-col items-center gap-4 p-16 text-center">
              <AlertCircle size={36} className="text-amber-500" />
              <p className="max-w-md text-sm text-slate-600">{error}</p>
              <button
                type="button"
                onClick={() => void fetchStudents()}
                className="rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-700"
              >
                Try Again
              </button>
            </div>
          ) : filteredStudents.length === 0 ? (
            <div className="p-16 text-center">
              <Users size={38} className="mx-auto text-slate-300" />
              <h3 className="mt-4 font-semibold text-slate-800">
                {search ? "No matching students" : "No students found"}
              </h3>
              <p className="mt-2 text-sm text-slate-500">
                {search
                  ? "Try searching with a different name, email, or course."
                  : "Student records will appear here when available."}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[780px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-6 py-4 font-semibold">Student</th>
                    <th className="px-6 py-4 font-semibold">Student ID</th>
                    <th className="px-6 py-4 font-semibold">Email Address</th>
                    <th className="px-6 py-4 font-semibold">Phone</th>
                    <th className="px-6 py-4 font-semibold">Course</th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100">
                  {filteredStudents.map((student) => {
                    const initials = `${student.firstName?.charAt(0) || ""}${student.lastName?.charAt(0) || ""}`.toUpperCase();

                    return (
                      <tr key={student.id} className="transition hover:bg-blue-50/30">
                        <td className="px-6 py-5">
                          <div className="flex items-center gap-3">
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-100 text-sm font-bold text-blue-700">
                              {initials || "S"}
                            </div>
                            <div>
                              <p className="font-semibold text-slate-900">
                                {student.firstName} {student.lastName}
                              </p>
                              <p className="mt-1 text-xs text-slate-400">
                                Student Record
                              </p>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-5 font-medium text-slate-600">
                          #{student.id}
                        </td>
                        <td className="px-6 py-5 text-slate-600">
                          {student.email}
                        </td>
                        <td className="px-6 py-5 text-slate-600">
                          {student.phone || "Not provided"}
                        </td>
                        <td className="px-6 py-5">
                          {student.course ? (
                            <span className="inline-flex rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700">
                              {student.course}
                            </span>
                          ) : (
                            <span className="text-slate-400">Not assigned</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {!loading && !error && (
            <div className="border-t border-slate-100 px-6 py-4 text-xs text-slate-500">
              Showing {filteredStudents.length} of {students.length} students
            </div>
          )}
        </section>

        <p className="text-center text-xs text-slate-400">
          StudentMS · Academic Student Directory
        </p>
      </main>
    </div>
  );
}
