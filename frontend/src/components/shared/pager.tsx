import { Button } from "@/components/ui/button";
import type { Page } from "@/lib/api/types";

export function Pager<T>({ page, onChange }: { page: Page<T>["page"]; onChange: (next: number) => void }) {
  if (page.totalPages <= 1) return null;
  return (
    <nav className="flex items-center justify-between text-sm" aria-label="Pagination">
      <span className="text-muted-foreground">
        Page {page.number + 1} of {page.totalPages} · {page.totalElements} total
      </span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page.number === 0} onClick={() => onChange(page.number - 1)}>
          Previous
        </Button>
        <Button variant="outline" size="sm" disabled={page.number + 1 >= page.totalPages} onClick={() => onChange(page.number + 1)}>
          Next
        </Button>
      </div>
    </nav>
  );
}

export function EmptyState({ title, text }: { title: string; text?: string }) {
  return (
    <div className="rounded-lg border border-dashed p-8 text-center">
      <p className="font-medium">{title}</p>
      {text ? <p className="mt-1 text-sm text-muted-foreground">{text}</p> : null}
    </div>
  );
}
