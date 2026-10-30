"use client";

import { Briefcase, MapPin, Plus, Search } from "lucide-react";
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
import { useMyApplications, useMyOpportunities, useOpportunities } from "../hooks";
import {
  APPLICATION_STATUS_LABELS,
  LOCATION_LABELS,
  OPPORTUNITY_STATUS_LABELS,
  OPPORTUNITY_TYPE_LABELS,
  type LocationType,
  type OpportunityCard as OpportunityCardData,
  type OpportunityParams,
  type OpportunityType,
} from "../types";

const selectClass = "h-9 rounded-lg border bg-background px-2 text-sm";

export function OpportunityCardItem({ item }: { item: OpportunityCardData }) {
  return (
    <Card data-testid="opportunity-card">
      <CardContent className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <Link href={`/opportunities/${item.id}`} className="font-semibold hover:underline">{item.title}</Link>
            <p className="text-sm text-muted-foreground">{item.organization}</p>
          </div>
          <div className="flex gap-1.5">
            <Badge variant="secondary">{OPPORTUNITY_TYPE_LABELS[item.type]}</Badge>
            {item.status !== "OPEN" ? <Badge variant="outline">{OPPORTUNITY_STATUS_LABELS[item.status]}</Badge> : null}
          </div>
        </div>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1"><MapPin className="size-3" aria-hidden="true" /> {LOCATION_LABELS[item.locationType]}{item.location ? ` · ${item.location}` : ""}</span>
          {item.deadline ? <span>Deadline {formatDate(item.deadline)}</span> : null}
          {item.own ? <span>{item.applicationCount} application{item.applicationCount === 1 ? "" : "s"}</span> : null}
          {item.author && !item.own ? <span>by <Link href={`/members/${item.author.userId}`} className="hover:underline">{item.author.displayName}</Link></span> : null}
        </p>
        {item.skills.length > 0 ? <p className="text-xs text-muted-foreground">{item.skills.join(" · ")}</p> : null}
      </CardContent>
    </Card>
  );
}

export function OpportunityList() {
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Opportunities</h1>
          <p className="mt-1 text-muted-foreground">Jobs, internships, research assistantships, freelance work and scholarships shared by verified members.</p>
        </div>
        <Link href="/opportunities/new" className={cn(buttonVariants())}><Plus aria-hidden="true" /> Post an opportunity</Link>
      </div>
      <Tabs defaultValue="browse">
        <TabsList>
          <TabsTrigger value="browse">Browse</TabsTrigger>
          <TabsTrigger value="mine">Posted by me</TabsTrigger>
          <TabsTrigger value="applications">My applications</TabsTrigger>
        </TabsList>
        <TabsContent value="browse"><Browse /></TabsContent>
        <TabsContent value="mine"><Mine /></TabsContent>
        <TabsContent value="applications"><MyApplications /></TabsContent>
      </Tabs>
    </div>
  );
}

function Browse() {
  const [draft, setDraft] = useState({ q: "", type: "", locationType: "", skill: "" });
  const [applied, setApplied] = useState<OpportunityParams>({ page: 0, size: 20 });
  const { data, isPending, isError, error } = useOpportunities(applied);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    setApplied({
      q: draft.q || undefined,
      type: (draft.type || undefined) as OpportunityType | undefined,
      locationType: (draft.locationType || undefined) as LocationType | undefined,
      skill: draft.skill || undefined,
      page: 0,
      size: 20,
    });
  };
  return (
    <div className="space-y-4">
      <form onSubmit={submit} className="flex flex-wrap gap-2" aria-label="Opportunity filters">
        <div className="relative min-w-48 flex-1">
          <Search className="pointer-events-none absolute top-2.5 left-2.5 size-4 text-muted-foreground" aria-hidden="true" />
          <Input aria-label="Search opportunities" placeholder="Search title, organisation, description" className="pl-8" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
        </div>
        <select className={selectClass} aria-label="Type" value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value })}>
          <option value="">Any type</option>
          {Object.entries(OPPORTUNITY_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className={selectClass} aria-label="Work location" value={draft.locationType} onChange={(e) => setDraft({ ...draft, locationType: e.target.value })}>
          <option value="">Any location</option>
          {Object.entries(LOCATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <Input aria-label="Skill" placeholder="Skill" className="w-40" value={draft.skill} onChange={(e) => setDraft({ ...draft, skill: e.target.value })} />
        <Button type="submit" variant="outline">Filter</Button>
      </form>
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No open opportunities match" text="Try fewer filters, or post one yourself." />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">{data.content.map((o) => <OpportunityCardItem key={o.id} item={o} />)}</div>
          <Pager page={data.page} onChange={(page) => setApplied({ ...applied, page })} />
        </>
      )}
    </div>
  );
}

function Mine() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMyOpportunities(page);
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) return <EmptyState title="You have not posted anything yet" text="Drafts and published opportunities appear here." />;
  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-2">{data.content.map((o) => <OpportunityCardItem key={o.id} item={o} />)}</div>
      <Pager page={data.page} onChange={setPage} />
    </div>
  );
}

function MyApplications() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMyApplications(page);
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) return <EmptyState title="No applications yet" text="Applications you submit on the platform are tracked here." />;
  return (
    <div className="space-y-4">
      <ul className="space-y-3">
        {data.content.map((a) => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
            <div className="min-w-0">
              {a.opportunity ? (
                <Link href={`/opportunities/${a.opportunity.id}`} className="font-medium hover:underline">{a.opportunity.title}</Link>
              ) : (
                <span className="font-medium">Opportunity</span>
              )}
              <p className="text-xs text-muted-foreground">
                <Briefcase className="mr-1 inline size-3" aria-hidden="true" />{a.opportunity?.organization} · applied {formatDate(a.createdAt)}
              </p>
            </div>
            <Badge variant={a.status === "ACCEPTED" ? "default" : "secondary"}>{APPLICATION_STATUS_LABELS[a.status]}</Badge>
          </li>
        ))}
      </ul>
      <Pager page={data.page} onChange={setPage} />
    </div>
  );
}
