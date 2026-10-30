"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { networkingApi } from "./api";

export function useConnections(page: number) {
  return useQuery({ queryKey: ["network", "connections", page], queryFn: () => networkingApi.connections(page) });
}

export function useNetworkSummary() {
  return useQuery({ queryKey: ["network", "summary"], queryFn: networkingApi.summary, staleTime: 30_000 });
}

export function useRequests(direction: "incoming" | "outgoing", page: number) {
  return useQuery({
    queryKey: ["network", "requests", direction, page],
    queryFn: () => networkingApi.requests(direction, page),
  });
}

export function useBlocks() {
  return useQuery({ queryKey: ["network", "blocks"], queryFn: networkingApi.blocks });
}

function useRefreshNetwork() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ["network"] });
    queryClient.invalidateQueries({ queryKey: ["members"] });
  };
}

export function useSendRequest() {
  const refresh = useRefreshNetwork();
  return useMutation({
    mutationFn: ({ userId, message }: { userId: string; message?: string }) => networkingApi.request(userId, message),
    onSuccess: refresh,
  });
}

export function useRespondToRequest(kind: "accept" | "decline" | "cancel") {
  const refresh = useRefreshNetwork();
  return useMutation({ mutationFn: (requestId: string) => networkingApi[kind](requestId), onSuccess: refresh });
}

export function useRemoveConnection() {
  const refresh = useRefreshNetwork();
  return useMutation({ mutationFn: (userId: string) => networkingApi.remove(userId), onSuccess: refresh });
}

export function useBlockMember() {
  const refresh = useRefreshNetwork();
  return useMutation({ mutationFn: (userId: string) => networkingApi.block(userId), onSuccess: refresh });
}

export function useUnblockMember() {
  const refresh = useRefreshNetwork();
  return useMutation({ mutationFn: (userId: string) => networkingApi.unblock(userId), onSuccess: refresh });
}
