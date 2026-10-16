"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { GraduationCap } from "lucide-react";

import { useAuthUser } from "@/components/auth/AuthProvider";
import SignOutButton from "@/components/auth/SignOutButton";
import { isActivePath, type PortalConfig } from "@/components/layout/portals";

/** Portal navigation content, shared by the desktop rail and the mobile drawer. */
export function SidebarContent({
  portal,
  onNavigate,
}: {
  portal: PortalConfig;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const user = useAuthUser();

  return (
    <div className="flex h-full flex-col px-4 py-6">
      <Link
        href={portal.homeHref}
        onClick={onNavigate}
        className="flex items-center gap-3 px-2"
      >
        <div className="rounded-xl bg-blue-600 p-2.5 text-white">
          <GraduationCap size={22} aria-hidden />
        </div>
        <div>
          <p className="text-lg font-bold text-white">StudentMS</p>
          <p className="text-xs text-slate-400">{portal.label}</p>
        </div>
      </Link>

      <p className="mb-3 mt-10 px-3 text-xs font-semibold uppercase tracking-widest text-slate-500">
        {portal.navLabel}
      </p>

      <nav aria-label={portal.label} className="space-y-1">
        {portal.navigation.map((item) => {
          const Icon = item.icon;
          const active = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={`flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition focus-visible:outline-2 focus-visible:outline-blue-400 ${
                active
                  ? "bg-blue-600 text-white shadow-sm shadow-blue-900/40"
                  : "text-slate-300 hover:bg-slate-800 hover:text-white"
              }`}
            >
              <Icon size={18} aria-hidden />
              {item.name}
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto space-y-1">
        {user && (
          <div className="mb-2 rounded-2xl border border-slate-800 bg-slate-900 p-4">
            <p className="text-xs text-slate-500">Signed in as</p>
            <p className="mt-0.5 truncate text-sm font-semibold text-white">{user.username}</p>
            <p className="truncate text-xs text-slate-400">{user.email}</p>
          </div>
        )}
        <SignOutButton
          onSignedOut={onNavigate}
          className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:opacity-60"
        />
      </div>
    </div>
  );
}

export default function Sidebar({ portal }: { portal: PortalConfig }) {
  return (
    <aside className="sticky top-0 hidden h-screen w-64 shrink-0 bg-slate-950 lg:block">
      <SidebarContent portal={portal} />
    </aside>
  );
}
