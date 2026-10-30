"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Camera, Loader2, Pencil, Plus, Trash2, X } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { FormField, fieldAria } from "@/components/forms/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { formatDate } from "@/lib/api/types";
import {
  useEducationMutations,
  useExperienceMutations,
  useMyProfile,
  usePhotoMutations,
  useReplaceSkills,
  useSkillSuggestions,
  useUpdateProfile,
} from "../hooks";
import {
  EMPLOYMENT_TYPES,
  educationSchema,
  experienceSchema,
  profileSchema,
  splitList,
  type EducationFormValues,
  type ExperienceFormValues,
  type ProfileFormValues,
} from "../schemas";
import { EMPLOYMENT_LABELS, type EducationView, type ExperienceView, type ProfileView } from "../types";
import { MemberAvatar } from "./member-avatar";

export function ProfileEditor() {
  const { data, isPending, isError, error } = useMyProfile();
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load your profile" />;
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Your profile</h1>
          <p className="mt-1 text-muted-foreground">What verified members see about you, within your privacy settings.</p>
        </div>
        <Link href={`/members/${data.userId}`} className="text-sm text-primary underline-offset-4 hover:underline">View as member</Link>
      </div>
      <PhotoCard profile={data} />
      <BasicsCard profile={data} />
      <SkillsCard profile={data} />
      <ExperienceCard profile={data} />
      <EducationCard profile={data} />
    </div>
  );
}

function PhotoCard({ profile }: { profile: ProfileView }) {
  const { upload, remove } = usePhotoMutations();
  const input = useRef<HTMLInputElement>(null);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Photo</CardTitle>
        <CardDescription>JPEG or PNG up to 2 MB. It is cropped to a square and stripped of metadata.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-4">
        <MemberAvatar name={profile.displayName} photoUrl={profile.photoUrl} size="lg" className="size-20 text-xl" />
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png"
          className="sr-only"
          aria-label="Choose a profile photo"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) upload.mutate(file);
            e.target.value = "";
          }}
        />
        <Button type="button" variant="outline" disabled={upload.isPending} onClick={() => input.current?.click()}>
          {upload.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Camera aria-hidden="true" />} Upload photo
        </Button>
        {profile.photoUrl ? (
          <Button type="button" variant="ghost" disabled={remove.isPending} onClick={() => remove.mutate()}>
            <Trash2 aria-hidden="true" /> Remove
          </Button>
        ) : null}
        <div className="basis-full"><FormError error={upload.error ?? remove.error} /></div>
      </CardContent>
    </Card>
  );
}

function toFormValues(p: ProfileView): ProfileFormValues {
  return {
    displayName: p.displayName,
    headline: p.headline ?? "",
    bio: p.bio ?? "",
    organization: p.organization ?? "",
    jobTitle: p.jobTitle ?? "",
    location: p.location ?? "",
    country: p.country ?? "",
    specializations: p.specializations.join(", "),
    mentorAvailable: p.mentorAvailable ?? false,
    mentorTopics: p.mentorTopics ?? "",
  };
}

