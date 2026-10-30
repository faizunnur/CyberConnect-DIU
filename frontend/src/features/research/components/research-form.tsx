"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormError } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { TagInput } from "@/components/forms/tag-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { researchSchema, type ResearchFormValues } from "@/features/opportunities/schemas";
import { useResearchProject, useSaveResearch } from "../hooks";
import type { ResearchProjectView } from "../types";

export function ResearchEditor({ id }: { id?: string }) {
  const { data, isPending, isError, error } = useResearchProject(id ?? "");
  if (!id) return <ResearchForm />;
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the project" />;
  return <ResearchForm existing={data} />;
}

function ResearchForm({ existing }: { existing?: ResearchProjectView }) {
  const router = useRouter();
  const save = useSaveResearch();
  const [skills, setSkills] = useState<string[]>(existing?.skills.map((s) => s.name) ?? []);
  const form = useForm<ResearchFormValues>({
    resolver: zodResolver(researchSchema),
    defaultValues: {
      title: existing?.title ?? "",
      summary: existing?.summary ?? "",
      lookingForMembers: existing?.lookingForMembers ?? true,
      startDate: existing?.startDate ?? "",
      endDate: existing?.endDate ?? "",
    },
  });
  const errors = form.formState.errors;
  const onSubmit = form.handleSubmit(async (v) => {
    const saved = await save.mutateAsync({
      id: existing?.id,
      body: { ...v, startDate: v.startDate || undefined, endDate: v.endDate || undefined, skills },
    });
    router.push(`/research/${saved.id}`);
  });
  return (
    <div className="space-y-6">
      <div>
        <Link href={existing ? `/research/${existing.id}` : "/research"} className="text-sm text-muted-foreground hover:text-foreground">← Research hub</Link>
        <h1 className="text-2xl font-semibold">{existing ? "Edit project" : "Propose a research project"}</h1>
      </div>
      <Card>
        <CardHeader><CardTitle>Project</CardTitle></CardHeader>
        <CardContent>
          <form className="space-y-4" noValidate onSubmit={onSubmit}>
            <FormField id="title" label="Title" error={errors.title?.message} required>
              <Input {...form.register("title")} {...fieldAria("title", errors.title?.message)} />
            </FormField>
            <FormField id="summary" label="Summary" error={errors.summary?.message} required hint="Goals, methods, what collaborators would do.">
              <Textarea rows={8} maxLength={10_000} {...form.register("summary")} {...fieldAria("summary", errors.summary?.message)} />
            </FormField>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="startDate" label="Start date" error={errors.startDate?.message}>
                <Input type="date" {...form.register("startDate")} {...fieldAria("startDate", errors.startDate?.message)} />
              </FormField>
              <FormField id="endDate" label="End date" error={errors.endDate?.message}>
                <Input type="date" {...form.register("endDate")} {...fieldAria("endDate", errors.endDate?.message)} />
              </FormField>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="needed-skills">Needed skills</Label>
              <TagInput id="needed-skills" value={skills} onChange={setSkills} />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="size-4 accent-primary" {...form.register("lookingForMembers")} /> Looking for members
            </label>
            <FormError error={save.error} />
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} {existing ? "Save changes" : "Create project"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
