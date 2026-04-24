import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useAuthStore } from '../store/auth.store';

// ─── Axios Instance ───────────────────────────────────────────────────────────

// Base URL comes from env — falls back to localhost for local development
const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// ─── Request Interceptor ──────────────────────────────────────────────────────
// Attaches Bearer token to every outgoing request automatically

apiClient.interceptors.request.use(
  async (config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ─── Response Interceptor ─────────────────────────────────────────────────────
// Handles 401s by attempting a token refresh before giving up

let isRefreshing = false;

apiClient.interceptors.response.use(
  // Pass successful responses straight through
  (response) => response,

  async (error) => {
    const originalRequest = error.config;

    // Only attempt refresh on 401 and only once per request
    if (error.response?.status === 401 && !originalRequest._retry) {
      // Guard against multiple simultaneous refresh attempts
      if (isRefreshing) return Promise.reject(error);

      isRefreshing        = true;
      originalRequest._retry = true;

      try {
        const refreshToken = await AsyncStorage.getItem('@akiba_refresh_token');
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await apiClient.post('/auth/refresh', { refreshToken });

        // Update token in store and retry the original request
        useAuthStore.getState().setToken(data.accessToken);
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(originalRequest);

      } catch {
        // Refresh failed — log the user out and redirect to Login
        useAuthStore.getState().logout();
        return Promise.reject(error);

      } finally {
        isRefreshing = false;
      }
    }

    // Extract a clean error message from the response body if available
    const message =
      error.response?.data?.message ??
      error.response?.data?.error ??
      error.message ??
      'Something went wrong';

    return Promise.reject(new Error(message));
  },
);

// ─── Typed Wrappers ───────────────────────────────────────────────────────────
// Use these instead of apiClient.get/post directly — they handle errors uniformly

export async function apiGet<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await apiClient.get(url, config);
  return response.data;
}

export async function apiPost<T>(
  url:  string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await apiClient.post(url, data, config);
  return response.data;
}

export async function apiPut<T>(
  url:  string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await apiClient.put(url, data, config);
  return response.data;
}

export async function apiDelete<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response: AxiosResponse<T> = await apiClient.delete(url, config);
  return response.data;
}
