import PortalShell, { type PageHeading } from "@/components/layout/PortalShell";
import { studentPortal } from "@/components/layout/portals";

export default function StudentShell(props: PageHeading) {
  return <PortalShell portal={studentPortal} {...props} />;
}
