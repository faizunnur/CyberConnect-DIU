import type { Metadata } from "next";
import { AuthCard } from "@/components/layout/auth-card";
import { VerifyEmail } from "@/features/auth/components/verify-email";

export const metadata: Metadata = { title: "Verify your email" };

export default async function VerifyEmailPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token = "" } = await searchParams;
  return (
    <AuthCard title="Email verification">
      <VerifyEmail token={token} />
    </AuthCard>
  );
}
