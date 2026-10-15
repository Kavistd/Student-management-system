import type { ReactNode } from "react";

import Navbar from "@/components/layout/Navbar";
import Sidebar from "@/components/layout/Sidebar";
import type { PortalConfig } from "@/components/layout/portals";

export interface PageHeading {
  eyebrow: string;
  title: string;
  description: string;
  actions?: ReactNode;
  children: ReactNode;
}

/** Page frame shared by the admin and student areas: sidebar, top bar, and heading. */
export default function PortalShell({
  portal,
  eyebrow,
  title,
  description,
  actions,
  children,
}: PageHeading & { portal: PortalConfig }) {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar portal={portal} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Navbar portal={portal} />
        <main className="mx-auto w-full max-w-7xl flex-1 space-y-6 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-blue-600">
                {eyebrow}
              </p>
              <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
                {title}
              </h1>
              <p className="mt-1.5 text-sm text-slate-500">{description}</p>
            </div>
            {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
          </div>
          {children}
        </main>
      </div>
    </div>
  );
}
