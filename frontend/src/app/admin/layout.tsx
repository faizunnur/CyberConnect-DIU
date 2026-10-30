import type { ReactNode } from "react";
import { AdminShell } from "@/components/layout/admin-shell";
import { RequireAuth } from "@/features/auth/components/guards";

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth mode="privileged">
      <AdminShell>{children}</AdminShell>
    </RequireAuth>
  );
}
