"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useForgotPassword, useResetPassword } from "../hooks";
import { forgotPasswordSchema, resetPasswordSchema, type ForgotPasswordFormValues, type ResetPasswordFormValues } from "../schemas";

export function ForgotPasswordForm() {
  const forgot = useForgotPassword();
  const form = useForm<ForgotPasswordFormValues>({ resolver: zodResolver(forgotPasswordSchema), defaultValues: { email: "" } });

  if (forgot.isSuccess) {
    return (
      <FormSuccess title="Check your inbox">
        If an account exists for that address, a reset link is on its way. It expires in 30 minutes.
      </FormSuccess>
    );
  }

  return (
    <form onSubmit={form.handleSubmit((v) => forgot.mutate(v.email))} className="space-y-4" noValidate>
      <FormField id="email" label="Email" error={form.formState.errors.email?.message} required>
        <Input {...form.register("email")} {...fieldAria("email", form.formState.errors.email?.message)} type="email" autoComplete="email" autoFocus />
      </FormField>
      <FormError error={forgot.error} />
      <Button type="submit" className="w-full" disabled={forgot.isPending}>
        {forgot.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
        Send reset link
      </Button>
    </form>
  );
}

export function ResetPasswordForm({ token }: { token: string }) {
  const reset = useResetPassword();
  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  if (!token) {
    return <FormError error={new Error("This reset link is incomplete. Request a new one.")} title="Invalid link" />;
  }

  if (reset.isSuccess) {
    return (
      <div className="space-y-4">
        <FormSuccess title="Password updated">You can now sign in with your new password.</FormSuccess>
        <Link href="/sign-in" className={buttonVariants({ className: "w-full" })}>
          Sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit((v) => reset.mutate({ token, newPassword: v.newPassword }))} className="space-y-4" noValidate>
      <FormField id="newPassword" label="New password" error={form.formState.errors.newPassword?.message} hint="At least 12 characters." required>
        <Input {...form.register("newPassword")} {...fieldAria("newPassword", form.formState.errors.newPassword?.message)} type="password" autoComplete="new-password" autoFocus />
      </FormField>
      <FormField id="confirmPassword" label="Confirm new password" error={form.formState.errors.confirmPassword?.message} required>
        <Input {...form.register("confirmPassword")} {...fieldAria("confirmPassword", form.formState.errors.confirmPassword?.message)} type="password" autoComplete="new-password" />
      </FormField>
      <FormError error={reset.error} title="Could not reset password" />
      <Button type="submit" className="w-full" disabled={reset.isPending}>
        {reset.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
        Set new password
      </Button>
    </form>
  );
}
