"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormError } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useLogin, useLoginMfa } from "../hooks";
import { homeFor } from "../routing";
import { mfaCodeSchema, signInSchema, type MfaCodeFormValues, type SignInFormValues } from "../schemas";

export function SignInForm() {
  const router = useRouter();
  const login = useLogin();
  const loginMfa = useLoginMfa();
  const [mfaStep, setMfaStep] = useState(false);

  const form = useForm<SignInFormValues>({ resolver: zodResolver(signInSchema), defaultValues: { email: "", password: "" } });
  const mfaForm = useForm<MfaCodeFormValues>({ resolver: zodResolver(mfaCodeSchema), defaultValues: { code: "" } });

  const onSubmit = form.handleSubmit(async (values) => {
    const result = await login.mutateAsync(values);
    if (result.mfaRequired) {
      setMfaStep(true);
      return;
    }
    router.replace(homeFor(result.user));
  });

  const onSubmitMfa = mfaForm.handleSubmit(async ({ code }) => {
    const result = await loginMfa.mutateAsync(code);
    router.replace(homeFor(result.user));
  });

  if (mfaStep) {
    return (
      <form onSubmit={onSubmitMfa} className="space-y-4" noValidate>
        <p className="text-sm text-muted-foreground">
          Enter the 6-digit code from your authenticator app, or one of your recovery codes.
        </p>
        <FormField id="code" label="Verification code" error={mfaForm.formState.errors.code?.message} required>
          <Input
            {...mfaForm.register("code")}
            {...fieldAria("code", mfaForm.formState.errors.code?.message)}
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
          />
        </FormField>
        <FormError error={loginMfa.error} title="Could not verify the code" />
        <Button type="submit" className="w-full" disabled={loginMfa.isPending}>
          {loginMfa.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
          Continue
        </Button>
      </form>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <FormField id="email" label="Email" error={form.formState.errors.email?.message} required>
        <Input
          {...form.register("email")}
          {...fieldAria("email", form.formState.errors.email?.message)}
          type="email"
          autoComplete="email"
          autoFocus
        />
      </FormField>
      <FormField id="password" label="Password" error={form.formState.errors.password?.message} required>
        <Input
          {...form.register("password")}
          {...fieldAria("password", form.formState.errors.password?.message)}
          type="password"
          autoComplete="current-password"
        />
      </FormField>
      <FormError error={login.error} title="Sign-in failed" />
      <Button type="submit" className="w-full" disabled={login.isPending}>
        {login.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
        Sign in
      </Button>
      <p className="text-center text-sm">
        <Link href="/forgot-password" className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
          Forgot your password?
        </Link>
      </p>
    </form>
  );
}
