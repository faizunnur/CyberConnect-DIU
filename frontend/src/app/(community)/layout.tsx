import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { RequireAuth } from "@/features/auth/components/guards";
import { VerifiedRail } from "@/features/auth/components/verified-rail";

export default function CommunityLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth mode="verified">
      <AppShell rail={<VerifiedRail />}>{children}</AppShell>
    </RequireAuth>
  );
}
