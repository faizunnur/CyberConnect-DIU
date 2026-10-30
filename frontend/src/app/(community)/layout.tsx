import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { RealtimeProvider } from "@/components/realtime-provider";
import { RequireAuth } from "@/features/auth/components/guards";
import { VerifiedRail } from "@/features/auth/components/verified-rail";
import { TrendingRail } from "@/features/community/components/trending-rail";

export default function CommunityLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth mode="verified">
      <RealtimeProvider>
        <AppShell rail={<><VerifiedRail /><TrendingRail /></>}>{children}</AppShell>
      </RealtimeProvider>
    </RequireAuth>
  );
}
