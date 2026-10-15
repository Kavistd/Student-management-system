"use client";

import { useMemo, useState } from "react";
import { BookOpen, CalendarDays, GraduationCap, RefreshCw, SearchX } from "lucide-react";

import StudentShell from "@/components/layout/StudentShell";
import CurrentStudentGate from "@/components/student/CurrentStudentGate";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { DataPanel, SearchInput } from "@/components/ui/Toolbar";
import { useApiResource } from "@/hooks/useApiResource";
import { useCurrentStudent } from "@/hooks/useCurrentStudent";
import type { Course } from "@/types/course";

function formatDuration(months: number | null) {
  if (months == null) return "Duration not specified";
  return `${months} month${months === 1 ? "" : "s"}`;
}

/** Student records store the course as text; match it to a catalog entry by name or code. */
function findCourse(courses: Course[] | null, recorded: string) {
  const key = recorded.trim().toLowerCase();
  return courses?.find(
    (course) =>
      course.courseName.trim().toLowerCase() === key ||
      course.courseCode.trim().toLowerCase() === key
  );
}

function EnrolledCourse({ recorded, catalog }: { recorded: string; catalog: Course[] | null }) {
  const course = findCourse(catalog, recorded);

  return (
    <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start">
      <div className="w-fit rounded-2xl bg-violet-50 p-3.5 text-violet-600">
        <GraduationCap size={26} aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        {course && <Badge tone="blue">{course.courseCode}</Badge>}
        <h3 className="mt-2 text-lg font-bold text-slate-900">
          {course?.courseName ?? recorded}
        </h3>
        {course ? (
          <>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              {course.description || "No description has been added for this course."}
            </p>
            <p className="mt-3 inline-flex items-center gap-1.5 text-sm text-slate-500">
              <CalendarDays size={16} aria-hidden />
              {formatDuration(course.durationMonths)}
            </p>
          </>
        ) : (
          <p className="mt-1 text-sm text-slate-500">
            Further course details appear when this course can be matched to the catalog.
          </p>
        )}
      </div>
    </div>
  );
}

export default function StudentCourses() {
  const current = useCurrentStudent();
  const catalog = useApiResource<Course[]>("/api/courses");
  const [search, setSearch] = useState("");

  const list = useMemo(() => catalog.data ?? [], [catalog.data]);
  const ready = !catalog.loading && !catalog.error;

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return [...list]
      .sort((a, b) => a.courseCode.localeCompare(b.courseCode))
      .filter(
        (course) =>
          !query ||
          [course.courseCode, course.courseName, course.description]
            .filter(Boolean)
            .some((value) => String(value).toLowerCase().includes(query))
      );
  }, [list, search]);

  return (
    <StudentShell
      eyebrow="My workspace"
      title="My courses"
      description="Your enrolled course and the courses offered by your institution."
      actions={
        <Button variant="secondary" onClick={catalog.reload} disabled={catalog.loading}>
          <RefreshCw size={16} className={catalog.loading ? "animate-spin" : ""} aria-hidden />
          Refresh
        </Button>
      }
    >
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-5">
          <h2 className="text-base font-bold text-slate-900">Enrolled course</h2>
          <p className="mt-0.5 text-sm text-slate-500">The course on your student record</p>
        </div>
        <CurrentStudentGate state={current} subject="your course">
          {(student) =>
            student.course?.trim() ? (
              <EnrolledCourse recorded={student.course} catalog={catalog.data} />
            ) : (
              <EmptyState
                icon={BookOpen}
                title="No course assigned yet"
                description="Your administrator hasn't assigned a course to your record yet."
              />
            )
          }
        </CurrentStudentGate>
      </section>

      <DataPanel
        title="Course catalog"
        description="Browse the courses offered by your institution."
        filters={
          <SearchInput
            value={search}
            onChange={setSearch}
            label="Search the course catalog"
            placeholder="Search courses…"
            disabled={!ready}
          />
        }
        footer={ready && list.length > 0 ? `Showing ${filtered.length} of ${list.length} courses` : undefined}
      >
        {catalog.loading ? (
          <LoadingState label="Loading the course catalog…" />
        ) : catalog.error ? (
          <ErrorState error={catalog.error} onRetry={catalog.reload} />
        ) : list.length === 0 ? (
          <EmptyState
            icon={BookOpen}
            title="No courses published"
            description="Courses will appear here once your institution adds them."
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No matching courses"
            description="Try a different course name or code."
            action={
              <Button variant="secondary" onClick={() => setSearch("")}>
                Clear search
              </Button>
            }
          />
        ) : (
          <ul className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-3">
            {filtered.map((course) => (
              <li
                key={course.id}
                className="flex flex-col rounded-2xl border border-slate-200 p-5 transition hover:border-blue-200 hover:shadow-sm"
              >
                <Badge tone="blue">{course.courseCode}</Badge>
                <h3 className="mt-3 font-semibold text-slate-900">{course.courseName}</h3>
                <p className="mt-1 line-clamp-3 flex-1 text-sm leading-6 text-slate-500">
                  {course.description || "No description provided."}
                </p>
                <p className="mt-4 inline-flex items-center gap-1.5 text-xs font-medium text-slate-500">
                  <CalendarDays size={14} aria-hidden />
                  {formatDuration(course.durationMonths)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </DataPanel>
    </StudentShell>
  );
}