function BasicsCard({ profile }: { profile: ProfileView }) {
  const update = useUpdateProfile();
  const form = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema), values: toFormValues(profile) });
  const errors = form.formState.errors;
  const mentor = useWatch({ control: form.control, name: "mentorAvailable" });
  const onSubmit = form.handleSubmit((v) =>
    update.mutate({
      displayName: v.displayName,
      headline: v.headline,
      bio: v.bio,
      organization: v.organization,
      jobTitle: v.jobTitle,
      location: v.location,
      country: v.country,
      specializations: splitList(v.specializations),
      mentorAvailable: v.mentorAvailable,
      mentorTopics: v.mentorTopics,
    }),
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle>About you</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="displayName" label="Display name" error={errors.displayName?.message} required>
              <Input {...form.register("displayName")} {...fieldAria("displayName", errors.displayName?.message)} />
            </FormField>
            <FormField id="headline" label="Headline" error={errors.headline?.message} hint="One line, e.g. “SOC analyst · DFIR enthusiast”.">
              <Input {...form.register("headline")} {...fieldAria("headline", errors.headline?.message)} maxLength={160} />
            </FormField>
            <FormField id="jobTitle" label="Job title" error={errors.jobTitle?.message}>
              <Input {...form.register("jobTitle")} {...fieldAria("jobTitle", errors.jobTitle?.message)} />
            </FormField>
            <FormField id="organization" label="Organisation" error={errors.organization?.message}>
              <Input {...form.register("organization")} {...fieldAria("organization", errors.organization?.message)} />
            </FormField>
            <FormField id="location" label="City" error={errors.location?.message}>
              <Input {...form.register("location")} {...fieldAria("location", errors.location?.message)} />
            </FormField>
            <FormField id="country" label="Country" error={errors.country?.message}>
              <Input {...form.register("country")} {...fieldAria("country", errors.country?.message)} />
            </FormField>
          </div>
          <FormField id="bio" label="Bio" error={errors.bio?.message}>
            <Textarea {...form.register("bio")} {...fieldAria("bio", errors.bio?.message)} rows={5} maxLength={4000} />
          </FormField>
          <FormField id="specializations" label="Specialisations" error={errors.specializations?.message} hint="Comma-separated, up to 10, e.g. “Blue team, DFIR, Cloud security”.">
            <Input {...form.register("specializations")} {...fieldAria("specializations", errors.specializations?.message)} />
          </FormField>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" className="mt-0.5 size-4 accent-primary" {...form.register("mentorAvailable")} />
            <span>
              <span className="block font-medium">Available for mentoring</span>
              <span className="block text-muted-foreground">Shown on your profile and in the directory according to your privacy settings.</span>
            </span>
          </label>
          {mentor ? (
            <FormField id="mentorTopics" label="What can you help with?" error={errors.mentorTopics?.message}>
              <Textarea {...form.register("mentorTopics")} {...fieldAria("mentorTopics", errors.mentorTopics?.message)} rows={3} maxLength={500} />
            </FormField>
          ) : null}
          <FormError error={update.error} />
          {update.isSuccess && !form.formState.isDirty ? <FormSuccess title="Profile saved" /> : null}
          <Button type="submit" disabled={update.isPending}>
            {update.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save profile
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function SkillsCard({ profile }: { profile: ProfileView }) {
  const replace = useReplaceSkills();
  const [skills, setSkills] = useState<string[]>(() => profile.skills.map((s) => s.name));
  const [draft, setDraft] = useState("");
  const { data: suggestions } = useSkillSuggestions(draft.trim(), draft.trim().length > 0);
  const dirty = skills.join("\u0000") !== profile.skills.map((s) => s.name).join("\u0000");

  const add = (name: string) => {
    const clean = name.trim();
    if (!clean) return;
    if (skills.some((s) => s.toLowerCase() === clean.toLowerCase())) { setDraft(""); return; }
    if (skills.length >= 30) return;
    setSkills([...skills, clean]);
    setDraft("");
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Skills</CardTitle>
        <CardDescription>Up to 30. Pick from the catalogue or add your own.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-1.5" aria-label="Your skills">
          {skills.length === 0 ? <p className="text-sm text-muted-foreground">No skills yet.</p> : null}
          {skills.map((s) => (
            <Badge key={s} variant="secondary" className="gap-1 pr-1">
              {s}
              <button type="button" className="rounded-full p-0.5 hover:bg-background/60" aria-label={`Remove ${s}`} onClick={() => setSkills(skills.filter((x) => x !== s))}>
                <X className="size-3" aria-hidden="true" />
              </button>
            </Badge>
          ))}
        </div>
        <div className="flex gap-2">
          <Input
            aria-label="Add a skill"
            placeholder="Type a skill and press Enter"
            value={draft}
            maxLength={60}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") { e.preventDefault(); add(draft); }
            }}
          />
          <Button type="button" variant="outline" onClick={() => add(draft)} disabled={!draft.trim()}>
            <Plus aria-hidden="true" /> Add
          </Button>
        </div>
        {suggestions && suggestions.length > 0 && draft.trim() ? (
          <div className="flex flex-wrap gap-1.5" aria-label="Suggestions">
            {suggestions.filter((s) => !skills.some((x) => x.toLowerCase() === s.name.toLowerCase())).slice(0, 8).map((s) => (
              <button key={s.id} type="button" className="rounded-full border px-2.5 py-0.5 text-xs hover:bg-accent" onClick={() => add(s.name)}>
                + {s.name}
              </button>
            ))}
          </div>
        ) : null}
        <FormError error={replace.error} />
        <Button type="button" disabled={!dirty || replace.isPending} onClick={() => replace.mutate(skills)}>
          {replace.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save skills
        </Button>
      </CardContent>
    </Card>
  );
}

const emptyExperience: ExperienceFormValues = { title: "", organization: "", employmentType: "", location: "", startDate: "", endDate: "", current: false, description: "" };

function ExperienceCard({ profile }: { profile: ProfileView }) {
  const { add, update, remove } = useExperienceMutations();
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const busy = add.isPending || update.isPending || remove.isPending;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Experience</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {profile.experiences.length === 0 && editing !== "new" ? <p className="text-sm text-muted-foreground">No experience listed yet.</p> : null}
        <ul className="space-y-3">
          {profile.experiences.map((e) =>
            editing === e.id ? (
              <li key={e.id}>
                <ExperienceForm
                  initial={{ title: e.title, organization: e.organization, employmentType: e.employmentType ?? "", location: e.location ?? "", startDate: e.startDate, endDate: e.endDate ?? "", current: e.current, description: e.description ?? "" }}
                  busy={busy}
                  error={update.error}
                  onCancel={() => setEditing(null)}
                  onSubmit={(v) => update.mutate({ id: e.id, body: toExperienceRequest(v) }, { onSuccess: () => setEditing(null) })}
                />
              </li>
            ) : (
              <li key={e.id} className="flex items-start justify-between gap-3 rounded-lg border p-3">
                <ExperienceSummary e={e} />
                <div className="flex shrink-0 gap-1">
                  <Button type="button" size="icon-sm" variant="ghost" aria-label={`Edit ${e.title}`} onClick={() => setEditing(e.id)}><Pencil aria-hidden="true" /></Button>
                  <Button type="button" size="icon-sm" variant="ghost" aria-label={`Delete ${e.title}`} disabled={busy} onClick={() => remove.mutate(e.id)}><Trash2 aria-hidden="true" /></Button>
                </div>
              </li>
            ),
          )}
        </ul>
        <FormError error={remove.error} />
        {editing === "new" ? (
          <ExperienceForm initial={emptyExperience} busy={busy} error={add.error} onCancel={() => setEditing(null)} onSubmit={(v) => add.mutate(toExperienceRequest(v), { onSuccess: () => setEditing(null) })} />
        ) : (
          <Button type="button" variant="outline" onClick={() => setEditing("new")}><Plus aria-hidden="true" /> Add experience</Button>
        )}
      </CardContent>
    </Card>
  );
}

