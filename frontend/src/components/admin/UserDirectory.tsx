"use client";

import { useCallback, useMemo, useState } from "react";
import { LockKeyhole, RefreshCw, SearchX, Trash2, UserCog } from "lucide-react";

import AdminShell from "@/components/layout/AdminShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import FeedbackBanner, { type Feedback } from "@/components/ui/FeedbackBanner";
import Pagination from "@/components/ui/Pagination";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { DataPanel, SearchInput, SelectFilter } from "@/components/ui/Toolbar";
import { useApiResource } from "@/hooks/useApiResource";
import { usePagination } from "@/hooks/usePagination";
import api from "@/lib/api";
import { toApiError } from "@/lib/apiError";
import type { User, UserRole } from "@/types/user";

const roleOptions: { value: "ALL" | UserRole; label: string }[] = [
  { value: "ALL", label: "All roles" },
  { value: "ADMIN", label: "Admins" },
  { value: "STUDENT", label: "Students" },
];

function RoleBadge({ role }: { role: UserRole }) {
  return role === "ADMIN" ? (
    <Badge tone="amber">Admin</Badge>
  ) : (
    <Badge tone="emerald">Student</Badge>
  );
}

/**
 * Admins can delete student accounts (the API also removes the linked student
 * record and sessions). ADMIN accounts are protected: the API refuses to
 * modify or delete them, so no delete action is offered for them.
 */
export default function UserDirectory() {
  const users = useApiResource<User[]>("/api/users");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<string>("ALL");
  const [deleting, setDeleting] = useState<User | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const dismissFeedback = useCallback(() => setFeedback(null), []);

  const list = useMemo(() => users.data ?? [], [users.data]);
  const ready = !users.loading && !users.error;

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return [...list]
      .sort((a, b) => a.username.localeCompare(b.username))
      .filter((user) => role === "ALL" || user.role === role)
      .filter(
        (user) =>
          !query ||
          [user.username, user.email, `#${user.id}`].some((value) =>
            value.toLowerCase().includes(query)
          )
      );
  }, [list, search, role]);

  const paged = usePagination(filtered);

  function openDelete(user: User) {
    setDeleteError(null);
    setDeleting(user);
  }

  async function confirmDelete() {
    if (!deleting) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await api.delete(`/api/users/${deleting.id}`);
      users.setData((current) => current.filter((user) => user.id !== deleting.id));
      setFeedback({ type: "success", message: `${deleting.username}'s account was deleted.` });
      setDeleting(null);
    } catch (err) {
      setDeleteError(toApiError(err).message);
    } finally {
      setDeleteBusy(false);
    }
  }

  return (
    <AdminShell
      eyebrow="Access"
      title="User accounts"
      description="Review registered login accounts and remove student accounts."
      actions={
        <Button variant="secondary" onClick={users.reload} disabled={users.loading}>
          <RefreshCw size={16} className={users.loading ? "animate-spin" : ""} aria-hidden />
          Refresh
        </Button>
      }
    >
      <FeedbackBanner feedback={feedback} onDismiss={dismissFeedback} />

      <DataPanel
        title="Registered accounts"
        description="Search by username, email, or ID."
        filters={
          <>
            <SearchInput
              value={search}
              onChange={(value) => {
                setSearch(value);
                paged.setPage(1);
              }}
              label="Search users"
              placeholder="Search users…"
              disabled={!ready}
            />
            <SelectFilter
              value={role}
              onChange={(value) => {
                setRole(value);
                paged.setPage(1);
              }}
              label="Filter by role"
              options={roleOptions}
              disabled={!ready}
            />
          </>
        }
        footer={
          ready && list.length > 0 ? (
            <Pagination
              page={paged.page}
              pageCount={paged.pageCount}
              firstItem={paged.firstItem}
              lastItem={paged.lastItem}
              total={paged.total}
              unfilteredTotal={list.length}
              noun="accounts"
              onPageChange={paged.setPage}
            />
          ) : undefined
        }
      >
        {users.loading ? (
          <LoadingState label="Loading user accounts…" />
        ) : users.error ? (
          <ErrorState error={users.error} onRetry={users.reload} />
        ) : list.length === 0 ? (
          <EmptyState
            icon={UserCog}
            title="No accounts yet"
            description="Accounts appear here when students register."
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No matching accounts"
            description="Try a different search term or role."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold">Username</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Email</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Role</th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {paged.pageItems.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3.5">
                      <p className="font-semibold text-slate-900">{user.username}</p>
                      <p className="text-xs text-slate-400">ID #{user.id}</p>
                    </td>
                    <td className="px-5 py-3.5 text-slate-600">{user.email}</td>
                    <td className="px-5 py-3.5">
                      <RoleBadge role={user.role} />
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {user.role === "ADMIN" ? (
                        <span
                          className="inline-flex items-center gap-1.5 text-xs text-slate-400"
                          title="Administrator accounts cannot be deleted from the dashboard"
                        >
                          <LockKeyhole size={14} aria-hidden />
                          Protected
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => openDelete(user)}
                          aria-label={`Delete account ${user.username}`}
                          title="Delete account"
                          className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-red-600 transition hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-red-600"
                        >
                          <Trash2 size={16} aria-hidden />
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </DataPanel>

      <ConfirmDialog
        open={deleting !== null}
        title="Delete user account?"
        confirmLabel="Delete account"
        busy={deleteBusy}
        error={deleteError}
        onConfirm={() => void confirmDelete()}
        onClose={() => setDeleting(null)}
      >
        {deleting && (
          <p>
            You are about to delete{" "}
            <span className="font-semibold text-slate-900">{deleting.username}</span> (
            {deleting.email}). Their login, their student record and any active sessions
            will be removed. This cannot be undone.
          </p>
        )}
      </ConfirmDialog>
    </AdminShell>
  );
}
