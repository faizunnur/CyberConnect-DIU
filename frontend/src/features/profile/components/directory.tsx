"use client";

import { Search } from "lucide-react";
import { useState, type FormEvent } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { AFFILIATION_LABELS, type AffiliationType } from "@/features/auth/types";
import { useDirectory } from "../hooks";
import type { DirectoryParams } from "../types";
import { MemberCardItem } from "./member-card";

const selectClass = "h-9 w-full rounded-lg border bg-background px-2 text-sm";

export function Directory() {
  const [draft, setDraft] = useState({ q: "", affiliation: "", skill: "", organization: "", experience: "", mentor: false });
  const [applied, setApplied] = useState<DirectoryParams>({ page: 0, size: 20 });
  const { data, isPending, isError, error, isFetching } = useDirectory(applied);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setApplied({
      q: draft.q || undefined,
      affiliation: (draft.affiliation || undefined) as AffiliationType | undefined,
      skill: draft.skill || undefined,
      organization: draft.organization || undefined,
      experience: draft.experience || undefined,
      mentorAvailable: draft.mentor ? true : undefined,
      page: 0,
      size: 20,
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Members</h1>
        <p className="mt-1 text-muted-foreground">Verified students, alumni and CSC members. Search by name, skill, role or organisation.</p>
      </div>

      <form onSubmit={submit} className="space-y-3 rounded-xl border bg-card p-4" aria-label="Directory filters">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-2.5 left-2.5 size-4 text-muted-foreground" aria-hidden="true" />
            <Input
              aria-label="Search members"
              placeholder="Search by name, headline, skill or specialisation"
              className="pl-8"
              value={draft.q}
              onChange={(e) => setDraft({ ...draft, q: e.target.value })}
            />
          </div>
          <Button type="submit" disabled={isFetching}>Search</Button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1">
            <Label htmlFor="dir-affiliation" className="text-xs">Affiliation</Label>
            <select id="dir-affiliation" className={selectClass} value={draft.affiliation} onChange={(e) => setDraft({ ...draft, affiliation: e.target.value })}>
              <option value="">Any</option>
              {Object.entries(AFFILIATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="dir-skill" className="text-xs">Skill</Label>
            <Input id="dir-skill" placeholder="e.g. Digital Forensics" value={draft.skill} onChange={(e) => setDraft({ ...draft, skill: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="dir-org" className="text-xs">Organisation</Label>
            <Input id="dir-org" placeholder="e.g. bank, university" value={draft.organization} onChange={(e) => setDraft({ ...draft, organization: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="dir-exp" className="text-xs">Experience / role</Label>
            <Input id="dir-exp" placeholder="e.g. SOC analyst" value={draft.experience} onChange={(e) => setDraft({ ...draft, experience: e.target.value })} />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-primary" checked={draft.mentor} onChange={(e) => setDraft({ ...draft, mentor: e.target.checked })} />
          Available for mentoring
        </label>
      </form>

      {isPending ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 w-full" />)}
        </div>
      ) : isError ? (
        <FormError error={error} title="Could not load the directory" />
      ) : data.content.length === 0 ? (
        <EmptyState title="No members match" text="Try fewer filters or a different spelling." />
      ) : (
        <>
          <p className="text-sm text-muted-foreground" aria-live="polite">{data.page.totalElements} member{data.page.totalElements === 1 ? "" : "s"}</p>
          <div className="grid gap-4 md:grid-cols-2">
            {data.content.map((member) => <MemberCardItem key={member.userId} member={member} />)}
          </div>
          <Pager page={data.page} onChange={(page) => setApplied({ ...applied, page })} />
        </>
      )}
    </div>
  );
}
