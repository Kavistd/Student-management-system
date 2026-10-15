import PortalShell, { type PageHeading } from "@/components/layout/PortalShell";
import { adminPortal } from "@/components/layout/portals";

export default function AdminShell(props: PageHeading) {
  return <PortalShell portal={adminPortal} {...props} />;
}
