import { z } from "zod";

const optionalText = (max: number) => z.string().trim().max(max, `Use at most ${max} characters`);

export const profileSchema = z.object({
  displayName: z.string().trim().min(2, "Enter the name members should see").max(120),
  headline: optionalText(160),
  bio: optionalText(4000),
  organization: optionalText(160),
  jobTitle: optionalText(120),
  location: optionalText(120),
  country: optionalText(80),
  /** Comma-separated in the form; split into a list before sending. */
  specializations: optionalText(700),
  mentorAvailable: z.boolean(),
  mentorTopics: optionalText(500),
});
export type ProfileFormValues = z.infer<typeof profileSchema>;

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "INTERNSHIP", "FREELANCE", "RESEARCH", "VOLUNTEER"] as const;

export const experienceSchema = z
  .object({
    title: z.string().trim().min(1, "Enter a title").max(120),
    organization: z.string().trim().min(1, "Enter the organisation").max(160),
    employmentType: z.enum(EMPLOYMENT_TYPES).or(z.literal("")),
    location: optionalText(120),
    startDate: z.string().min(1, "Enter a start date"),
    endDate: z.string(),
    current: z.boolean(),
    description: optionalText(2000),
  })
  .refine((v) => v.current || v.endDate.length > 0, { message: "Add an end date or mark the role as current", path: ["endDate"] })
  .refine((v) => v.current || !v.endDate || v.endDate >= v.startDate, { message: "End date is before the start date", path: ["endDate"] });
export type ExperienceFormValues = z.infer<typeof experienceSchema>;

const yearField = z
  .string()
  .trim()
  .max(4)
  .refine((v) => v === "" || /^(19[5-9]\d|20\d\d|2100)$/.test(v), "Enter a year between 1950 and 2100");

export const educationSchema = z
  .object({
    institution: z.string().trim().min(1, "Enter the institution").max(160),
    degree: optionalText(120),
    fieldOfStudy: optionalText(120),
    startYear: yearField,
    endYear: yearField,
    description: optionalText(2000),
  })
  .refine((v) => !v.startYear || !v.endYear || Number(v.endYear) >= Number(v.startYear), {
    message: "End year is before the start year",
    path: ["endYear"],
  });
export type EducationFormValues = z.infer<typeof educationSchema>;

export function splitList(text: string): string[] {
  return text
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}
