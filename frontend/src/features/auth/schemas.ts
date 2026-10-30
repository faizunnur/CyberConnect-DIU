import { z } from "zod";

export const emailSchema = z.string().trim().toLowerCase().email("Enter a valid email address").max(254);

export const passwordSchema = z
  .string()
  .min(12, "Use at least 12 characters")
  .max(128, "Use at most 128 characters")
  .refine((value) => !/^(.)\1+$/.test(value), "Choose a less predictable password");

export const currentYear = new Date().getFullYear();

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Enter your full name").max(120),
    email: emailSchema,
    password: passwordSchema,
    confirmPassword: z.string(),
    affiliationType: z.enum(["STUDENT", "ALUMNI", "CSC_MEMBER"], { message: "Choose your affiliation" }),
    studentId: z.string().trim().max(32).optional(),
    batch: z.string().trim().max(16).optional(),
    department: z.string().trim().max(120).optional(),
    graduationYear: z.string().trim().max(4).optional(),
    cscMemberId: z.string().trim().max(32).optional(),
    privacyAccepted: z.literal(true, { message: "You must accept the privacy notice" }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  })
  .refine((data) => data.password.toLowerCase() !== data.email.toLowerCase(), {
    message: "Password must differ from your email",
    path: ["password"],
  })
  .superRefine((data, ctx) => {
    const required = (field: "studentId" | "batch" | "department" | "graduationYear" | "cscMemberId", label: string) => {
      const value = data[field];
      if (value === undefined || value === null || value === "") {
        ctx.addIssue({ code: "custom", path: [field], message: `${label} is required` });
      }
    };
    if (data.affiliationType === "STUDENT") {
      required("studentId", "Student ID");
      required("batch", "Batch");
      required("department", "Department");
    } else if (data.affiliationType === "ALUMNI") {
      required("studentId", "Student ID");
      required("graduationYear", "Graduation year");
      if (data.graduationYear && !/^(19|20)\d{2}$/.test(data.graduationYear)) {
        ctx.addIssue({ code: "custom", path: ["graduationYear"], message: "Enter a four-digit year" });
      } else if (data.graduationYear && Number(data.graduationYear) > currentYear) {
        ctx.addIssue({ code: "custom", path: ["graduationYear"], message: "Graduation year cannot be in the future" });
      }
    } else if (data.affiliationType === "CSC_MEMBER") {
      required("cscMemberId", "CSC member ID");
    }
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;

export const signInSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Enter your password"),
});
export type SignInFormValues = z.infer<typeof signInSchema>;

export const mfaCodeSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[0-9]{6}$|^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$/, "Enter the 6-digit code or a recovery code"),
});
export type MfaCodeFormValues = z.infer<typeof mfaCodeSchema>;

export const forgotPasswordSchema = z.object({ email: emailSchema });
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({ newPassword: passwordSchema, confirmPassword: z.string() })
  .refine((d) => d.newPassword === d.confirmPassword, { message: "Passwords do not match", path: ["confirmPassword"] });
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({ currentPassword: z.string().min(1, "Enter your current password"), newPassword: passwordSchema, confirmPassword: z.string() })
  .refine((d) => d.newPassword === d.confirmPassword, { message: "Passwords do not match", path: ["confirmPassword"] });
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
