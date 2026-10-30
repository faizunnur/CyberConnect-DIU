"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, ArrowRight, Loader2, MailCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { FormError } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useRegister } from "../hooks";
import { registerSchema, type RegisterFormValues } from "../schemas";
import { AFFILIATION_LABELS, type AffiliationType } from "../types";

const steps = ["Account", "Affiliation", "Confirm"] as const;

const affiliationOptions: { value: AffiliationType; description: string; evidence: string }[] = [
  {
    value: "STUDENT",
    description: "Current student of the Cybersecurity major at DIU.",
    evidence: "Student ID card",
  },
  {
    value: "ALUMNI",
    description: "Graduate of the DIU Cybersecurity program.",
    evidence: "Alumni card, degree certificate or transcript",
  },
  {
    value: "CSC_MEMBER",
    description: "Eligible member of the Cyber Security Center, DIU.",
    evidence: "CSC membership card or CSC-issued letter",
  },
];

export function RegisterForm() {
  const register = useRegister();
  const [step, setStep] = useState(0);
  const [done, setDone] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: "onTouched",
    defaultValues: {
      fullName: "",
      email: "",
      password: "",
      confirmPassword: "",
      affiliationType: undefined,
      studentId: "",
      batch: "",
      department: "",
      graduationYear: "",
      cscMemberId: "",
      privacyAccepted: undefined,
    },
  });
  const { errors } = form.formState;
  const affiliationType = useWatch({ control: form.control, name: "affiliationType" });

  const next = async () => {
    const fields: (keyof RegisterFormValues)[] =
      step === 0
        ? ["fullName", "email", "password", "confirmPassword"]
        : ["affiliationType", "studentId", "batch", "department", "graduationYear", "cscMemberId"];
    const valid = await form.trigger(fields);
    if (valid) setStep((s) => s + 1);
  };

  const onSubmit = form.handleSubmit(async (values) => {
    await register.mutateAsync({
      fullName: values.fullName,
      email: values.email,
      password: values.password,
      affiliationType: values.affiliationType,
      studentId: values.studentId || undefined,
      batch: values.batch || undefined,
      department: values.department || undefined,
      graduationYear: values.graduationYear ? Number(values.graduationYear) : undefined,
      cscMemberId: values.cscMemberId || undefined,
      privacyAccepted: true,
    });
    setDone(values.email);
  });

  if (done) {
    return (
      <div className="space-y-4 text-center">
        <MailCheck className="mx-auto size-10 text-success" aria-hidden="true" />
        <h2 className="text-xl font-semibold">Check your inbox</h2>
        <p className="text-sm text-muted-foreground">
          If <span className="font-medium text-foreground">{done}</span> is new to us, we have sent a verification link.
          Verify your email, then sign in to upload your eligibility evidence.
        </p>
        <Link href="/sign-in" className={buttonVariants()}>
          Go to sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-6" noValidate>
      <ol className="flex items-center gap-2 text-xs" aria-label="Registration progress">
        {steps.map((label, index) => (
          <li key={label} className="flex items-center gap-2">
            <span
              className={cn(
                "flex size-6 items-center justify-center rounded-full border text-[11px] font-semibold",
                index === step && "border-primary bg-primary text-primary-foreground",
                index < step && "border-success bg-success text-success-foreground",
              )}
              aria-current={index === step ? "step" : undefined}
            >
              {index + 1}
            </span>
            <span className={cn("hidden sm:inline", index === step ? "font-medium" : "text-muted-foreground")}>{label}</span>
            {index < steps.length - 1 ? <span className="mx-1 h-px w-6 bg-border" aria-hidden="true" /> : null}
          </li>
        ))}
      </ol>

      {step === 0 ? (
        <div className="space-y-4">
          <FormField id="fullName" label="Full name" error={errors.fullName?.message} required>
            <Input {...form.register("fullName")} {...fieldAria("fullName", errors.fullName?.message)} autoComplete="name" autoFocus />
          </FormField>
          <FormField id="email" label="Email" error={errors.email?.message} hint="We will send a verification link here." required>
            <Input {...form.register("email")} {...fieldAria("email", errors.email?.message)} type="email" autoComplete="email" />
          </FormField>
          <FormField id="password" label="Password" error={errors.password?.message} hint="At least 12 characters. A passphrase works well." required>
            <Input {...form.register("password")} {...fieldAria("password", errors.password?.message)} type="password" autoComplete="new-password" />
          </FormField>
          <FormField id="confirmPassword" label="Confirm password" error={errors.confirmPassword?.message} required>
            <Input {...form.register("confirmPassword")} {...fieldAria("confirmPassword", errors.confirmPassword?.message)} type="password" autoComplete="new-password" />
          </FormField>
        </div>
      ) : null}

      {step === 1 ? (
        <div className="space-y-5">
          <fieldset className="space-y-3">
            <legend className="text-sm font-medium">
              Primary affiliation <span aria-hidden="true" className="text-destructive">*</span>
            </legend>
            <p className="text-xs text-muted-foreground">You can add further affiliations from settings after verification.</p>
            <div className="grid gap-3">
              {affiliationOptions.map((option) => {
                const selected = affiliationType === option.value;
                return (
                  <label
                    key={option.value}
                    className={cn(
                      "flex cursor-pointer gap-3 rounded-lg border p-4 transition-colors hover:bg-accent",
                      selected && "border-primary bg-accent ring-1 ring-primary",
                    )}
                  >
                    <input type="radio" value={option.value} {...form.register("affiliationType")} className="mt-1 size-4 accent-primary" />
                    <span>
                      <span className="block font-medium">{AFFILIATION_LABELS[option.value]}</span>
                      <span className="block text-sm text-muted-foreground">{option.description}</span>
                      <span className="mt-1 block text-xs text-muted-foreground">Evidence: {option.evidence}</span>
                    </span>
                  </label>
                );
              })}
            </div>
            {errors.affiliationType ? <p role="alert" className="text-sm text-destructive">{errors.affiliationType.message}</p> : null}
          </fieldset>

          {affiliationType === "STUDENT" || affiliationType === "ALUMNI" ? (
            <FormField id="studentId" label="Student ID" error={errors.studentId?.message} required>
              <Input {...form.register("studentId")} {...fieldAria("studentId", errors.studentId?.message)} />
            </FormField>
          ) : null}
          {affiliationType === "STUDENT" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="batch" label="Batch" error={errors.batch?.message} required>
                <Input {...form.register("batch")} {...fieldAria("batch", errors.batch?.message)} />
              </FormField>
              <FormField id="department" label="Department" error={errors.department?.message} required>
                <Input {...form.register("department")} {...fieldAria("department", errors.department?.message)} />
              </FormField>
            </div>
          ) : null}
          {affiliationType === "ALUMNI" ? (
            <FormField id="graduationYear" label="Graduation year" error={errors.graduationYear?.message} required>
              <Input {...form.register("graduationYear")} {...fieldAria("graduationYear", errors.graduationYear?.message)} inputMode="numeric" placeholder="e.g. 2023" maxLength={4} />
            </FormField>
          ) : null}
          {affiliationType === "CSC_MEMBER" ? (
            <FormField id="cscMemberId" label="CSC member ID" error={errors.cscMemberId?.message} required>
              <Input {...form.register("cscMemberId")} {...fieldAria("cscMemberId", errors.cscMemberId?.message)} />
            </FormField>
          ) : null}
        </div>
      ) : null}

      {step === 2 ? (
        <div className="space-y-4">
          <div className="rounded-lg border bg-muted/40 p-4 text-sm">
            <p className="font-medium">What happens next</p>
            <ol className="mt-2 list-decimal space-y-1 pl-5 text-muted-foreground">
              <li>We email you a verification link.</li>
              <li>After verifying, you upload your eligibility evidence through a private, encrypted upload.</li>
              <li>A moderator reviews it. Identity documents are purged after the decision.</li>
            </ol>
          </div>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" {...form.register("privacyAccepted")} className="mt-1 size-4 accent-primary" />
            <span>
              I have read the{" "}
              <Link href="/privacy" className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary" target="_blank">
                privacy notice
              </Link>{" "}
              and agree that my eligibility evidence will be reviewed by moderators and retained only as described.
            </span>
          </label>
          {errors.privacyAccepted ? <p role="alert" className="text-sm text-destructive">{errors.privacyAccepted.message}</p> : null}
          <FormError error={register.error} title="Registration failed" />
        </div>
      ) : null}

      <div className="flex items-center justify-between gap-3">
        {step > 0 ? (
          <Button type="button" variant="ghost" onClick={() => setStep((s) => s - 1)}>
            <ArrowLeft aria-hidden="true" /> Back
          </Button>
        ) : (
          <span />
        )}
        {step < steps.length - 1 ? (
          <Button type="button" onClick={next}>
            Continue <ArrowRight aria-hidden="true" />
          </Button>
        ) : (
          <Button type="submit" disabled={register.isPending}>
            {register.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
            Create account
          </Button>
        )}
      </div>
    </form>
  );
}
