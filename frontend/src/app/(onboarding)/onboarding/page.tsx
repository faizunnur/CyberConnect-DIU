import type { Metadata } from "next";
import { OnboardingHub } from "@/features/verification/components/onboarding-hub";

export const metadata: Metadata = { title: "Verification" };

export default function OnboardingPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <OnboardingHub />
    </div>
  );
}
