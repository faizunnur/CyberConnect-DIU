"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { authApi } from "../api";
import { ME_QUERY_KEY, useMe } from "../hooks";
import { stepUpIsFresh } from "../routing";

/** Reports whether sensitive actions are currently allowed (fresh MFA within the backend's 10-minute window). */
export function useStepUpFresh(): boolean {
  const { data: me } = useMe();
  return stepUpIsFresh(me);
}

/** Inline step-up prompt shown on privileged screens; sensitive buttons stay disabled until it succeeds. */
export function StepUpBanner() {
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const stepUp = useMutation({
    mutationFn: (c: string) => authApi.mfaStepUp(c),
    onSuccess: () => {
      setCode("");
      queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
    },
  });

  if (!me) return null;
  if (stepUpIsFresh(me)) {
    const until = new Date(me.stepUpValidUntil!).toLocaleTimeString(undefined, { timeStyle: "short" });
    return (
      <p className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/5 px-3 py-2 text-sm">
        <ShieldCheck className="size-4 text-success" aria-hidden="true" />
        Identity confirmed. Decisions and privileged changes are enabled until {until}.
      </p>
    );
  }
  return (
    <form
      className="flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning/5 p-3 sm:flex-row sm:items-end"
      onSubmit={(e) => {
        e.preventDefault();
        stepUp.mutate(code);
      }}
    >
      <div className="flex-1">
        <p className="flex items-center gap-2 text-sm font-medium">
          <KeyRound className="size-4 text-warning" aria-hidden="true" /> Confirm your identity to take decisions
        </p>
        <p className="text-xs text-muted-foreground">Enter the current code from your authenticator app. Valid for 10 minutes.</p>
        <FormError error={stepUp.error} title="Code not accepted" />
      </div>
      <div className="flex items-end gap-2">
        <label className="sr-only" htmlFor="step-up-code">Authenticator code</label>
        <Input id="step-up-code" value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric" maxLength={6} className="w-32" placeholder="123456" />
        <Button type="submit" disabled={stepUp.isPending || code.length !== 6}>
          {stepUp.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
          Confirm
        </Button>
      </div>
    </form>
  );
}
