import RoleGuard from "@/components/auth/RoleGuard";

export default function CoursesLayout({ children }: LayoutProps<"/courses">) {
  return <RoleGuard role="ADMIN">{children}</RoleGuard>;
}
