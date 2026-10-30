import type { Metadata } from "next";
import { AuthCard } from "@/components/layout/auth-card";
import { ResetPasswordForm } from "@/features/auth/components/password-forms";

export const metadata: Metadata = { title: "Choose a new password" };

export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token = "" } = await searchParams;
  return (
    <AuthCard title="Choose a new password" description="Your new password will sign you out of all other devices.">
      <ResetPasswordForm token={token} />
    </AuthCard>
  );
}
