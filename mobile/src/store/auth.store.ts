import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';

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

type AuthState = {
  user:            UserProfile | null;
  accessToken:     string | null;
  isAuthenticated: boolean;

  // Actions
  login:      (token: string, user: UserProfile) => void;
  logout:     () => void;
  updateUser: (partial: Partial<UserProfile>) => void;
  setToken:   (token: string) => void;
};

// ─── Store ────────────────────────────────────────────────────────────────────

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user:            null,
      accessToken:     null,
      isAuthenticated: false,

      login: (token, user) => {
        // Store token in AsyncStorage via persist middleware + update state
        AsyncStorage.setItem('@akiba_access_token', token);
        set({ accessToken: token, user, isAuthenticated: true });
      },

      logout: () => {
        // Clear everything — middleware handles AsyncStorage cleanup
        AsyncStorage.multiRemove([
          '@akiba_access_token',
          '@akiba_refresh_token',
        ]);
        set({ accessToken: null, user: null, isAuthenticated: false });
      },

      updateUser: (partial) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...partial } : null,
        })),

      setToken: (token) => {
        AsyncStorage.setItem('@akiba_access_token', token);
        set({ accessToken: token });
      },
    }),
    {
      name:    'akiba-auth',
      storage: createJSONStorage(() => AsyncStorage),
      // Only persist these fields — never persist actions
      partialize: (state) => ({
        user:            state.user,
        accessToken:     state.accessToken,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
