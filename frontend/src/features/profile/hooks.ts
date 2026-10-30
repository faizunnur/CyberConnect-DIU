"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { profileApi } from "./api";
import type { DirectoryParams, EducationRequest, ExperienceRequest, PrivacySettings, UpdateProfileRequest } from "./types";

export const MY_PROFILE_KEY = ["profile", "me"] as const;

export function useMyProfile() {
  return useQuery({ queryKey: MY_PROFILE_KEY, queryFn: profileApi.me });
}

export function useMemberProfile(userId: string) {
  return useQuery({ queryKey: ["members", "profile", userId], queryFn: () => profileApi.member(userId), retry: false });
}

export function useDirectory(params: DirectoryParams) {
  return useQuery({ queryKey: ["members", "directory", params], queryFn: () => profileApi.directory(params) });
}

export function useSkillSuggestions(q: string, enabled: boolean) {
  return useQuery({ queryKey: ["skills", q], queryFn: () => profileApi.skills(q), enabled, staleTime: 60_000 });
}

function useRefreshProfile() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ["profile"] });
    queryClient.invalidateQueries({ queryKey: ["members"] });
    queryClient.invalidateQueries({ queryKey: ["network"] });
  };
}

export function useUpdateProfile() {
  const refresh = useRefreshProfile();
  return useMutation({ mutationFn: (body: UpdateProfileRequest) => profileApi.update(body), onSuccess: refresh });
}

export function useUpdatePrivacy() {
  const refresh = useRefreshProfile();
  return useMutation({ mutationFn: (body: PrivacySettings) => profileApi.updatePrivacy(body), onSuccess: refresh });
}

export function useReplaceSkills() {
  const refresh = useRefreshProfile();
  return useMutation({ mutationFn: (skills: string[]) => profileApi.replaceSkills(skills), onSuccess: refresh });
}

export function useExperienceMutations() {
  const refresh = useRefreshProfile();
  const add = useMutation({ mutationFn: (body: ExperienceRequest) => profileApi.addExperience(body), onSuccess: refresh });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ExperienceRequest }) => profileApi.updateExperience(id, body),
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: (id: string) => profileApi.deleteExperience(id), onSuccess: refresh });
  return { add, update, remove };
}

export function useEducationMutations() {
  const refresh = useRefreshProfile();
  const add = useMutation({ mutationFn: (body: EducationRequest) => profileApi.addEducation(body), onSuccess: refresh });
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: EducationRequest }) => profileApi.updateEducation(id, body),
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: (id: string) => profileApi.deleteEducation(id), onSuccess: refresh });
  return { add, update, remove };
}

export function usePhotoMutations() {
  const refresh = useRefreshProfile();
  const upload = useMutation({ mutationFn: (file: File) => profileApi.uploadPhoto(file), onSuccess: refresh });
  const remove = useMutation({ mutationFn: () => profileApi.removePhoto(), onSuccess: refresh });
  return { upload, remove };
}
