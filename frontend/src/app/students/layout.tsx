import RoleGuard from "@/components/auth/RoleGuard";

export default function StudentsLayout({ children }: LayoutProps<"/students">) {
  return <RoleGuard role="ADMIN">{children}</RoleGuard>;
}
