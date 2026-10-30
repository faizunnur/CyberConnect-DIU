"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { communityApi } from "./api";
import type { CreatePostRequest, FeedParams, PostIntent, PostVisibility, ReportReason, ReportTargetType } from "./types";

export function useFeed(params: FeedParams) {
  return useQuery({ queryKey: ["community", "feed", params], queryFn: () => communityApi.feed(params) });
}

export function useBookmarks(page: number) {
  return useQuery({ queryKey: ["community", "bookmarks", page], queryFn: () => communityApi.bookmarks(page) });
}

export function useTrending() {
  return useQuery({ queryKey: ["community", "trending"], queryFn: communityApi.trending, staleTime: 120_000 });
}

export function usePost(id: string) {
  return useQuery({ queryKey: ["community", "post", id], queryFn: () => communityApi.post(id), retry: false });
}

export function useComments(postId: string, page: number) {
  return useQuery({ queryKey: ["community", "comments", postId, page], queryFn: () => communityApi.comments(postId, page) });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["community"] });
}

export function useCreatePost() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (body: CreatePostRequest) => communityApi.create(body), onSuccess: refresh });
}

export function useEditPost() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; intent: PostIntent; body: string; visibility: PostVisibility }) =>
      communityApi.edit(id, body),
    onSuccess: refresh,
  });
}

export function useDeletePost() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => communityApi.remove(id), onSuccess: refresh });
}

export function useToggleLike() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, liked }: { id: string; liked: boolean }) => (liked ? communityApi.unlike(id) : communityApi.like(id)),
    onSuccess: refresh,
  });
}

export function useToggleBookmark() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, bookmarked }: { id: string; bookmarked: boolean }) =>
      bookmarked ? communityApi.unbookmark(id) : communityApi.bookmark(id),
    onSuccess: refresh,
  });
}

export function useAddComment() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ postId, body, parentId }: { postId: string; body: string; parentId?: string }) =>
      communityApi.comment(postId, body, parentId),
    onSuccess: refresh,
  });
}

export function useEditComment() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: string }) => communityApi.editComment(id, body),
    onSuccess: refresh,
  });
}

export function useDeleteComment() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => communityApi.deleteComment(id), onSuccess: refresh });
}

export function useReport() {
  return useMutation({
    mutationFn: ({ targetType, targetId, reason, details }: {
      targetType: ReportTargetType;
      targetId: string;
      reason: ReportReason;
      details?: string;
    }) => communityApi.report(targetType, targetId, reason, details),
  });
}
