import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type {
  DirectoryParams,
  EducationRequest,
  EducationView,
  ExperienceRequest,
  ExperienceView,
  MemberCard,
  PrivacySettings,
  ProfileView,
  SkillView,
  UpdateProfileRequest,
} from "./types";

export function toQuery(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === "" || value === null) continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const profileApi = {
  me: () => apiFetch<ProfileView>("/me/profile"),
  update: (body: UpdateProfileRequest) => apiFetch<ProfileView>("/me/profile", { method: "PUT", json: body }),
  privacy: () => apiFetch<PrivacySettings>("/me/profile/privacy"),
  updatePrivacy: (body: PrivacySettings) =>
    apiFetch<PrivacySettings>("/me/profile/privacy", { method: "PUT", json: body }),
  replaceSkills: (skills: string[]) =>
    apiFetch<SkillView[]>("/me/profile/skills", { method: "PUT", json: { skills } }),
  addExperience: (body: ExperienceRequest) =>
    apiFetch<ExperienceView>("/me/profile/experiences", { method: "POST", json: body }),
  updateExperience: (id: string, body: ExperienceRequest) =>
    apiFetch<ExperienceView>(`/me/profile/experiences/${id}`, { method: "PUT", json: body }),
  deleteExperience: (id: string) => apiFetch<void>(`/me/profile/experiences/${id}`, { method: "DELETE" }),
  addEducation: (body: EducationRequest) =>
    apiFetch<EducationView>("/me/profile/educations", { method: "POST", json: body }),
  updateEducation: (id: string, body: EducationRequest) =>
    apiFetch<EducationView>(`/me/profile/educations/${id}`, { method: "PUT", json: body }),
  deleteEducation: (id: string) => apiFetch<void>(`/me/profile/educations/${id}`, { method: "DELETE" }),
  uploadPhoto: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiFetch<ProfileView>("/me/profile/photo", { method: "POST", body: form });
  },
  removePhoto: () => apiFetch<void>("/me/profile/photo", { method: "DELETE" }),
  member: (userId: string) => apiFetch<ProfileView>(`/members/${userId}`),
  directory: (params: DirectoryParams) => apiFetch<Page<MemberCard>>(`/members${toQuery({ ...params })}`),
  skills: (q: string) => apiFetch<SkillView[]>(`/skills${toQuery({ q })}`),
};
