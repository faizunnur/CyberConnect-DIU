"use client";

/* eslint-disable @next/next/no-img-element -- attachments are private, session-authorised backend responses;
   next/image would proxy them through the image optimiser without the member's cookies. */
import { ChevronLeft, ChevronRight, Download, FileSpreadsheet, FileText, Presentation } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { formatBytes, type AttachmentView } from "../types";

/** Photos as a social-media style grid (1, 2, 3 or 4 tiles) plus a single document card. */
export function PostAttachments({ attachments, className }: { attachments: AttachmentView[]; className?: string }) {
  const photos = attachments.filter((a) => a.kind === "IMAGE" && a.url);
  const files = attachments.filter((a) => a.kind === "FILE" && a.url);
  if (photos.length === 0 && files.length === 0) return null;
  return (
    <div className={cn("space-y-2", className)}>
      {photos.length > 0 ? <PhotoGrid photos={photos} /> : null}
      {files.map((file) => <DocumentCard key={file.id} file={file} />)}
    </div>
  );
}

function PhotoGrid({ photos }: { photos: AttachmentView[] }) {
  const [open, setOpen] = useState<number | null>(null);
  const count = photos.length;
  const single = photos[0];
  const singleRatio = single.width && single.height ? Math.min(Math.max(single.width / single.height, 0.75), 1.91) : 1.5;

  return (
    <>
      {count === 1 ? (
        <button type="button" className="block w-full overflow-hidden rounded-lg bg-muted" onClick={() => setOpen(0)} aria-label="Open photo">
          <img src={single.url!} alt="" loading="lazy" className="max-h-[560px] w-full object-cover" style={{ aspectRatio: String(singleRatio) }} />
        </button>
      ) : (
        <div className={cn("grid gap-1 overflow-hidden rounded-lg", count === 2 ? "grid-cols-2" : "grid-cols-2 grid-rows-2")} style={{ aspectRatio: count === 2 ? "2 / 1.2" : "1.4 / 1" }}>
          {photos.map((photo, i) => (
            <button
              key={photo.id}
              type="button"
              onClick={() => setOpen(i)}
              aria-label={`Open photo ${i + 1} of ${count}`}
              className={cn("relative min-h-0 overflow-hidden bg-muted", count === 3 && i === 0 && "row-span-2")}
            >
              <img src={photo.url!} alt="" loading="lazy" className="size-full object-cover transition-transform duration-300 hover:scale-[1.02]" />
            </button>
          ))}
        </div>
      )}
      <Lightbox photos={photos} index={open} onIndexChange={setOpen} />
    </>
  );
}

function Lightbox({ photos, index, onIndexChange }: {
  photos: AttachmentView[];
  index: number | null;
  onIndexChange: (index: number | null) => void;
}) {
  const current = index === null ? null : photos[index];
  const many = photos.length > 1;
  const step = (delta: number) => index !== null && onIndexChange((index + delta + photos.length) % photos.length);
  return (
    <Dialog open={current !== null} onOpenChange={(o) => !o && onIndexChange(null)}>
      <DialogContent
        className="max-w-[calc(100%-1rem)] gap-2 bg-black/95 p-2 text-white ring-0 sm:max-w-5xl"
        onKeyDown={(e) => {
          if (e.key === "ArrowRight") step(1);
          if (e.key === "ArrowLeft") step(-1);
        }}
      >
        <DialogTitle className="sr-only">Photo {index === null ? "" : `${index + 1} of ${photos.length}`}</DialogTitle>
        <DialogDescription className="sr-only">Use the arrow keys to move between photos.</DialogDescription>
        {current ? (
          <div className="relative flex items-center justify-center">
            <img src={current.url!} alt="" className="max-h-[82vh] w-auto max-w-full rounded-md object-contain" />
            {many ? (
              <>
                <button type="button" onClick={() => step(-1)} aria-label="Previous photo" className="absolute left-1 rounded-full bg-black/60 p-2 hover:bg-black/80">
                  <ChevronLeft className="size-5" aria-hidden="true" />
                </button>
                <button type="button" onClick={() => step(1)} aria-label="Next photo" className="absolute right-1 rounded-full bg-black/60 p-2 hover:bg-black/80">
                  <ChevronRight className="size-5" aria-hidden="true" />
                </button>
                <span className="absolute bottom-2 rounded-full bg-black/60 px-2 py-0.5 text-xs">{index! + 1} / {photos.length}</span>
              </>
            ) : null}
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

/** File-type icon for a document attachment. */
export function DocumentIcon({ contentType, className }: { contentType: string; className?: string }) {
  if (contentType.includes("presentation")) return <Presentation className={className} aria-hidden="true" />;
  if (contentType.includes("spreadsheet")) return <FileSpreadsheet className={className} aria-hidden="true" />;
  return <FileText className={className} aria-hidden="true" />;
}

export function documentLabel(contentType: string): string {
  if (contentType === "application/pdf") return "PDF";
  if (contentType.includes("wordprocessing")) return "Word document";
  if (contentType.includes("presentation")) return "PowerPoint";
  if (contentType.includes("spreadsheet")) return "Excel workbook";
  return "Document";
}

function DocumentCard({ file }: { file: AttachmentView }) {
  return (
    <a
      href={file.url!}
      download={file.filename ?? undefined}
      className="flex items-center gap-3 rounded-lg border bg-muted/40 p-3 transition-colors hover:bg-muted"
    >
      <span className="flex size-11 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
        <DocumentIcon contentType={file.contentType} className="size-6" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{file.filename ?? "Document"}</span>
        <span className="block text-xs text-muted-foreground">{documentLabel(file.contentType)} · {formatBytes(file.sizeBytes)}</span>
      </span>
      <Download className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span className="sr-only">Download</span>
    </a>
  );
}
