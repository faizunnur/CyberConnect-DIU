import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard } from "@/components/layout/auth-card";
import { SignInForm } from "@/features/auth/components/sign-in-form";

export const metadata: Metadata = { title: "Sign in" };

export default function SignInPage() {
  return (
    <AuthCard
      title="Sign in"
      description="Use the email and password you registered with."
      footer={
        <>
          New here?{" "}
          <Link href="/register" className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
            Create an account
          </Link>
        </>
      }
    >
      <SignInForm />
    </AuthCard>
  );
}
