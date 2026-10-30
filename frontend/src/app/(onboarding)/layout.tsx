import type { ReactNode } from "react";
import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";
import { RequireAuth } from "@/features/auth/components/guards";

export default function OnboardingLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <SiteHeader showNavigation={false} />
      <main className="flex-1">
        <RequireAuth mode="onboarding">{children}</RequireAuth>
      </main>
      <SiteFooter />
    </>
  );
}