function ExperienceSummary({ e }: { e: ExperienceView }) {
  return (
    <div className="min-w-0">
      <p className="font-medium">{e.title}</p>
      <p className="text-sm text-muted-foreground">{e.organization}{e.employmentType ? ` · ${EMPLOYMENT_LABELS[e.employmentType]}` : ""}</p>
      <p className="text-xs text-muted-foreground">{formatDate(e.startDate)} – {e.current ? "present" : formatDate(e.endDate)}</p>
    </div>
  );
}

function toExperienceRequest(v: ExperienceFormValues) {
  return {
    title: v.title,
    organization: v.organization,
    employmentType: v.employmentType || undefined,
    location: v.location || undefined,
    startDate: v.startDate,
    endDate: v.current ? undefined : v.endDate || undefined,
    current: v.current,
    description: v.description || undefined,
  };
}

function ExperienceForm({ initial, busy, error, onCancel, onSubmit }: {
  initial: ExperienceFormValues;
  busy: boolean;
  error: unknown;
  onCancel: () => void;
  onSubmit: (values: ExperienceFormValues) => void;
}) {
  const form = useForm<ExperienceFormValues>({ resolver: zodResolver(experienceSchema), defaultValues: initial });
  const errors = form.formState.errors;
  const current = useWatch({ control: form.control, name: "current" });
  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3 rounded-lg border p-3" noValidate>
      <div className="grid gap-3 sm:grid-cols-2">
        <FormField id="exp-title" label="Title" error={errors.title?.message} required>
          <Input {...form.register("title")} {...fieldAria("exp-title", errors.title?.message)} />
        </FormField>
        <FormField id="exp-org" label="Organisation" error={errors.organization?.message} required>
          <Input {...form.register("organization")} {...fieldAria("exp-org", errors.organization?.message)} />
        </FormField>
        <FormField id="exp-type" label="Type" error={errors.employmentType?.message}>
          <select id="exp-type" className="h-9 w-full rounded-lg border bg-background px-2 text-sm" {...form.register("employmentType")}>
            <option value="">—</option>
            {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{EMPLOYMENT_LABELS[t]}</option>)}
          </select>
        </FormField>
        <FormField id="exp-location" label="Location" error={errors.location?.message}>
          <Input {...form.register("location")} {...fieldAria("exp-location", errors.location?.message)} />
        </FormField>
        <FormField id="exp-start" label="Start date" error={errors.startDate?.message} required>
          <Input type="date" {...form.register("startDate")} {...fieldAria("exp-start", errors.startDate?.message)} />
        </FormField>
        <FormField id="exp-end" label="End date" error={errors.endDate?.message}>
          <Input type="date" {...form.register("endDate")} {...fieldAria("exp-end", errors.endDate?.message)} disabled={current} />
        </FormField>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="size-4 accent-primary" {...form.register("current")} /> I currently work here
      </label>
      <FormField id="exp-description" label="Description" error={errors.description?.message}>
        <Textarea {...form.register("description")} {...fieldAria("exp-description", errors.description?.message)} rows={3} maxLength={2000} />
      </FormField>
      <FormError error={error} />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={busy}>{busy ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save</Button>
        <Button type="button" size="sm" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}

