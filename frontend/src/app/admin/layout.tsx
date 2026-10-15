import RoleGuard from "@/components/auth/RoleGuard";

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return <RoleGuard role="ADMIN">{children}</RoleGuard>;
}
