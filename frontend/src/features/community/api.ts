import { toQuery } from "@/features/profile/api";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type {
  AttachmentView,
  CommentView,
  CreatePostRequest,
  FeedParams,
  PostIntent,
  PostView,
  PostVisibility,
  ReportReason,
  ReportTargetType,
  TrendingTag,
} from "./types";

export const communityApi = {
  feed: (params: FeedParams) => apiFetch<Page<PostView>>(`/feed${toQuery({ ...params })}`),
  bookmarks: (page = 0) => apiFetch<Page<PostView>>(`/bookmarks${toQuery({ page, size: 20 })}`),
  trending: () => apiFetch<TrendingTag[]>("/hashtags/trending"),
  post: (id: string) => apiFetch<PostView>(`/posts/${id}`),
  uploadAttachment: (file: File) => {
    const form = new FormData();
    form.append("file", file, file.name);
    return apiFetch<AttachmentView>("/posts/attachments", { method: "POST", body: form });
  },
  discardAttachment: (id: string) => apiFetch<void>(`/posts/attachments/${id}`, { method: "DELETE" }),
  create: (body: CreatePostRequest) => apiFetch<PostView>("/posts", { method: "POST", json: body }),
  edit: (id: string, body: { intent: PostIntent; body: string; visibility: PostVisibility }) =>
    apiFetch<PostView>(`/posts/${id}`, { method: "PUT", json: body }),
  remove: (id: string) => apiFetch<void>(`/posts/${id}`, { method: "DELETE" }),
  like: (id: string) => apiFetch<PostView>(`/posts/${id}/like`, { method: "POST" }),
  unlike: (id: string) => apiFetch<PostView>(`/posts/${id}/like`, { method: "DELETE" }),
  bookmark: (id: string) => apiFetch<PostView>(`/posts/${id}/bookmark`, { method: "POST" }),
  unbookmark: (id: string) => apiFetch<PostView>(`/posts/${id}/bookmark`, { method: "DELETE" }),
  comments: (postId: string, page = 0) =>
    apiFetch<Page<CommentView>>(`/posts/${postId}/comments${toQuery({ page, size: 50 })}`),
  comment: (postId: string, body: string, parentId?: string) =>
    apiFetch<CommentView>(`/posts/${postId}/comments`, { method: "POST", json: { body, parentId } }),
  editComment: (id: string, body: string) => apiFetch<CommentView>(`/comments/${id}`, { method: "PUT", json: { body } }),
  deleteComment: (id: string) => apiFetch<void>(`/comments/${id}`, { method: "DELETE" }),
  report: (targetType: ReportTargetType, targetId: string, reason: ReportReason, details?: string) =>
    apiFetch<{ id: string }>("/reports", { method: "POST", json: { targetType, targetId, reason, details } }),
};
