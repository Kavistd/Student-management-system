"use client";

import { useCallback, useMemo, useState } from "react";
import { BookOpen, CalendarDays, Plus, RefreshCw, SearchX } from "lucide-react";

import CourseFormDialog from "@/components/admin/CourseFormDialog";
import AdminShell from "@/components/layout/AdminShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FeedbackBanner, { type Feedback } from "@/components/ui/FeedbackBanner";
import Pagination from "@/components/ui/Pagination";
import RowActions from "@/components/ui/RowActions";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { DataPanel, SearchInput } from "@/components/ui/Toolbar";
import { useApiResource } from "@/hooks/useApiResource";
import { usePagination } from "@/hooks/usePagination";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import type { Course } from "@/types/course";

function formatDuration(months: number | null) {
  if (months == null) return "Not specified";
  return `${months} month${months === 1 ? "" : "s"}`;
}

export default function CourseDirectory() {
  const courses = useApiResource<Course[]>("/api/courses");

  const [search, setSearch] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Course | null>(null);
  const [deleting, setDeleting] = useState<Course | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const dismissFeedback = useCallback(() => setFeedback(null), []);

  const list = useMemo(() => courses.data ?? [], [courses.data]);
  const canManage = !courses.loading && !courses.error;

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    const sorted = [...list].sort((a, b) => a.courseCode.localeCompare(b.courseCode));
    if (!query) return sorted;
    return sorted.filter((course) =>
      [course.courseCode, course.courseName, course.description, `#${course.id}`]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query))
    );
  }, [list, search]);

  const paged = usePagination(filtered);

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
  }

  function openEdit(course: Course) {
    setEditing(course);
    setFormOpen(true);
  }

  function handleSaved(saved: Course, created: boolean) {
    courses.setData((current) =>
      created
        ? [...current, saved]
        : current.map((course) => (course.id === saved.id ? saved : course))
    );
    setFormOpen(false);
    setFeedback({
      type: "success",
      message: `${saved.courseCode} — ${saved.courseName} was ${created ? "added" : "updated"} successfully.`,
    });
  }

  function openDelete(course: Course) {
    setDeleteError(null);
    setDeleting(course);
  }

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await api.delete(`/api/courses/${deleting.id}`);
      courses.setData((current) => current.filter((course) => course.id !== deleting.id));
      setFeedback({ type: "success", message: `${deleting.courseCode} was deleted.` });
      setDeleting(null);
    } catch (err) {
      setDeleteError(toApiError(err).message);
    } finally {
      setDeleteBusy(false);
    }
  }

  return (
    <AdminShell
      eyebrow="Academic catalog"
      title="Course management"
      description="Maintain the courses offered by your institution."
      actions={
        <>
          <Button variant="secondary" onClick={courses.reload} disabled={courses.loading}>
            <RefreshCw size={16} className={courses.loading ? "animate-spin" : ""} aria-hidden />
            Refresh
          </Button>
          <Button
            onClick={openCreate}
            disabled={!canManage}
            title={canManage ? undefined : "Available once courses can be loaded"}
          >
            <Plus size={16} aria-hidden />
            Add course
          </Button>
        </>
      }
    >
      <FeedbackBanner feedback={feedback} onDismiss={dismissFeedback} />

      <DataPanel
        title="Course catalog"
        description="Search by code, name, or description."
        filters={
          <SearchInput
            value={search}
            onChange={(value) => {
              setSearch(value);
              paged.setPage(1);
            }}
            label="Search courses"
            placeholder="Search courses…"
            disabled={!canManage}
          />
        }
        footer={
          canManage && list.length > 0 ? (
            <Pagination
              page={paged.page}
              pageCount={paged.pageCount}
              firstItem={paged.firstItem}
              lastItem={paged.lastItem}
              total={paged.total}
              unfilteredTotal={list.length}
              noun="courses"
              onPageChange={paged.setPage}
            />
          ) : undefined
        }
      >
        {courses.loading ? (
          <LoadingState label="Loading courses…" />
        ) : courses.error ? (
          <ErrorState error={courses.error} onRetry={courses.reload} />
        ) : list.length === 0 ? (
          <EmptyState
            icon={BookOpen}
            title="No courses yet"
            description="Add your first course to build the catalog."
            action={
              <Button onClick={openCreate}>
                <Plus size={16} aria-hidden />
                Add course
              </Button>
            }
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No matching courses"
            description="Try searching with a different course name or code."
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  setSearch("");
                  paged.setPage(1);
                }}
              >
                Clear search
              </Button>
            }
          />
        ) : (
          <>
            <ul className="divide-y divide-slate-100 md:hidden">
              {paged.pageItems.map((course) => (
                <li key={course.id} className="flex items-start gap-3 p-4">
                  <div className="min-w-0 flex-1">
                    <Badge tone="blue">{course.courseCode}</Badge>
                    <p className="mt-2 font-semibold text-slate-900">{course.courseName}</p>
                    {course.description && (
                      <p className="mt-1 line-clamp-2 text-sm text-slate-500">{course.description}</p>
                    )}
                    <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-slate-500">
                      <CalendarDays size={14} aria-hidden />
                      {formatDuration(course.durationMonths)}
                    </p>
                  </div>
                  <RowActions
                    itemLabel={course.courseName}
                    onEdit={() => openEdit(course)}
                    onDelete={() => openDelete(course)}
                  />
                </li>
              ))}
            </ul>

            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Code</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Course</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Duration</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {paged.pageItems.map((course) => (
                    <tr key={course.id} className="align-top transition hover:bg-slate-50">
                      <td className="px-5 py-4">
                        <Badge tone="blue">{course.courseCode}</Badge>
                      </td>
                      <td className="max-w-md px-5 py-4">
                        <p className="font-semibold text-slate-900">{course.courseName}</p>
                        <p className="mt-0.5 line-clamp-2 text-slate-500">
                          {course.description || (
                            <span className="text-slate-400">No description</span>
                          )}
                        </p>
                      </td>
                      <td className="whitespace-nowrap px-5 py-4 text-slate-600">
                        {formatDuration(course.durationMonths)}
                      </td>
                      <td className="px-5 py-3">
                        <RowActions
                          itemLabel={course.courseName}
                          onEdit={() => openEdit(course)}
                          onDelete={() => openDelete(course)}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </DataPanel>

      <CourseFormDialog
        open={formOpen}
        course={editing}
        onClose={() => setFormOpen(false)}
        onSaved={handleSaved}
      />

      <ConfirmDialog
        open={deleting !== null}
        title="Delete course?"
        confirmLabel="Delete course"
        busy={deleteBusy}
        error={deleteError}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleting(null)}
      >
        {deleting && (
          <>
            <p>
              This permanently removes{" "}
              <span className="font-semibold text-slate-900">
                {deleting.courseCode} — {deleting.courseName}
              </span>{" "}
              from the catalog.
            </p>
            <p className="mt-2 text-slate-500">
              Student records store their course as text, so students listed under this
              course keep that value until you edit them.
            </p>
          </>
        )}
      </ConfirmDialog>
    </AdminShell>
  );
}
