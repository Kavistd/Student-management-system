
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  CalendarDays,
  GraduationCap,
  RefreshCw,
  Search,
  ShieldCheck,
} from "lucide-react";

import api from "@/lib/api";
import type { Course } from "@/types/course";

export default function CourseDirectory() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const fetchCourses = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await api.get<Course[]>("/api/courses");
      setCourses(response.data);
    } catch (err) {
      setCourses([]);

      if (axios.isAxiosError(err)) {
        setError(
          err.response?.status === 401 || err.response?.status === 403
            ? "Course records require administrator authentication."
            : typeof err.response?.data?.message === "string"
              ? err.response.data.message
              : "Unable to load courses. Please check the backend connection."
        );
      } else {
        setError("An unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchCourses();
  }, [fetchCourses]);

  const filteredCourses = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) return courses;

    return courses.filter((course) =>
      [
        course.courseCode,
        course.courseName,
        course.description,
        course.durationMonths,
        course.id,
      ]
        .filter((value) => value !== null && value !== undefined)
        .some((value) => String(value).toLowerCase().includes(query))
    );
  }, [courses, search]);

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
              Course Directory
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              Explore and organize courses offered by your institution.
            </p>
          </div>

          <button
            type="button"
            onClick={() => void fetchCourses()}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={17} className={loading ? "animate-spin" : ""} />
            Refresh Courses
          </button>
        </div>

        <section className="grid gap-5 sm:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="inline-flex rounded-xl bg-blue-50 p-3 text-blue-600">
              <BookOpen size={24} />
            </div>
            <p className="mt-5 text-sm font-medium text-slate-500">
              Total Courses
            </p>
            <p className="mt-2 text-3xl font-bold text-slate-900">
              {loading ? "..." : error ? "Pending" : courses.length}
            </p>
            <p className="mt-2 text-xs text-slate-400">
              {error ? "Available after login" : "Courses in the database"}
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="inline-flex rounded-xl bg-violet-50 p-3 text-violet-600">
              <Search size={24} />
            </div>
            <p className="mt-5 text-sm font-medium text-slate-500">
              Search Results
            </p>
            <p className="mt-2 text-3xl font-bold text-slate-900">
              {loading ? "..." : error ? "Pending" : filteredCourses.length}
            </p>
            <p className="mt-2 text-xs text-slate-400">
              {error ? "Available after login" : "Matching course records"}
            </p>
          </div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-5 border-b border-slate-100 p-6">
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                Available Courses
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Search by course code, name, description, or duration.
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
                placeholder="Search courses..."
                aria-label="Search courses"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          </div>

          {loading ? (
            <div role="status" className="flex items-center justify-center gap-3 p-16 text-sm text-slate-500">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
              Loading courses...
            </div>
          ) : error ? (
            <div role="alert" className="flex flex-col items-center gap-4 p-16 text-center">
              <AlertCircle size={36} className="text-amber-500" />
              <p className="max-w-md text-sm text-slate-600">{error}</p>
              <button
                type="button"
                onClick={() => void fetchCourses()}
                className="rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-700"
              >
                Try Again
              </button>
            </div>
          ) : filteredCourses.length === 0 ? (
            <div className="p-16 text-center">
              <BookOpen size={38} className="mx-auto text-slate-300" />
              <h3 className="mt-4 font-semibold text-slate-800">
                {search ? "No matching courses" : "No courses found"}
              </h3>
              <p className="mt-2 text-sm text-slate-500">
                {search
                  ? "Try searching with a different course name or code."
                  : "Course records will appear here when available."}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[800px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-6 py-4 font-semibold">Course</th>
                    <th className="px-6 py-4 font-semibold">Code</th>
                    <th className="px-6 py-4 font-semibold">Description</th>
                    <th className="px-6 py-4 font-semibold">Duration</th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100">
                  {filteredCourses.map((course) => (
                    <tr key={course.id} className="transition hover:bg-blue-50/30">
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-3">
                          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-100 text-indigo-700">
                            <BookOpen size={20} />
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">
                              {course.courseName}
                            </p>
                            <p className="mt-1 text-xs text-slate-400">
                              Course #{course.id}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        <span className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-bold text-blue-700">
                          {course.courseCode}
                        </span>
                      </td>
                      <td className="max-w-sm px-6 py-5 text-slate-600">
                        {course.description || "No description provided"}
                      </td>
                      <td className="px-6 py-5">
                        <span className="inline-flex items-center gap-2 text-slate-600">
                          <CalendarDays size={16} className="text-slate-400" />
                          {course.durationMonths != null
                            ? `${course.durationMonths} months`
                            : "Not specified"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!loading && !error && (
            <div className="border-t border-slate-100 px-6 py-4 text-xs text-slate-500">
              Showing {filteredCourses.length} of {courses.length} courses
            </div>
          )}
        </section>

        <div className="flex items-center justify-center gap-2 text-xs text-slate-400">
          <ShieldCheck size={15} />
          StudentMS Course Directory · Authentication integration pending
        </div>
      </main>
    </div>
  );
}
