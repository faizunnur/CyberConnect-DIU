"use client";

import { FlaskConical, Plus, Search, Users } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatDate } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { useMyResearchApplications, useResearchProjects } from "../hooks";
import { RESEARCH_STATUS_LABELS, type ResearchCard as ResearchCardData, type ResearchParams, type ResearchStatus } from "../types";

export function ResearchCardItem({ item }: { item: ResearchCardData }) {
  return (
    <Card data-testid="research-card">
      <CardContent className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <Link href={`/research/${item.id}`} className="font-semibold hover:underline">{item.title}</Link>
          <div className="flex gap-1.5">
            <Badge variant="secondary">{RESEARCH_STATUS_LABELS[item.status]}</Badge>
            {item.lookingForMembers ? <Badge variant="outline" className="border-success text-success">Recruiting</Badge> : null}
            {item.member ? <Badge variant="outline">Member</Badge> : null}
          </div>
        </div>
        <p className="flex flex-wrap items-center gap-x-3 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1"><Users className="size-3" aria-hidden="true" /> {item.memberCount} member{item.memberCount === 1 ? "" : "s"}</span>
          {item.lead ? <span>led by <Link href={`/members/${item.lead.userId}`} className="hover:underline">{item.lead.displayName}</Link></span> : null}
          <span>{formatDate(item.createdAt)}</span>
        </p>
        {item.skills.length > 0 ? <p className="text-xs text-muted-foreground">Needs: {item.skills.join(" · ")}</p> : null}
      </CardContent>
    </Card>
  );
}

export function ResearchList() {
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Research hub</h1>
          <p className="mt-1 text-muted-foreground">Propose projects, find collaborators with the right skills, and build a research record.</p>
        </div>
        <Link href="/research/new" className={cn(buttonVariants())}><Plus aria-hidden="true" /> Propose a project</Link>
      </div>
      <Tabs defaultValue="browse">
        <TabsList>
          <TabsTrigger value="browse">Browse</TabsTrigger>
          <TabsTrigger value="mine">My projects</TabsTrigger>
          <TabsTrigger value="applications">My applications</TabsTrigger>
        </TabsList>
        <TabsContent value="browse"><Browse /></TabsContent>
        <TabsContent value="mine"><Mine /></TabsContent>
        <TabsContent value="applications"><Applications /></TabsContent>
      </Tabs>
    </div>
  );
}

function Grid({ params }: { params: ResearchParams }) {
  const { data, isPending, isError, error } = useResearchProjects(params);
  const [, setPage] = useState(0);
  if (isPending) return <Skeleton className="h-64 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) return <EmptyState title="No projects match" text="Try a different filter, or propose one yourself." />;
  return (
    <>
      <div className="grid gap-4 md:grid-cols-2">{data.content.map((p) => <ResearchCardItem key={p.id} item={p} />)}</div>
      <Pager page={data.page} onChange={setPage} />
    </>
  );
}

function Browse() {
  const [draft, setDraft] = useState({ q: "", status: "", skill: "", recruiting: false });
  const [applied, setApplied] = useState<ResearchParams>({ page: 0, size: 20 });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    setApplied({
      q: draft.q || undefined,
      status: (draft.status || undefined) as ResearchStatus | undefined,
      skill: draft.skill || undefined,
      lookingForMembers: draft.recruiting ? true : undefined,
      page: 0,
      size: 20,
    });
  };
  return (
    <div className="space-y-4">
      <form onSubmit={submit} className="flex flex-wrap gap-2" aria-label="Research filters">
        <div className="relative min-w-48 flex-1">
          <Search className="pointer-events-none absolute top-2.5 left-2.5 size-4 text-muted-foreground" aria-hidden="true" />
          <Input aria-label="Search projects" placeholder="Search title or summary" className="pl-8" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
        </div>
        <select className="h-9 rounded-lg border bg-background px-2 text-sm" aria-label="Status" value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
          <option value="">Proposed and active</option>
          {(["PROPOSED", "ACTIVE", "COMPLETED", "ARCHIVED"] as ResearchStatus[]).map((s) => <option key={s} value={s}>{RESEARCH_STATUS_LABELS[s]}</option>)}
        </select>
        <Input aria-label="Needed skill" placeholder="Skill" className="w-40" value={draft.skill} onChange={(e) => setDraft({ ...draft, skill: e.target.value })} />
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="size-4 accent-primary" checked={draft.recruiting} onChange={(e) => setDraft({ ...draft, recruiting: e.target.checked })} /> Recruiting</label>
        <Button type="submit" variant="outline">Filter</Button>
      </form>
      <Grid params={applied} />
    </div>
  );
}

function Mine() {
  return <Grid params={{ mine: true, page: 0, size: 20 }} />;
}

function Applications() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMyResearchApplications(page);
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) return <EmptyState title="No applications yet" />;
  return (
    <div className="space-y-4">
      <ul className="space-y-3">
        {data.content.map((a) => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm">
            <div>
              {a.project ? <Link href={`/research/${a.project.id}`} className="font-medium hover:underline">{a.project.title}</Link> : "Project"}
              <p className="text-xs text-muted-foreground"><FlaskConical className="mr-1 inline size-3" aria-hidden="true" />applied {formatDate(a.createdAt)}</p>
            </div>
            <Badge variant={a.status === "ACCEPTED" ? "default" : "secondary"}>{a.status.toLowerCase()}</Badge>
          </li>
        ))}
      </ul>
      <Pager page={data.page} onChange={setPage} />
    </div>
  );
}
