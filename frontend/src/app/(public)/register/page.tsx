import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard } from "@/components/layout/auth-card";
import { RegisterForm } from "@/features/auth/components/register-form";

export const metadata: Metadata = { title: "Create an account" };

export default function RegisterPage() {
  return (
    <AuthCard
      title="Create your account"
      description="Registration is open to DIU cybersecurity students, alumni and CSC members. Access is granted after verification."
      wide
      footer={
        <>
          Already registered?{" "}
          <Link href="/sign-in" className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
            Sign in
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AuthCard>
  );
}
