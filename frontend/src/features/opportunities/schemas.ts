import { z } from "zod";

const text = (max: number) => z.string().trim().max(max, `Use at most ${max} characters`);

export const opportunitySchema = z
  .object({
    type: z.enum(["FULL_TIME", "PART_TIME", "INTERNSHIP", "RESEARCH_ASSISTANT", "FREELANCE", "SCHOLARSHIP"]),
    title: z.string().trim().min(3, "Enter a title").max(160),
    organization: z.string().trim().min(2, "Enter the organisation").max(160),
    locationType: z.enum(["ONSITE", "REMOTE", "HYBRID"]),
    location: text(160),
    description: z.string().trim().min(20, "Describe the opportunity (at least 20 characters)").max(10_000),
    compensation: text(160),
    deadline: z.string(),
    applicationMethod: z.enum(["INTERNAL", "EXTERNAL_URL", "EMAIL"]),
    applicationUrl: text(500),
    applicationEmail: text(254),
  })
  .refine((v) => v.applicationMethod !== "EXTERNAL_URL" || /^https?:\/\/.+/i.test(v.applicationUrl), {
    message: "Enter an http(s) link",
    path: ["applicationUrl"],
  })
  .refine((v) => v.applicationMethod !== "EMAIL" || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.applicationEmail), {
    message: "Enter a valid email address",
    path: ["applicationEmail"],
  });
export type OpportunityFormValues = z.infer<typeof opportunitySchema>;

export const researchSchema = z
  .object({
    title: z.string().trim().min(3, "Enter a title").max(200),
    summary: z.string().trim().min(20, "Summarise the project (at least 20 characters)").max(10_000),
    lookingForMembers: z.boolean(),
    startDate: z.string(),
    endDate: z.string(),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { message: "End date is before the start date", path: ["endDate"] });
export type ResearchFormValues = z.infer<typeof researchSchema>;
