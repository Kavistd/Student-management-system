
import Link from "next/link";
import {
  ArrowRight,
  BookOpen,
  CheckCircle2,
  GraduationCap,
  LayoutDashboard,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";

const features = [
  {
    icon: Users,
    title: "Student Management",
    description: "Organize student profiles, records, and academic information in one place.",
  },
  {
    icon: BookOpen,
    title: "Course Management",
    description: "Manage academic courses through a simple, organized interface.",
  },
  {
    icon: LayoutDashboard,
    title: "Dedicated Dashboards",
    description: "Separate workspaces designed for administrators and students.",
  },
  {
    icon: ShieldCheck,
    title: "Role-Based Experience",
    description: "An interface prepared for secure role-based access.",
  },
];

export default function Landing() {
  return (
    <div className="min-h-screen bg-white text-slate-900">
      <header className="sticky top-0 z-50 border-b border-slate-100 bg-white/90 backdrop-blur-xl">
        <nav className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-5 py-4 lg:px-8">
          <Link href="/" className="flex items-center gap-3">
            <div className="rounded-xl bg-blue-600 p-2.5 text-white">
              <GraduationCap size={26} />
            </div>
            <span className="text-xl font-extrabold tracking-tight">
              Student<span className="text-blue-600">MS</span>
            </span>
          </Link>

          <div className="hidden items-center gap-8 text-sm font-medium text-slate-600 md:flex">
            <a href="#features" className="hover:text-blue-600">Features</a>
            <a href="#about" className="hover:text-blue-600">About</a>
            <a href="#workflow" className="hover:text-blue-600">How It Works</a>
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/login"
              className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100"
            >
              Sign In
            </Link>
            <Link
              href="/signup"
              className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700"
            >
              Get Started
            </Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="relative overflow-hidden bg-gradient-to-br from-slate-50 via-white to-blue-50">
          <div className="absolute -right-20 top-10 h-80 w-80 rounded-full bg-blue-200/40 blur-3xl" />

          <div className="relative mx-auto grid max-w-7xl items-center gap-14 px-5 py-20 lg:grid-cols-2 lg:px-8 lg:py-28">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-blue-100 bg-blue-50 px-4 py-2 text-xs font-semibold text-blue-700">
                <Sparkles size={15} />
                A smarter academic workspace
              </div>

              <h1 className="mt-8 text-5xl font-extrabold leading-[1.12] tracking-tight sm:text-6xl">
                Simplify your
                <span className="block text-blue-600">academic world.</span>
              </h1>

              <p className="mt-7 max-w-xl text-lg leading-8 text-slate-600">
                Bring students, courses, and academic administration
                together in one modern management platform.
                Beautifully organized. Effortlessly accessible.
              </p>

              <div className="mt-9 flex flex-wrap gap-4">
                <Link
                  href="/signup"
                  className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-7 py-4 font-semibold text-white shadow-xl shadow-blue-200 transition hover:bg-blue-700"
                >
                  Create an Account <ArrowRight size={18} />
                </Link>
                <a
                  href="#features"
                  className="rounded-xl border border-slate-200 bg-white px-7 py-4 font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Explore Features
                </a>
              </div>

              <div className="mt-10 flex flex-wrap gap-5 text-sm text-slate-600">
                {["Simple experience", "Organized records", "Modern interface"].map((item) => (
                  <span key={item} className="flex items-center gap-2">
                    <CheckCircle2 size={18} className="text-emerald-500" />
                    {item}
                  </span>
                ))}
              </div>
            </div>

            <div className="relative">
              <div className="absolute -inset-4 rounded-[2.5rem] bg-blue-200/40 blur-2xl" />

              <div className="relative rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl shadow-blue-100">
                <div className="mb-6 flex items-center justify-between">
                  <div className="flex gap-2">
                    <span className="h-3 w-3 rounded-full bg-red-400" />
                    <span className="h-3 w-3 rounded-full bg-amber-400" />
                    <span className="h-3 w-3 rounded-full bg-emerald-400" />
                  </div>
                  <span className="text-xs text-slate-400">Platform Preview</span>
                </div>

                <div className="rounded-2xl bg-gradient-to-r from-blue-700 to-indigo-600 p-7 text-white">
                  <p className="text-xs font-semibold text-blue-100">STUDENTMS WORKSPACE</p>
                  <h2 className="mt-3 text-2xl font-bold">Academic Overview</h2>
                  <p className="mt-2 text-sm text-blue-100">
                    Everything connected in one workspace.
                  </p>
                </div>

                <div className="mt-5 grid grid-cols-3 gap-3">
                  {[
                    { icon: Users, label: "Students" },
                    { icon: BookOpen, label: "Courses" },
                    { icon: ShieldCheck, label: "Accounts" },
                  ].map((item) => {
                    const Icon = item.icon;
                    return (
                      <div key={item.label} className="rounded-xl bg-slate-50 p-4">
                        <Icon size={22} className="text-blue-600" />
                        <p className="mt-4 text-sm font-semibold">{item.label}</p>
                        <p className="mt-1 text-xs text-slate-400">Manage easily</p>
                      </div>
                    );
                  })}
                </div>

                <div className="mt-5 space-y-3 rounded-xl border border-slate-100 p-5">
                  <p className="mb-4 text-sm font-semibold">Platform Features</p>
                  {[
                    ["Student Records", "w-4/5"],
                    ["Course Directory", "w-3/5"],
                    ["Account Management", "w-5/6"],
                  ].map(([label, width]) => (
                    <div key={label}>
                      <p className="mb-2 text-xs text-slate-500">{label}</p>
                      <div className="h-2 rounded-full bg-slate-100">
                        <div className={`h-2 rounded-full bg-blue-500 ${width}`} />
                      </div>
                    </div>
                  ))}
                  <p className="pt-2 text-xs text-slate-400">
                    Illustrative interface preview
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="features" className="scroll-mt-24 py-24">
          <div className="mx-auto max-w-7xl px-5 lg:px-8">
            <div className="mx-auto max-w-2xl text-center">
              <p className="text-sm font-bold uppercase tracking-widest text-blue-600">
                Platform Features
              </p>
              <h2 className="mt-4 text-4xl font-bold tracking-tight">
                Everything you need, in one place
              </h2>
              <p className="mt-5 text-slate-500">
                Practical tools designed for better academic organization.
              </p>
            </div>

            <div className="mt-14 grid gap-6 md:grid-cols-2 lg:grid-cols-4">
              {features.map((feature) => {
                const Icon = feature.icon;
                return (
                  <div
                    key={feature.title}
                    className="rounded-2xl border border-slate-200 p-7 transition hover:-translate-y-1 hover:border-blue-200 hover:shadow-xl"
                  >
                    <div className="inline-flex rounded-xl bg-blue-50 p-4 text-blue-600">
                      <Icon size={25} />
                    </div>
                    <h3 className="mt-6 text-lg font-bold">{feature.title}</h3>
                    <p className="mt-3 text-sm leading-7 text-slate-500">
                      {feature.description}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        <section id="about" className="scroll-mt-24 bg-slate-50 py-24">
          <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 lg:grid-cols-2 lg:px-8">
            <div className="rounded-3xl bg-gradient-to-br from-blue-700 to-indigo-700 p-10 text-white">
              <GraduationCap size={54} />
              <h2 className="mt-8 text-3xl font-bold">
                Designed for modern academic management.
              </h2>
              <p className="mt-5 leading-8 text-blue-100">
                A connected workspace for students, courses, and
                institutional information.
              </p>
            </div>

            <div>
              <p className="text-sm font-bold uppercase tracking-widest text-blue-600">
                About StudentMS
              </p>
              <h2 className="mt-4 text-4xl font-bold">
                A better way to stay organized.
              </h2>
              <p className="mt-6 leading-8 text-slate-600">
                StudentMS brings academic administration into one
                convenient digital environment, with dedicated
                experiences for administrators and students.
              </p>
            </div>
          </div>
        </section>

        <section id="workflow" className="scroll-mt-24 py-24">
          <div className="mx-auto max-w-7xl px-5 lg:px-8">
            <div className="text-center">
              <p className="text-sm font-bold uppercase tracking-widest text-blue-600">
                How It Works
              </p>
              <h2 className="mt-4 text-4xl font-bold">
                Simple from the beginning
              </h2>
            </div>

            <div className="mt-14 grid gap-6 md:grid-cols-3">
              {[
                ["01", "Create Your Account", "Register using your personal information."],
                ["02", "Access Your Workspace", "Open your dedicated academic interface."],
                ["03", "Stay Organized", "View and manage relevant academic information."],
              ].map(([number, title, description]) => (
                <div key={number} className="rounded-2xl border border-slate-200 p-8">
                  <span className="text-4xl font-extrabold text-blue-200">{number}</span>
                  <h3 className="mt-5 text-xl font-bold">{title}</h3>
                  <p className="mt-3 text-sm leading-7 text-slate-500">{description}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="px-5 pb-24 lg:px-8">
          <div className="mx-auto max-w-7xl rounded-3xl bg-gradient-to-r from-blue-700 to-indigo-700 px-8 py-16 text-center text-white">
            <h2 className="text-3xl font-bold md:text-4xl">
              Ready to simplify your academic experience?
            </h2>
            <p className="mx-auto mt-5 max-w-xl text-blue-100">
              Get started with StudentMS and explore a more organized workspace.
            </p>
            <Link
              href="/signup"
              className="mt-8 inline-flex items-center gap-2 rounded-xl bg-white px-7 py-4 font-semibold text-blue-700 hover:bg-blue-50"
            >
              Get Started <ArrowRight size={18} />
            </Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-100 bg-slate-50 py-9">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-5 lg:px-8">
          <span className="font-bold">
            Student<span className="text-blue-600">MS</span>
          </span>
          <p className="text-sm text-slate-500">
            © 2026 StudentMS. All rights reserved.
          </p>
          <div className="flex gap-5 text-sm text-slate-500">
            <Link href="/login" className="hover:text-blue-600">Sign In</Link>
            <Link href="/signup" className="hover:text-blue-600">Sign Up</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
