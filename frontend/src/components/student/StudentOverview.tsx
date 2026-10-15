
"use client";

import Link from "next/link";
import { useState } from "react";
import {
  ArrowRight,
  Bell,
  BookOpen,
  CalendarDays,
  GraduationCap,
  Home,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldCheck,
  UserRound,
  X,
} from "lucide-react";

const navigation = [
  { label: "My Overview", icon: LayoutDashboard, href: "/student/dashboard" },
  { label: "Home", icon: Home, href: "/" },
];

export default function StudentOverview() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="min-h-screen bg-slate-50 lg:flex">
      <aside className="hidden w-64 shrink-0 flex-col bg-slate-950 px-5 py-7 text-white lg:flex">
        <Link href="/" className="flex items-center gap-3 px-2">
          <div className="rounded-xl bg-blue-600 p-2.5">
            <GraduationCap size={24} />
          </div>
          <div>
            <p className="text-lg font-bold">StudentMS</p>
            <p className="text-xs text-slate-400">Student Portal</p>
          </div>
        </Link>

        <p className="mb-4 mt-12 px-3 text-xs font-semibold uppercase tracking-widest text-slate-500">
          My Workspace
        </p>

        <nav className="space-y-2">
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium ${
                  item.href === "/student/dashboard"
                    ? "bg-blue-600 text-white"
                    : "text-slate-300 hover:bg-slate-800"
                }`}
              >
                <Icon size={19} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto rounded-2xl border border-slate-700 bg-slate-900 p-4">
          <ShieldCheck size={25} className="text-blue-400" />
          <p className="mt-3 text-sm font-semibold">Student Workspace</p>
          <p className="mt-2 text-xs leading-5 text-slate-400">
            Personal account data will be connected after JWT authentication.
          </p>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-5 py-4 lg:px-9">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              aria-label="Toggle navigation"
              className="rounded-lg p-2 lg:hidden"
            >
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
            <div>
              <h1 className="text-lg font-bold">Student Portal</h1>
              <p className="text-xs text-slate-500">Your academic workspace</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-slate-100 p-2.5 text-slate-500">
              <Bell size={19} />
            </div>
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 font-bold text-blue-700">
              S
            </div>
          </div>
        </header>

        {menuOpen && (
          <nav className="border-b border-slate-200 bg-white p-4 lg:hidden">
            {navigation.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className="block rounded-xl px-4 py-3 text-sm hover:bg-slate-100"
              >
                {item.label}
              </Link>
            ))}
          </nav>
        )}

        <main className="mx-auto max-w-7xl space-y-7 p-5 lg:p-9">
          <div>
            <p className="text-sm font-semibold text-blue-600">MY WORKSPACE</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight">
              My Academic Overview
            </h2>
            <p className="mt-2 text-sm text-slate-500">
              Your personal academic information, all in one place.
            </p>
          </div>

          <section className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-700 to-indigo-700 p-8 text-white shadow-lg shadow-blue-100">
            <div className="absolute -right-16 -top-16 h-60 w-60 rounded-full bg-white/10" />
            <div className="relative">
              <p className="text-sm font-semibold text-blue-100">
                WELCOME TO STUDENTMS
              </p>
              <h3 className="mt-4 text-3xl font-bold">
                Your learning journey starts here.
              </h3>
              <p className="mt-3 max-w-xl text-sm leading-7 text-blue-100">
                Access your student profile and academic information
                from your personal workspace.
              </p>
              <div className="mt-6 inline-flex items-center gap-2 rounded-xl bg-white/15 px-4 py-2 text-xs font-semibold">
                <GraduationCap size={17} />
                Student Account
              </div>
            </div>
          </section>

          <section className="grid gap-5 md:grid-cols-3">
            {[
              {
                label: "My Profile",
                value: "Pending",
                description: "Profile connection",
                icon: UserRound,
                color: "bg-blue-50 text-blue-600",
              },
              {
                label: "My Course",
                value: "Pending",
                description: "Course information",
                icon: BookOpen,
                color: "bg-violet-50 text-violet-600",
              },
              {
                label: "Account Access",
                value: "Demo",
                description: "Authentication pending",
                icon: ShieldCheck,
                color: "bg-emerald-50 text-emerald-600",
              },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <div key={item.label} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <div className={`inline-flex rounded-xl p-3 ${item.color}`}>
                    <Icon size={24} />
                  </div>
                  <p className="mt-6 text-sm text-slate-500">{item.label}</p>
                  <p className="mt-2 text-2xl font-bold">{item.value}</p>
                  <p className="mt-2 text-xs text-slate-400">{item.description}</p>
                </div>
              );
            })}
          </section>

          <section className="grid gap-6 xl:grid-cols-5">
            <div className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm xl:col-span-3">
              <div className="mb-6 flex items-center gap-3">
                <div className="rounded-xl bg-blue-50 p-3 text-blue-600">
                  <UserRound size={23} />
                </div>
                <div>
                  <h3 className="text-lg font-bold">My Profile</h3>
                  <p className="text-sm text-slate-500">Student account information</p>
                </div>
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                {[
                  ["Full Name", "Available after sign in"],
                  ["Email Address", "Available after sign in"],
                  ["Student ID", "Available after sign in"],
                  ["Assigned Course", "Available after sign in"],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-xl bg-slate-50 p-4">
                    <p className="text-xs font-medium text-slate-500">{label}</p>
                    <p className="mt-2 text-sm font-semibold text-slate-700">{value}</p>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm xl:col-span-2">
              <div className="mb-6 flex items-center gap-3">
                <div className="rounded-xl bg-violet-50 p-3 text-violet-600">
                  <BookOpen size={23} />
                </div>
                <div>
                  <h3 className="text-lg font-bold">My Course</h3>
                  <p className="text-sm text-slate-500">Academic course details</p>
                </div>
              </div>

              <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 p-6 text-center">
                <BookOpen size={35} className="mx-auto text-slate-400" />
                <p className="mt-4 font-semibold">Course details unavailable</p>
                <p className="mt-2 text-sm leading-6 text-slate-500">
                  Your assigned course will appear here after account integration.
                </p>
              </div>
            </div>
          </section>

          <section className="grid gap-5 md:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <CalendarDays size={24} className="text-blue-600" />
              <h3 className="mt-4 text-lg font-bold">Academic Information</h3>
              <p className="mt-2 text-sm leading-7 text-slate-500">
                This area is prepared for future academic updates
                and student-related information.
              </p>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <LogOut size={24} className="text-violet-600" />
              <h3 className="mt-4 text-lg font-bold">Account Management</h3>
              <p className="mt-2 text-sm leading-7 text-slate-500">
                Secure sign in and sign out will be available
                after JWT authentication is implemented.
              </p>
              <Link href="/" className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-blue-600">
                Return Home <ArrowRight size={17} />
              </Link>
            </div>
          </section>

          <p className="text-center text-xs text-slate-400">
            StudentMS Student Portal · UI Preview
          </p>
        </main>
      </div>
    </div>
  );
}
