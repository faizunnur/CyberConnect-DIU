"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { MANDATORY_TYPES, NOTIFICATION_TYPE_LABELS, useNotificationPreferences, useUpdatePreferences, type PreferenceView } from "../api";

export function NotificationPreferences() {
  const { data, isPending, isError, error } = useNotificationPreferences();
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load your notification settings" />;
  return <Form key={JSON.stringify(data)} initial={data} />;
}

function Form({ initial }: { initial: PreferenceView[] }) {
  const [values, setValues] = useState(initial);
  const save = useUpdatePreferences();
  const set = (type: PreferenceView["type"], patch: Partial<PreferenceView>) =>
    setValues(values.map((v) => (v.type === type ? { ...v, ...patch } : v)));
  return (
    <form
      className="space-y-6"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate(values);
      }}
    >
      <div>
        <h1 className="text-2xl font-semibold">Notifications</h1>
        <p className="mt-1 text-muted-foreground">Choose what reaches your in-app inbox and your email. Realtime updates follow the in-app setting.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Delivery</CardTitle>
          <CardDescription>Verification decisions, moderation notices and account notices always appear in-app.</CardDescription>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-muted-foreground uppercase">
                <th className="py-2 font-medium">Event</th>
                <th className="py-2 text-center font-medium">In-app</th>
                <th className="py-2 text-center font-medium">Email</th>
              </tr>
            </thead>
            <tbody>
              {values.map((v) => {
                const mandatory = MANDATORY_TYPES.includes(v.type);
                return (
                  <tr key={v.type} className="border-t">
                    <td className="py-2 pr-2">{NOTIFICATION_TYPE_LABELS[v.type]}</td>
                    <td className="py-2 text-center">
                      <input type="checkbox" className="size-4 accent-primary" aria-label={`${NOTIFICATION_TYPE_LABELS[v.type]} in-app`} checked={v.inApp || mandatory} disabled={mandatory} onChange={(e) => set(v.type, { inApp: e.target.checked })} />
                    </td>
                    <td className="py-2 text-center">
                      <input type="checkbox" className="size-4 accent-primary" aria-label={`${NOTIFICATION_TYPE_LABELS[v.type]} email`} checked={v.email} onChange={(e) => set(v.type, { email: e.target.checked })} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </CardContent>
      </Card>
      <FormError error={save.error} />
      {save.isSuccess ? <FormSuccess title="Notification settings saved" /> : null}
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save settings
      </Button>
    </form>
  );
}
