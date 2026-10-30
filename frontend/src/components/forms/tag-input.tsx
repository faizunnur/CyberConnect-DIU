"use client";

import { Plus, X } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useSkillSuggestions } from "@/features/profile/hooks";

/** Chip list with free-text entry and catalogue suggestions (skills). Case-insensitive de-duplication. */
export function TagInput({ id, value, onChange, max = 15, placeholder = "Type a skill and press Enter" }: {
  id: string;
  value: string[];
  onChange: (next: string[]) => void;
  max?: number;
  placeholder?: string;
}) {
  const [draft, setDraft] = useState("");
  const { data: suggestions } = useSkillSuggestions(draft.trim(), draft.trim().length > 0);

  const add = (name: string) => {
    const clean = name.trim();
    if (!clean || value.length >= max) return;
    if (!value.some((v) => v.toLowerCase() === clean.toLowerCase())) onChange([...value, clean]);
    setDraft("");
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {value.map((v) => (
          <Badge key={v} variant="secondary" className="gap-1 pr-1">
            {v}
            <button type="button" className="rounded-full p-0.5 hover:bg-background/60" aria-label={`Remove ${v}`} onClick={() => onChange(value.filter((x) => x !== v))}>
              <X className="size-3" aria-hidden="true" />
            </button>
          </Badge>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          id={id}
          placeholder={placeholder}
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
      {suggestions && draft.trim() ? (
        <div className="flex flex-wrap gap-1.5">
          {suggestions.filter((s) => !value.some((v) => v.toLowerCase() === s.name.toLowerCase())).slice(0, 8).map((s) => (
            <button key={s.id} type="button" className="rounded-full border px-2.5 py-0.5 text-xs hover:bg-accent" onClick={() => add(s.name)}>
              + {s.name}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
