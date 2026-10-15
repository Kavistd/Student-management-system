"use client";

import { useCallback, useMemo, useState } from "react";
import { Plus, RefreshCw, SearchX, Users } from "lucide-react";

import StudentFormDialog from "@/components/admin/StudentFormDialog";
import AdminShell from "@/components/layout/AdminShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FeedbackBanner, { type Feedback } from "@/components/ui/FeedbackBanner";
import RowActions from "@/components/ui/RowActions";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { DataPanel, SearchInput, SelectFilter } from "@/components/ui/Toolbar";
import { useApiResource } from "@/hooks/useApiResource";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import type { Course } from "@/types/course";
import type { Student } from "@/types/student";

const ALL = "__all__";
const UNASSIGNED = "__unassigned__";

function fullName(student: Student) {
  return `${student.firstName} ${student.lastName}`.trim();
}

function initials(student: Student) {
  return `${student.firstName?.charAt(0) ?? ""}${student.lastName?.charAt(0) ?? ""}`.toUpperCase() || "S";
}

function CourseCell({ course }: { course: string | null }) {
  return course ? (
    <Badge tone="indigo">{course}</Badge>
  ) : (
    <span className="text-slate-400">Not assigned</span>
  );
}

export default function StudentManagement() {
  const students = useApiResource<Student[]>("/api/students");
  // Only used to suggest course names; the page works without it.
  const courses = useApiResource<Course[]>("/api/courses");

  const [search, setSearch] = useState("");
  const [courseFilter, setCourseFilter] = useState(ALL);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Student | null>(null);
  const [deleting, setDeleting] = useState<Student | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const dismissFeedback = useCallback(() => setFeedback(null), []);

  const list = useMemo(() => students.data ?? [], [students.data]);
  const canManage = !students.loading && !students.error;

  const courseSuggestions = useMemo(
    () =>
      [...new Set((courses.data ?? []).map((course) => course.courseName.trim()))].sort(),
    [courses.data]
  );

  const courseOptions = useMemo(() => {
    const recorded = new Set(
      list.map((student) => student.course?.trim()).filter((value): value is string => Boolean(value))
    );
    return [
      { value: ALL, label: "All courses" },
      { value: UNASSIGNED, label: "Not assigned" },
      ...[...recorded].sort().map((name) => ({ value: name, label: name })),
    ];
  }, [list]);

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return list.filter((student) => {
      const course = student.course?.trim() ?? "";
      if (courseFilter === UNASSIGNED && course) return false;
      if (courseFilter !== ALL && courseFilter !== UNASSIGNED && course !== courseFilter) {
        return false;
      }
      if (!query) return true;
      return [fullName(student), student.email, student.phone, course, `#${student.id}`]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    });
  }, [list, search, courseFilter]);

  const isFiltered = search.trim() !== "" || courseFilter !== ALL;

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
  }

  function openEdit(student: Student) {
    setEditing(student);
    setFormOpen(true);
  }

  function handleSaved(saved: Student, created: boolean) {
    students.setData((current) =>
      created
        ? [...current, saved]
        : current.map((student) => (student.id === saved.id ? saved : student))
    );
    setFormOpen(false);
    setFeedback({
      type: "success",
      message: `${fullName(saved)} was ${created ? "added" : "updated"} successfully.`,
    });
  }

  function openDelete(student: Student) {
    setDeleteError(null);
    setDeleting(student);
  }

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await api.delete(`/api/students/${deleting.id}`);
      students.setData((current) => current.filter((student) => student.id !== deleting.id));
      setFeedback({ type: "success", message: `${fullName(deleting)} was deleted.` });
      setDeleting(null);
    } catch (err) {
      setDeleteError(toApiError(err).message);
    } finally {
      setDeleteBusy(false);
    }
  }

  return (
    <AdminShell
      eyebrow="Academic records"
      title="Student management"
      description="Search, filter, and maintain student records."
      actions={
        <>
          <Button variant="secondary" onClick={students.reload} disabled={students.loading}>
            <RefreshCw size={16} className={students.loading ? "animate-spin" : ""} aria-hidden />
            Refresh
          </Button>
          <Button
            onClick={openCreate}
            disabled={!canManage}
            title={canManage ? undefined : "Available once student records can be loaded"}
          >
            <Plus size={16} aria-hidden />
            Add student
          </Button>
        </>
      }
    >
      <FeedbackBanner feedback={feedback} onDismiss={dismissFeedback} />

      <DataPanel
        title="All students"
        description="Search by name, email, phone, course, or ID."
        filters={
          <>
            <SearchInput
              value={search}
              onChange={setSearch}
              label="Search students"
              placeholder="Search students…"
              disabled={!canManage}
            />
            <SelectFilter
              value={courseFilter}
              onChange={setCourseFilter}
              label="Filter by course"
              options={courseOptions}
              disabled={!canManage}
            />
          </>
        }
        footer={canManage && list.length > 0 ? `Showing ${filtered.length} of ${list.length} students` : undefined}
      >
        {students.loading ? (
          <LoadingState label="Loading student records…" />
        ) : students.error ? (
          <ErrorState error={students.error} onRetry={students.reload} />
        ) : list.length === 0 ? (
          <EmptyState
            icon={Users}
            title="No students yet"
            description="Add your first student record to get started."
            action={
              <Button onClick={openCreate}>
                <Plus size={16} aria-hidden />
                Add student
              </Button>
            }
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No matching students"
            description="Try a different search term or course filter."
            action={
              isFiltered && (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSearch("");
                    setCourseFilter(ALL);
                  }}
                >
                  Clear filters
                </Button>
              )
            }
          />
        ) : (
          <>
            <ul className="divide-y divide-slate-100 md:hidden">
              {filtered.map((student) => (
                <li key={student.id} className="flex items-start gap-3 p-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-100 text-sm font-bold text-blue-700">
                    {initials(student)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-slate-900">{fullName(student)}</p>
                    <p className="truncate text-sm text-slate-500">{student.email}</p>
                    {student.phone && <p className="text-sm text-slate-500">{student.phone}</p>}
                    <div className="mt-2">
                      <CourseCell course={student.course} />
                    </div>
                  </div>
                  <RowActions
                    itemLabel={fullName(student)}
                    onEdit={() => openEdit(student)}
                    onDelete={() => openDelete(student)}
                  />
                </li>
              ))}
            </ul>

            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Student</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Email</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Phone</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Course</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filtered.map((student) => (
                    <tr key={student.id} className="transition hover:bg-slate-50">
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-100 text-xs font-bold text-blue-700">
                            {initials(student)}
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">{fullName(student)}</p>
                            <p className="text-xs text-slate-400">ID #{student.id}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-slate-600">{student.email}</td>
                      <td className="px-5 py-3.5 text-slate-600">
                        {student.phone || <span className="text-slate-400">—</span>}
                      </td>
                      <td className="px-5 py-3.5">
                        <CourseCell course={student.course} />
                      </td>
                      <td className="px-5 py-3.5">
                        <RowActions
                          itemLabel={fullName(student)}
                          onEdit={() => openEdit(student)}
                          onDelete={() => openDelete(student)}
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

      <StudentFormDialog
        open={formOpen}
        student={editing}
        courseSuggestions={courseSuggestions}
        onClose={() => setFormOpen(false)}
        onSaved={handleSaved}
      />

      <ConfirmDialog
        open={deleting !== null}
        title="Delete student?"
        confirmLabel="Delete student"
        busy={deleteBusy}
        error={deleteError}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleting(null)}
      >
        {deleting && (
          <p>
            This permanently removes the record for{" "}
            <span className="font-semibold text-slate-900">{fullName(deleting)}</span> (
            {deleting.email}). This cannot be undone.
          </p>
        )}
      </ConfirmDialog>
    </AdminShell>
  );
}
