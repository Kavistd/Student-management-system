import {
  BookOpen,
  LayoutDashboard,
  UserCog,
  UserRound,
  Users,
} from "lucide-react";

export interface PortalNavItem {
  name: string;
  href: string;
  icon: typeof BookOpen;
}

/** Branding and navigation for one area of the app (admin or student). */
export interface PortalConfig {
  label: string;
  homeHref: string;
  navLabel: string;
  navigation: readonly PortalNavItem[];
}

export const adminPortal: PortalConfig = {
  label: "Administration",
  homeHref: "/admin/dashboard",
  navLabel: "Management",
  navigation: [
    { name: "Overview", href: "/admin/dashboard", icon: LayoutDashboard },
    { name: "Students", href: "/students", icon: Users },
    { name: "Courses", href: "/courses", icon: BookOpen },
    { name: "Users", href: "/admin/users", icon: UserCog },
  ],
};

export const studentPortal: PortalConfig = {
  label: "Student Portal",
  homeHref: "/student/dashboard",
  navLabel: "My workspace",
  navigation: [
    { name: "Overview", href: "/student/dashboard", icon: LayoutDashboard },
    { name: "My Profile", href: "/student/profile", icon: UserRound },
    { name: "My Courses", href: "/student/courses", icon: BookOpen },
  ],
};

export function isActivePath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}
