"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ME_QUERY_KEY } from "@/features/auth/hooks";
import { verificationApi } from "./api";

export const VERIFICATION_STATUS_KEY = ["verification", "status"] as const;

export function useVerificationStatus() {
  return useQuery({ queryKey: VERIFICATION_STATUS_KEY, queryFn: verificationApi.status, staleTime: 15_000 });
}

function useInvalidateVerification() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: VERIFICATION_STATUS_KEY });
    queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
  };
}

export function useStartSubmission() {
  const invalidate = useInvalidateVerification();
  return useMutation({ mutationFn: (affiliationId: string) => verificationApi.start(affiliationId), onSuccess: invalidate });
}

export function useUploadDocument() {
  const invalidate = useInvalidateVerification();
  return useMutation({
    mutationFn: ({ submissionId, file, documentType }: { submissionId: string; file: File; documentType: string }) =>
      verificationApi.upload(submissionId, file, documentType),
    onSuccess: invalidate,
  });
}

export function useRemoveDocument() {
  const invalidate = useInvalidateVerification();
  return useMutation({
    mutationFn: ({ submissionId, documentId }: { submissionId: string; documentId: string }) =>
      verificationApi.removeDocument(submissionId, documentId),
    onSuccess: invalidate,
  });
}

export function useSubmitSubmission() {
  const invalidate = useInvalidateVerification();
  return useMutation({ mutationFn: (submissionId: string) => verificationApi.submit(submissionId), onSuccess: invalidate });
}

export function useSubmitAppeal() {
  const invalidate = useInvalidateVerification();
  return useMutation({
    mutationFn: ({ submissionId, statement }: { submissionId: string; statement: string }) =>
      verificationApi.appeal(submissionId, statement),
    onSuccess: invalidate,
  });
}
