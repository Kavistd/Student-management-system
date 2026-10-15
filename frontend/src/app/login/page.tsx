
"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import {
  ArrowRight,
  BookOpen,
  CheckCircle2,
  Eye,
  EyeOff,
  GraduationCap,
  LockKeyhole,
  Mail,
  ShieldCheck,
  Users,
} from "lucide-react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-2">
      <section className="relative hidden min-h-screen flex-col justify-between overflow-hidden bg-gradient-to-br from-slate-950 via-blue-950 to-blue-700 p-12 text-white lg:flex">
        <div className="absolute -right-28 top-24 h-96 w-96 rounded-full bg-blue-400/20 blur-3xl" />
        <div className="absolute -left-20 bottom-10 h-80 w-80 rounded-full bg-indigo-500/20 blur-3xl" />

        <div className="relative flex items-center gap-3">
          <div className="rounded-2xl bg-white/15 p-3">
            <GraduationCap size={30} />
          </div>
          <div>
            <h1 className="text-xl font-bold">StudentMS</h1>
            <p className="text-xs text-blue-200">Academic Management Platform</p>
          </div>
        </div>

        <div className="relative max-w-xl">
          <span className="rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm text-blue-100">
            A smarter way to manage education
          </span>

          <h2 className="mt-8 text-5xl font-bold leading-tight">
            Everything you need to manage your academic world.
          </h2>

          <p className="mt-6 text-lg leading-8 text-blue-100">
            Organize students, manage courses, and keep your academic
            operations connected in one beautiful workspace.
          </p>

          <div className="mt-10 grid grid-cols-3 gap-4">
            {[
              { icon: Users, label: "Students" },
              { icon: BookOpen, label: "Courses" },
              { icon: ShieldCheck, label: "Secure Access" },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.label}
                  className="rounded-2xl border border-white/15 bg-white/10 p-5 backdrop-blur-sm"
                >
                  <Icon className="mb-4 text-blue-200" size={26} />
                  <p className="text-sm font-medium">{item.label}</p>
                </div>
              );
            })}
          </div>
        </div>

        <p className="relative text-sm text-blue-200">
          © 2026 StudentMS. Academic Management Platform.
        </p>
      </section>

      <section className="flex min-h-screen items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-md">
          <div className="mb-10 flex items-center gap-3 lg:hidden">
            <div className="rounded-xl bg-blue-600 p-3 text-white">
              <GraduationCap size={25} />
            </div>
            <span className="text-xl font-bold text-slate-900">StudentMS</span>
          </div>

          <div className="mb-9">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">
              <CheckCircle2 size={16} />
              Welcome to StudentMS
            </div>

            <h2 className="text-4xl font-bold tracking-tight text-slate-900">
              Welcome back
            </h2>
            <p className="mt-3 text-slate-500">
              Sign in to continue to your management dashboard.
            </p>
          </div>

          <form
            onSubmit={handleSubmit}
            className="rounded-3xl border border-slate-200 bg-white p-7 shadow-xl shadow-slate-200/50 sm:p-9"
          >
            <div className="mb-6">
              <label htmlFor="email" className="mb-2 block text-sm font-semibold text-slate-700">
                Email address
              </label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" size={19} />
                <input
                  id="email"
                  type="email"
                  required
                  autoComplete="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3.5 pl-12 pr-4 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                />
              </div>
            </div>

            <div className="mb-5">
              <label htmlFor="password" className="mb-2 block text-sm font-semibold text-slate-700">
                Password
              </label>
              <div className="relative">
                <LockKeyhole className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" size={19} />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  required
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3.5 pl-12 pr-12 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 hover:text-blue-600"
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>
            </div>

            <div className="mb-7 flex items-center justify-between gap-3 text-sm">
              <label className="flex items-center gap-2 text-slate-600">
                <input type="checkbox" className="accent-blue-600" />
                Remember me
              </label>
              <span className="text-slate-400">Forgot password?</span>
            </div>

            <button
              type="submit"
              disabled
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3.5 font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-60"
            >
              Sign In <ArrowRight size={19} />
            </button>

            <p className="mt-3 text-center text-xs text-slate-400">
              Authentication will be available on Day 4.
            </p>

            <div className="my-7 flex items-center gap-3">
              <div className="h-px flex-1 bg-slate-200" />
              <span className="text-xs text-slate-400">New to StudentMS?</span>
              <div className="h-px flex-1 bg-slate-200" />
            </div>

            <Link
              href="/signup"
              className="flex w-full items-center justify-center rounded-xl border border-blue-200 bg-blue-50 px-5 py-3.5 font-semibold text-blue-700 transition hover:bg-blue-100"
            >
              Create an Account
            </Link>
          </form>

          <div className="mt-8 text-center">
            <Link href="/dashboard" className="text-sm font-medium text-slate-500 hover:text-blue-600">
              ← Back to Dashboard
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