const emptyEducation: EducationFormValues = { institution: "", degree: "", fieldOfStudy: "", startYear: "", endYear: "", description: "" };

function EducationCard({ profile }: { profile: ProfileView }) {
  const { add, update, remove } = useEducationMutations();
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const busy = add.isPending || update.isPending || remove.isPending;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Education</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {profile.educations.length === 0 && editing !== "new" ? <p className="text-sm text-muted-foreground">No education listed yet.</p> : null}
        <ul className="space-y-3">
          {profile.educations.map((e) =>
            editing === e.id ? (
              <li key={e.id}>
                <EducationForm
                  initial={{ institution: e.institution, degree: e.degree ?? "", fieldOfStudy: e.fieldOfStudy ?? "", startYear: e.startYear?.toString() ?? "", endYear: e.endYear?.toString() ?? "", description: e.description ?? "" }}
                  busy={busy}
                  error={update.error}
                  onCancel={() => setEditing(null)}
                  onSubmit={(v) => update.mutate({ id: e.id, body: toEducationRequest(v) }, { onSuccess: () => setEditing(null) })}
                />
              </li>
            ) : (
              <li key={e.id} className="flex items-start justify-between gap-3 rounded-lg border p-3">
                <EducationSummary e={e} />
                <div className="flex shrink-0 gap-1">
                  <Button type="button" size="icon-sm" variant="ghost" aria-label={`Edit ${e.institution}`} onClick={() => setEditing(e.id)}><Pencil aria-hidden="true" /></Button>
                  <Button type="button" size="icon-sm" variant="ghost" aria-label={`Delete ${e.institution}`} disabled={busy} onClick={() => remove.mutate(e.id)}><Trash2 aria-hidden="true" /></Button>
                </div>
              </li>
            ),
          )}
        </ul>
        <FormError error={remove.error} />
        {editing === "new" ? (
          <EducationForm initial={emptyEducation} busy={busy} error={add.error} onCancel={() => setEditing(null)} onSubmit={(v) => add.mutate(toEducationRequest(v), { onSuccess: () => setEditing(null) })} />
        ) : (
          <Button type="button" variant="outline" onClick={() => setEditing("new")}><Plus aria-hidden="true" /> Add education</Button>
        )}
      </CardContent>
    </Card>
  );
}

