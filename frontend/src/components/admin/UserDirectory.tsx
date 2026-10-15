"use client";

import { useMemo, useState } from "react";
import { Info, RefreshCw, SearchX, UserCog } from "lucide-react";

import AdminShell from "@/components/layout/AdminShell";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/StatePanel";
import { DataPanel, SearchInput, SelectFilter } from "@/components/ui/Toolbar";
import { useApiResource } from "@/hooks/useApiResource";
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
 * Read-only on purpose: account management from the UI is not enabled yet,
 * and the API refuses to create or modify ADMIN accounts.
 */
export default function UserDirectory() {
  const users = useApiResource<User[]>("/api/users");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<string>("ALL");

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

  return (
    <AdminShell
      eyebrow="Access"
      title="User accounts"
      description="Review registered login accounts and their roles."
      actions={
        <Button variant="secondary" onClick={users.reload} disabled={users.loading}>
          <RefreshCw size={16} className={users.loading ? "animate-spin" : ""} aria-hidden />
          Refresh
        </Button>
      }
    >
      <div className="flex items-start gap-3 rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
        <Info size={18} className="mt-0.5 shrink-0" aria-hidden />
        <p>
          This view is read-only. Students create their own accounts from the Sign Up
          page; administrator accounts are provisioned separately.
        </p>
      </div>

      <DataPanel
        title="Registered accounts"
        description="Search by username, email, or ID."
        filters={
          <>
            <SearchInput
              value={search}
              onChange={setSearch}
              label="Search users"
              placeholder="Search users…"
              disabled={!ready}
            />
            <SelectFilter
              value={role}
              onChange={setRole}
              label="Filter by role"
              options={roleOptions}
              disabled={!ready}
            />
          </>
        }
        footer={ready && list.length > 0 ? `Showing ${filtered.length} of ${list.length} accounts` : undefined}
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
            <table className="w-full min-w-[520px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold">Username</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Email</th>
                  <th scope="col" className="px-5 py-3 font-semibold">Role</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3.5">
                      <p className="font-semibold text-slate-900">{user.username}</p>
                      <p className="text-xs text-slate-400">ID #{user.id}</p>
                    </td>
                    <td className="px-5 py-3.5 text-slate-600">{user.email}</td>
                    <td className="px-5 py-3.5">
                      <RoleBadge role={user.role} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </DataPanel>
    </AdminShell>
  );
}
