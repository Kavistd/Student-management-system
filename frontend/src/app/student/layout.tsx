import RoleGuard from "@/components/auth/RoleGuard";

export default function StudentLayout({ children }: LayoutProps<"/student">) {
  return <RoleGuard role="STUDENT">{children}</RoleGuard>;
}