function EducationSummary({ e }: { e: EducationView }) {
  return (
    <div className="min-w-0">
      <p className="font-medium">{e.institution}</p>
      <p className="text-sm text-muted-foreground">{[e.degree, e.fieldOfStudy].filter(Boolean).join(", ") || "—"}</p>
      {e.startYear || e.endYear ? <p className="text-xs text-muted-foreground">{e.startYear ?? ""}{e.startYear && e.endYear ? " – " : ""}{e.endYear ?? ""}</p> : null}
    </div>
  );
}

function toEducationRequest(v: EducationFormValues) {
  return {
    institution: v.institution,
    degree: v.degree || undefined,
    fieldOfStudy: v.fieldOfStudy || undefined,
    startYear: v.startYear ? Number(v.startYear) : undefined,
    endYear: v.endYear ? Number(v.endYear) : undefined,
    description: v.description || undefined,
  };
}

function EducationForm({ initial, busy, error, onCancel, onSubmit }: {
  initial: EducationFormValues;
  busy: boolean;
  error: unknown;
  onCancel: () => void;
  onSubmit: (values: EducationFormValues) => void;
}) {
  const form = useForm<EducationFormValues>({ resolver: zodResolver(educationSchema), defaultValues: initial });
  const errors = form.formState.errors;
  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3 rounded-lg border p-3" noValidate>
      <div className="grid gap-3 sm:grid-cols-2">
        <FormField id="edu-institution" label="Institution" error={errors.institution?.message} required className="sm:col-span-2">
          <Input {...form.register("institution")} {...fieldAria("edu-institution", errors.institution?.message)} />
        </FormField>
        <FormField id="edu-degree" label="Degree" error={errors.degree?.message}>
          <Input {...form.register("degree")} {...fieldAria("edu-degree", errors.degree?.message)} placeholder="e.g. BSc" />
        </FormField>
        <FormField id="edu-field" label="Field of study" error={errors.fieldOfStudy?.message}>
          <Input {...form.register("fieldOfStudy")} {...fieldAria("edu-field", errors.fieldOfStudy?.message)} placeholder="e.g. Cybersecurity" />
        </FormField>
        <FormField id="edu-start" label="Start year" error={errors.startYear?.message}>
          <Input {...form.register("startYear")} {...fieldAria("edu-start", errors.startYear?.message)} inputMode="numeric" maxLength={4} />
        </FormField>
        <FormField id="edu-end" label="End year" error={errors.endYear?.message}>
          <Input {...form.register("endYear")} {...fieldAria("edu-end", errors.endYear?.message)} inputMode="numeric" maxLength={4} />
        </FormField>
      </div>
      <FormField id="edu-description" label="Description" error={errors.description?.message}>
        <Textarea {...form.register("description")} {...fieldAria("edu-description", errors.description?.message)} rows={3} maxLength={2000} />
      </FormField>
      <FormError error={error} />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={busy}>{busy ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save</Button>
        <Button type="button" size="sm" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}
