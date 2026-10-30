"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { FormError } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { TagInput } from "@/components/forms/tag-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useOpportunity, useSaveOpportunity } from "../hooks";
import { opportunitySchema, type OpportunityFormValues } from "../schemas";
import { LOCATION_LABELS, METHOD_LABELS, OPPORTUNITY_TYPE_LABELS, type OpportunityView } from "../types";

const selectClass = "h-9 w-full rounded-lg border bg-background px-2 text-sm";

export function OpportunityEditor({ id }: { id?: string }) {
  const { data, isPending, isError, error } = useOpportunity(id ?? "");
  if (!id) return <OpportunityForm />;
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the opportunity" />;
  return <OpportunityForm existing={data} />;
}

function OpportunityForm({ existing }: { existing?: OpportunityView }) {
  const router = useRouter();
  const save = useSaveOpportunity();
  const [skills, setSkills] = useState<string[]>(existing?.skills.map((s) => s.name) ?? []);
  const form = useForm<OpportunityFormValues>({
    resolver: zodResolver(opportunitySchema),
    defaultValues: {
      type: existing?.type ?? "INTERNSHIP",
      title: existing?.title ?? "",
      organization: existing?.organization ?? "",
      locationType: existing?.locationType ?? "ONSITE",
      location: existing?.location ?? "",
      description: existing?.description ?? "",
      compensation: existing?.compensation ?? "",
      deadline: existing?.deadline ?? "",
      applicationMethod: existing?.applicationMethod ?? "INTERNAL",
      applicationUrl: existing?.applicationUrl ?? "",
      applicationEmail: existing?.applicationEmail ?? "",
    },
  });
  const errors = form.formState.errors;
  const method = useWatch({ control: form.control, name: "applicationMethod" });

  const submit = (publish: boolean) =>
    form.handleSubmit(async (v) => {
      const saved = await save.mutateAsync({
        id: existing?.id,
        body: {
          ...v,
          location: v.location || undefined,
          compensation: v.compensation || undefined,
          deadline: v.deadline || undefined,
          applicationUrl: v.applicationUrl || undefined,
          applicationEmail: v.applicationEmail || undefined,
          skills,
          publish,
        },
      });
      router.push(`/opportunities/${saved.id}`);
    });

  return (
    <div className="space-y-6">
      <div>
        <Link href={existing ? `/opportunities/${existing.id}` : "/opportunities"} className="text-sm text-muted-foreground hover:text-foreground">← Opportunities</Link>
        <h1 className="text-2xl font-semibold">{existing ? "Edit opportunity" : "Post an opportunity"}</h1>
        <p className="mt-1 text-muted-foreground">Jobs, internships, research assistantships, freelance work and scholarships for verified members.</p>
      </div>
      <Card>
        <CardHeader><CardTitle>Details</CardTitle></CardHeader>
        <CardContent>
          <form className="space-y-4" noValidate onSubmit={(e) => e.preventDefault()}>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="type" label="Type" error={errors.type?.message} required>
                <select id="type" className={selectClass} {...form.register("type")}>
                  {Object.entries(OPPORTUNITY_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </FormField>
              <FormField id="title" label="Title" error={errors.title?.message} required>
                <Input {...form.register("title")} {...fieldAria("title", errors.title?.message)} />
              </FormField>
              <FormField id="organization" label="Organisation" error={errors.organization?.message} required>
                <Input {...form.register("organization")} {...fieldAria("organization", errors.organization?.message)} />
              </FormField>
              <FormField id="locationType" label="Work location" error={errors.locationType?.message} required>
                <select id="locationType" className={selectClass} {...form.register("locationType")}>
                  {Object.entries(LOCATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </FormField>
              <FormField id="location" label="City / country" error={errors.location?.message}>
                <Input {...form.register("location")} {...fieldAria("location", errors.location?.message)} />
              </FormField>
              <FormField id="compensation" label="Compensation" error={errors.compensation?.message} hint="Optional, e.g. “BDT 25,000/month” or “Full tuition”.">
                <Input {...form.register("compensation")} {...fieldAria("compensation", errors.compensation?.message)} />
              </FormField>
              <FormField id="deadline" label="Application deadline" error={errors.deadline?.message} hint="Optional. Open opportunities expire automatically after this date.">
                <Input type="date" {...form.register("deadline")} {...fieldAria("deadline", errors.deadline?.message)} />
              </FormField>
            </div>
            <FormField id="description" label="Description" error={errors.description?.message} required hint="Plain text. Responsibilities, requirements, how to prepare.">
              <Textarea rows={8} maxLength={10_000} {...form.register("description")} {...fieldAria("description", errors.description?.message)} />
            </FormField>
            <div className="space-y-1.5">
              <Label htmlFor="skills">Relevant skills</Label>
              <TagInput id="skills" value={skills} onChange={setSkills} />
            </div>
            <fieldset className="space-y-3 rounded-lg border p-4">
              <legend className="px-1 text-sm font-medium">How to apply</legend>
              {(Object.keys(METHOD_LABELS) as (keyof typeof METHOD_LABELS)[]).map((m) => (
                <label key={m} className="flex items-center gap-2 text-sm">
                  <input type="radio" value={m} {...form.register("applicationMethod")} className="size-4 accent-primary" /> {METHOD_LABELS[m]}
                </label>
              ))}
              {method === "EXTERNAL_URL" ? (
                <FormField id="applicationUrl" label="Application link" error={errors.applicationUrl?.message} required>
                  <Input type="url" placeholder="https://" {...form.register("applicationUrl")} {...fieldAria("applicationUrl", errors.applicationUrl?.message)} />
                </FormField>
              ) : null}
              {method === "EMAIL" ? (
                <FormField id="applicationEmail" label="Application email" error={errors.applicationEmail?.message} required>
                  <Input type="email" {...form.register("applicationEmail")} {...fieldAria("applicationEmail", errors.applicationEmail?.message)} />
                </FormField>
              ) : null}
            </fieldset>
            <FormError error={save.error} />
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={submit(true)} disabled={save.isPending}>
                {save.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} {existing?.status === "OPEN" ? "Save changes" : "Publish"}
              </Button>
              {!existing || existing.status === "DRAFT" ? (
                <Button type="button" variant="outline" onClick={submit(false)} disabled={save.isPending}>Save as draft</Button>
              ) : null}
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
