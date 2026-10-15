"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Menu, X } from "lucide-react";

import { useAuthUser } from "@/components/auth/AuthProvider";
import Badge from "@/components/ui/Badge";
import { SidebarContent } from "@/components/layout/Sidebar";
import { isActivePath, type PortalConfig } from "@/components/layout/portals";

export default function Navbar({ portal }: { portal: PortalConfig }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const pathname = usePathname();
  const user = useAuthUser();
  const current = portal.navigation.find((item) => isActivePath(pathname, item.href));

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = "";
    };
  }, [menuOpen]);

  return (
    <>
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6 lg:px-8">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            className="rounded-lg p-2 text-slate-700 hover:bg-slate-100 lg:hidden"
            aria-label="Open navigation menu"
            aria-expanded={menuOpen}
            aria-controls="portal-mobile-nav"
          >
            <Menu size={22} aria-hidden />
          </button>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-slate-900">
              {current?.name ?? portal.label}
            </p>
            <p className="hidden text-xs text-slate-500 sm:block">
              Student Management System
            </p>
          </div>
        </div>

        {user && (
          <div className="flex min-w-0 items-center gap-3">
            <div className="hidden min-w-0 text-right sm:block">
              <p className="truncate text-sm font-semibold text-slate-900">{user.username}</p>
              <p className="truncate text-xs text-slate-500">{user.email}</p>
            </div>
            <div
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-700"
              aria-hidden
            >
              {user.username.charAt(0).toUpperCase()}
            </div>
            <Badge tone={user.role === "ADMIN" ? "amber" : "emerald"}>
              {user.role === "ADMIN" ? "Admin" : "Student"}
            </Badge>
          </div>
        )}
      </header>

      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" id="portal-mobile-nav">
          <div
            className="absolute inset-0 bg-slate-950/50 backdrop-blur-sm"
            onClick={() => setMenuOpen(false)}
            aria-hidden
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Navigation menu"
            className="absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-slate-950 shadow-2xl"
          >
            <button
              type="button"
              onClick={() => setMenuOpen(false)}
              className="absolute right-3 top-3 rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white"
              aria-label="Close navigation menu"
              autoFocus
            >
              <X size={20} aria-hidden />
            </button>
            <SidebarContent portal={portal} onNavigate={() => setMenuOpen(false)} />
          </div>
        </div>
      )}
    </>
  );
}
