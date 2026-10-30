"use client";

import { Flag, Loader2 } from "lucide-react";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api/client";
import { useReport } from "../hooks";
import { REPORT_REASON_LABELS, type ReportReason, type ReportTargetType } from "../types";

/** Report a post, comment or member. The reporter only learns that moderators will look; nothing else. */
export function ReportButton({ targetType, targetId, label = "Report", size = "sm" }: {
  targetType: ReportTargetType;
  targetId: string;
  label?: string;
  size?: "sm" | "xs";
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("SPAM");
  const [details, setDetails] = useState("");
  const report = useReport();
  const already = isApiError(report.error, "ALREADY_REPORTED");

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size={size}
        className="text-muted-foreground"
        onClick={() => { report.reset(); setOpen(true); }}
      >
        <Flag aria-hidden="true" /> {label}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Report to moderators</DialogTitle>
            <DialogDescription>Reports are confidential. A moderator reviews the content and decides what happens.</DialogDescription>
          </DialogHeader>
          {report.isSuccess || already ? (
            <FormSuccess title="Thanks, a moderator will review this">{already ? "You had already reported it." : null}</FormSuccess>
          ) : (
            <form
              id="report-form"
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                report.mutate({ targetType, targetId, reason, details: details || undefined });
              }}
            >
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium">Reason</legend>
                {(Object.keys(REPORT_REASON_LABELS) as ReportReason[]).map((value) => (
                  <label key={value} className="flex items-center gap-2 text-sm">
                    <input type="radio" name="reason" value={value} checked={reason === value} onChange={() => setReason(value)} className="size-4 accent-primary" />
                    {REPORT_REASON_LABELS[value]}
                  </label>
                ))}
              </fieldset>
              <div className="space-y-1.5">
                <Label htmlFor="report-details">Details (optional)</Label>
                <Textarea id="report-details" rows={3} maxLength={2000} value={details} onChange={(e) => setDetails(e.target.value)} />
              </div>
              <FormError error={report.error} />
            </form>
          )}
          <DialogFooter>
            {report.isSuccess || already ? (
              <Button type="button" onClick={() => setOpen(false)}>Close</Button>
            ) : (
              <>
                <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
                <Button type="submit" form="report-form" disabled={report.isPending}>
                  {report.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Send report
                </Button>
              </>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
