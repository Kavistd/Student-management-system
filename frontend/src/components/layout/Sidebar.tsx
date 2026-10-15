
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  GraduationCap,
  LayoutDashboard,
  Users,
  BookOpen,
  LogOut,
} from "lucide-react";

const menuItems = [
  { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { name: "Students", href: "/students", icon: Users },
  { name: "Courses", href: "/courses", icon: BookOpen },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden min-h-screen w-64 shrink-0 border-r border-slate-200 bg-white md:flex md:flex-col">
      <div className="flex h-20 items-center gap-3 border-b border-slate-100 px-6">
        <div className="rounded-xl bg-blue-600 p-2 text-white">
          <GraduationCap size={24} />
        </div>
        <div>
          <h1 className="text-lg font-bold text-slate-900">
            StudentMS
          </h1>
          <p className="text-xs text-slate-500">
            Management Portal
          </p>
        </div>
      </div>

      <nav className="flex-1 space-y-2 px-4 py-6">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = pathname === item.href;

          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition ${
                active
                  ? "bg-blue-50 text-blue-700"
                  : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              <Icon size={19} />
              {item.name}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-slate-100 p-4">
        <Link
          href="/login"
          className="flex items-center gap-3 rounded-xl px-4 py-3 text-sm text-slate-600 hover:bg-slate-100"
        >
          <LogOut size={19} />
          Login
        </Link>
      </div>
    </aside>
  );
}
