import { apiFetch } from "@/lib/api/client";
import type { AppealView, DocumentView, StatusView, SubmissionView } from "./types";

export const verificationApi = {
  status: () => apiFetch<StatusView>("/verification/status"),
  start: (affiliationId: string) =>
    apiFetch<SubmissionView>("/verification/submissions", { method: "POST", json: { affiliationId } }),
  upload: (submissionId: string, file: File, documentType: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("documentType", documentType);
    return apiFetch<DocumentView>(`/verification/submissions/${submissionId}/documents`, { method: "POST", body: form });
  },
  removeDocument: (submissionId: string, documentId: string) =>
    apiFetch<void>(`/verification/submissions/${submissionId}/documents/${documentId}`, { method: "DELETE" }),
  submit: (submissionId: string) =>
    apiFetch<SubmissionView>(`/verification/submissions/${submissionId}/submit`, { method: "POST" }),
  appeal: (submissionId: string, statement: string) =>
    apiFetch<AppealView>("/verification/appeals", { method: "POST", json: { submissionId, statement } }),
};
