import { apiGet, apiPost, apiPut } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type UserProfile = {
  id:                string;
  fullName:          string;
  email:             string;
  phone:             string;
  role:              string;
  profilePictureUrl: string | null;
  createdAt:         string;
};

export type LoginResponse = {
  accessToken:  string;
  refreshToken: string;
  user:         UserProfile;
};

export type OtpType = 'EMAIL_VERIFICATION' | 'PASSWORD_RESET';

// Step types for the 4-step registration wizard
export type RegisterRequest = {
  // Step 1 — required
  fullName:        string;
  email:           string;
  phone:           string;
  password:        string;
  confirmPassword: string;

  // Step 2 — optional
  incomeRange?:     string;
  employmentType?:  string;
  primaryGoal?:     string;

  // Step 3 — optional
  budgets?: {
    category:     string;
    monthlyLimit: number;
  }[];

  // Step 4 — optional
  bankName?:       string;
  accountNickname?: string;
  accountType?:    string;
};

// ─── Auth Service ─────────────────────────────────────────────────────────────

export const authService = {

  register: (data: RegisterRequest) =>
    apiPost<{ message: string }>('/auth/register', data),

  verifyEmailOtp: (data: {
    email: string;
    otp:   string;
    type:  OtpType;
  }) => apiPost<{ message: string }>('/auth/verify-email', data),

  login: (data: {
    email:    string;
    password: string;
  }) => apiPost<LoginResponse>('/auth/login', data),

  refreshToken: (refreshToken: string) =>
    apiPost<{ accessToken: string }>('/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    apiPost<{ message: string }>('/auth/logout', { refreshToken }),

  forgotPassword: (email: string) =>
    apiPost<{ message: string }>('/auth/forgot-password', { email }),

  resetPassword: (data: {
    email:           string;
    otp:             string;
    newPassword:     string;
    confirmPassword: string;
  }) => apiPost<{ message: string }>('/auth/reset-password', data),

  getProfile: () =>
    apiGet<UserProfile>('/auth/profile'),

  updateProfile: (data: Partial<UserProfile>) =>
    apiPut<UserProfile>('/auth/profile', data),

  uploadProfilePicture: async (uri: string) => {
    // Build multipart form — uri comes from expo-image-picker
    const formData = new FormData();
    formData.append('file', {
      uri,
      type: 'image/jpeg',
      name: 'profile.jpg',
    } as any);

    return apiPost<{ profilePictureUrl: string }>(
      '/auth/upload-picture',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
  },
};
