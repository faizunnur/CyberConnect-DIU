"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Copy, KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { authApi } from "../api";
import { ME_QUERY_KEY, useChangePassword, useMe } from "../hooks";
import { isPrivileged } from "../routing";
import { changePasswordSchema, type ChangePasswordFormValues } from "../schemas";
import type { MfaEnrolment } from "../types";

export function SecuritySettings() {
  const { data: me } = useMe();
  if (!me) return null;
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Security</h1>
        <p className="mt-1 text-muted-foreground">Multi-factor authentication and password.</p>
      </div>
      <MfaCard enabled={me.mfaEnabled} privileged={isPrivileged(me)} />
      <PasswordCard />
    </div>
  );
}

function MfaCard({ enabled, privileged }: { enabled: boolean; privileged: boolean }) {
  const queryClient = useQueryClient();
  const [enrolment, setEnrolment] = useState<MfaEnrolment | null>(null);
  const [code, setCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [disableCode, setDisableCode] = useState("");

  const begin = useMutation({ mutationFn: authApi.mfaEnrol, onSuccess: setEnrolment });
  const confirm = useMutation({
    mutationFn: (c: string) => authApi.mfaConfirm(c),
    onSuccess: (result) => {
      setRecoveryCodes(result.recoveryCodes);
      setEnrolment(null);
      setCode("");
      queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
    },
  });
  const regenerate = useMutation({
    mutationFn: (c: string) => authApi.mfaRegenerateRecovery(c),
    onSuccess: (result) => {
      setRecoveryCodes(result.recoveryCodes);
      setCode("");
    },
  });
  const disable = useMutation({
    mutationFn: (c: string) => authApi.mfaDisable(c),
    onSuccess: () => {
      setDisableCode("");
      queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ShieldCheck className="size-5 text-primary" aria-hidden="true" /> Multi-factor authentication
        </CardTitle>
        <CardDescription>
          {enabled
            ? "Enabled. You will be asked for a code from your authenticator app at sign-in and before sensitive actions."
            : privileged
              ? "Required for your role. Set it up to use moderation and administration tools."
              : "Recommended. Adds a second step with an authenticator app such as Aegis, 1Password or Google Authenticator."}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {recoveryCodes ? (
          <Alert>
            <KeyRound aria-hidden="true" />
            <AlertTitle>Save your recovery codes now</AlertTitle>
            <AlertDescription>
              <p>Each code works once if you lose your authenticator. They will not be shown again.</p>
              <ul className="mt-2 grid grid-cols-2 gap-1 font-mono text-sm">
                {recoveryCodes.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
              <Button
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={() => navigator.clipboard?.writeText(recoveryCodes.join("\n"))}
              >
                <Copy aria-hidden="true" /> Copy codes
              </Button>
            </AlertDescription>
          </Alert>
        ) : null}

        {!enabled && !enrolment ? (
          <>
            <FormError error={begin.error} />
            <Button onClick={() => begin.mutate()} disabled={begin.isPending}>
              {begin.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
              Set up authenticator app
            </Button>
          </>
        ) : null}

        {enrolment ? (
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              confirm.mutate(code);
            }}
          >
            <p className="text-sm">
              Add this account to your authenticator app, then enter the 6-digit code it shows.
            </p>
            <div className="rounded-lg border bg-muted/40 p-3 text-sm">
              <p className="text-xs text-muted-foreground">Secret key (type it manually or use the link below)</p>
              <p className="mt-1 break-all font-mono">{enrolment.secret}</p>
              <a href={enrolment.otpauthUri} className="mt-2 inline-block text-primary underline-offset-4 hover:underline">
                Open in authenticator app
              </a>
            </div>
            <FormField id="mfa-code" label="6-digit code" required>
              <Input {...fieldAria("mfa-code")} value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} />
            </FormField>
            <FormError error={confirm.error} title="Could not confirm" />
            <div className="flex gap-2">
              <Button type="submit" disabled={confirm.isPending || code.length !== 6}>
                {confirm.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
                Confirm and enable
              </Button>
              <Button type="button" variant="ghost" onClick={() => setEnrolment(null)}>Cancel</Button>
            </div>
          </form>
        ) : null}

        {enabled ? (
          <div className="grid gap-6 md:grid-cols-2">
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                regenerate.mutate(code);
              }}
            >
              <p className="text-sm font-medium">Generate new recovery codes</p>
              <FormField id="regen-code" label="Current authenticator code" required>
                <Input {...fieldAria("regen-code")} value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric" maxLength={6} />
              </FormField>
              <FormError error={regenerate.error} />
              <Button type="submit" variant="outline" disabled={regenerate.isPending || code.length !== 6}>
                Regenerate codes
              </Button>
            </form>
            {!privileged ? (
              <form
                className="space-y-3"
                onSubmit={(e) => {
                  e.preventDefault();
                  disable.mutate(disableCode);
                }}
              >
                <p className="text-sm font-medium">Turn off MFA</p>
                <FormField id="disable-code" label="Current authenticator code" required>
                  <Input {...fieldAria("disable-code")} value={disableCode} onChange={(e) => setDisableCode(e.target.value)} inputMode="numeric" maxLength={6} />
                </FormField>
                <FormError error={disable.error} />
                <Button type="submit" variant="destructive" disabled={disable.isPending || disableCode.length !== 6}>
                  Disable MFA
                </Button>
              </form>
            ) : (
              <p className="text-sm text-muted-foreground">MFA cannot be disabled while you hold a moderator or admin role.</p>
            )}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function PasswordCard() {
  const router = useRouter();
  const change = useChangePassword();
  const form = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });
  const { errors } = form.formState;

  if (change.isSuccess) {
    return (
      <Card>
        <CardContent className="space-y-3 pt-6">
          <FormSuccess title="Password changed">All sessions were signed out. Sign in again with your new password.</FormSuccess>
          <Button onClick={() => router.replace("/sign-in")}>Go to sign in</Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change password</CardTitle>
        <CardDescription>Changing your password signs you out everywhere.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit((v) => change.mutate({ currentPassword: v.currentPassword, newPassword: v.newPassword }))} className="max-w-md space-y-4" noValidate>
          <FormField id="currentPassword" label="Current password" error={errors.currentPassword?.message} required>
            <Input {...form.register("currentPassword")} {...fieldAria("currentPassword", errors.currentPassword?.message)} type="password" autoComplete="current-password" />
          </FormField>
          <FormField id="newPassword" label="New password" error={errors.newPassword?.message} hint="At least 12 characters." required>
            <Input {...form.register("newPassword")} {...fieldAria("newPassword", errors.newPassword?.message)} type="password" autoComplete="new-password" />
          </FormField>
          <FormField id="confirmPassword" label="Confirm new password" error={errors.confirmPassword?.message} required>
            <Input {...form.register("confirmPassword")} {...fieldAria("confirmPassword", errors.confirmPassword?.message)} type="password" autoComplete="new-password" />
          </FormField>
          <FormError error={change.error} title="Could not change password" />
          <Button type="submit" disabled={change.isPending}>
            {change.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
            Change password
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
