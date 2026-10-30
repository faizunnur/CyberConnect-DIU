"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useMyProfile, useUpdatePrivacy } from "../hooks";
import {
  FIELD_VISIBILITY_LABELS,
  MESSAGING_LABELS,
  PROFILE_VISIBILITY_LABELS,
  type FieldVisibility,
  type MessagingPolicy,
  type PrivacySettings,
  type ProfileVisibility,
} from "../types";

const selectClass = "h-9 w-full rounded-lg border bg-background px-2 text-sm";

export function PrivacySettingsForm() {
  const { data, isPending, isError, error } = useMyProfile();
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError || !data.privacy) return <FormError error={error} title="Could not load your privacy settings" />;
  return <PrivacyForm key={JSON.stringify(data.privacy)} initial={data.privacy} />;
}

function PrivacyForm({ initial }: { initial: PrivacySettings }) {
  const [values, setValues] = useState<PrivacySettings>(initial);
  const save = useUpdatePrivacy();
  const set = <K extends keyof PrivacySettings>(key: K, value: PrivacySettings[K]) => setValues({ ...values, [key]: value });

  return (
    <form
      className="space-y-6"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate(values);
      }}
    >
      <div>
        <h1 className="text-2xl font-semibold">Privacy</h1>
        <p className="mt-1 text-muted-foreground">
          Choose who can see your profile and contact you. Your student id and identity documents are never shown to anyone.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Profile visibility</CardTitle>
          <CardDescription>Members who cannot open your full profile still see your name, headline and photo so they can send a request.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field id="profileVisibility" label="Full profile">
            <select id="profileVisibility" className={selectClass} value={values.profileVisibility} onChange={(e) => set("profileVisibility", e.target.value as ProfileVisibility)}>
              {Object.entries(PROFILE_VISIBILITY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Field>
          <VisibilitySelect id="emailVisibility" label="Email address" value={values.emailVisibility} onChange={(v) => set("emailVisibility", v)} />
          <VisibilitySelect id="organizationVisibility" label="Organisation" value={values.organizationVisibility} onChange={(v) => set("organizationVisibility", v)} />
          <VisibilitySelect id="locationVisibility" label="Location and country" value={values.locationVisibility} onChange={(v) => set("locationVisibility", v)} />
          <VisibilitySelect id="mentorVisibility" label="Mentor availability" value={values.mentorVisibility} onChange={(v) => set("mentorVisibility", v)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Discovery and contact</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" className="mt-0.5 size-4 accent-primary" checked={values.searchable} onChange={(e) => set("searchable", e.target.checked)} />
            <span>
              <span className="block font-medium">Show me in the member directory</span>
              <span className="block text-muted-foreground">When off, members can still open your profile from a direct link or a connection.</span>
            </span>
          </label>
          <Field id="messagingPolicy" label="Who can message me">
            <select id="messagingPolicy" className={selectClass} value={values.messagingPolicy} onChange={(e) => set("messagingPolicy", e.target.value as MessagingPolicy)}>
              {Object.entries(MESSAGING_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Field>
        </CardContent>
      </Card>

      <FormError error={save.error} />
      {save.isSuccess ? <FormSuccess title="Privacy settings saved" /> : null}
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save privacy settings
      </Button>
    </form>
  );
}

function Field({ id, label, children }: { id: string; label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  );
}

function VisibilitySelect({ id, label, value, onChange }: { id: string; label: string; value: FieldVisibility; onChange: (v: FieldVisibility) => void }) {
  return (
    <Field id={id} label={label}>
      <select id={id} className={selectClass} value={value} onChange={(e) => onChange(e.target.value as FieldVisibility)}>
        {Object.entries(FIELD_VISIBILITY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
    </Field>
  );
}
