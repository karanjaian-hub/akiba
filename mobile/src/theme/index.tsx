import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { lightColors, darkColors, AppColors } from './colors';
import { typography, AppTypography } from './typography';
import { spacing, AppSpacing } from './spacing';

// ─── Types ───────────────────────────────────────────────────────────────────

export type ColorScheme = 'light' | 'dark' | 'auto';

export type Theme = {
  colors:     AppColors;
  typography: AppTypography;
  spacing:    AppSpacing;
  isDark:     boolean;
  scheme:     ColorScheme;
  setScheme:  (scheme: ColorScheme) => void;
};

// ─── Context ─────────────────────────────────────────────────────────────────

const ThemeContext = createContext<Theme | null>(null);

const STORAGE_KEY = '@akiba_theme';

// ─── Provider ────────────────────────────────────────────────────────────────

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme(); // 'light' | 'dark' | null
  const [scheme, setSchemeState] = useState<ColorScheme>('auto');

  // Restore saved preference on app start
  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((saved) => {
      if (saved === 'light' || saved === 'dark' || saved === 'auto') {
        setSchemeState(saved);
      }
    });
  }, []);

  const setScheme = (next: ColorScheme) => {
    setSchemeState(next);
    AsyncStorage.setItem(STORAGE_KEY, next);
  };

  // 'auto' defers to the system setting; explicit choice overrides it
  const isDark =
    scheme === 'dark' || (scheme === 'auto' && systemScheme === 'dark');

  const theme: Theme = {
    colors:     isDark ? darkColors : lightColors,
    typography,
    spacing,
    isDark,
    scheme,
    setScheme,
  };

  return (
    <ThemeContext.Provider value={theme}>
      {children}
    </ThemeContext.Provider>
  );
}

// ─── Hooks ───────────────────────────────────────────────────────────────────

// Main hook — gives you the full theme object
export function useTheme(): Theme {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}

// Convenience hooks — import only what you need
export const useColors     = () => useTheme().colors;
export const useTypography = () => useTheme().typography;
export const useSpacing    = () => useTheme().spacing;
