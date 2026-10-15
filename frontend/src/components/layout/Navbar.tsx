
"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BookOpen,
  GraduationCap,
  LayoutDashboard,
  LogIn,
  Menu,
  X,
  Users,
} from "lucide-react";

const links = [
  { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { name: "Students", href: "/students", icon: Users },
  { name: "Courses", href: "/courses", icon: BookOpen },
];

export default function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-5 md:px-8">
        <div>
          <p className="text-sm font-semibold text-slate-900">
            Student Management System
          </p>
          <p className="text-xs text-slate-500">
            Admin Dashboard
          </p>
        </div>

        <div className="flex items-center gap-3">
          <span className="hidden text-sm text-slate-500 sm:block">
            Management Portal
          </span>

          <div className="rounded-full bg-blue-100 p-2 text-blue-700">
            <GraduationCap size={20} />
          </div>

          <button
            type="button"
            onClick={() => setMenuOpen(!menuOpen)}
            className="rounded-lg p-2 text-slate-700 hover:bg-slate-100 md:hidden"
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </header>

      {menuOpen && (
        <nav className="border-b border-slate-200 bg-white p-4 md:hidden">
          {links.map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href;

            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className={`flex items-center gap-3 rounded-lg px-4 py-3 text-sm ${
                  active
                    ? "bg-blue-50 font-semibold text-blue-700"
                    : "text-slate-600 hover:bg-slate-100"
                }`}
              >
                <Icon size={18} />
                {item.name}
              </Link>
            );
          })}

          <Link
            href="/login"
            onClick={() => setMenuOpen(false)}
            className="flex items-center gap-3 rounded-lg px-4 py-3 text-sm text-slate-600 hover:bg-slate-100"
          >
            <LogIn size={18} />
            Login
          </Link>
        </nav>
      )}
    </>
  );
}
