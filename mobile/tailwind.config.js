/** @type {import('tailwindcss').Config} */
module.exports = {
  // Tailwind scans these files for class names — anything outside this list gets purged
  content: [
    './App.{js,jsx,ts,tsx}',
    './src/**/*.{js,jsx,ts,tsx}',
  ],

  // 'class' means dark mode is toggled by adding a 'dark' class — ThemeProvider handles this
  darkMode: 'class',

  theme: {
    extend: {
      colors: {
        // Primary purple — headers, active nav, main CTAs
        primary: {
          DEFAULT: '#4C1D95', // light mode
          dark:    '#7C3AED', // dark mode
        },
        // Secondary blue — cards, secondary actions, charts
        secondary: {
          DEFAULT: '#1E40AF',
          dark:    '#3B82F6',
        },
        // Success green — income, savings, success states
        accentGreen: {
          DEFAULT: '#065F46',
          dark:    '#10B981',
        },
        // Gold — warnings, AI badge, savings bars
        gold: {
          DEFAULT: '#D97706',
          dark:    '#D97706', // same in both modes
        },
        // Screen backgrounds
        background: {
          DEFAULT: '#F9FAFB',
          dark:    '#0F0F1A',
        },
        // Cards, bottom sheets
        surface: {
          DEFAULT: '#FFFFFF',
          dark:    '#1A1A2E',
        },
        // Main body text
        textPrimary: {
          DEFAULT: '#111827',
          dark:    '#F3F4F6',
        },
        // Subtitles, captions
        textSecondary: {
          DEFAULT: '#6B7280',
          dark:    '#9CA3AF',
        },
        // Errors, debit amounts
        danger: {
          DEFAULT: '#DC2626',
          dark:    '#F87171',
        },
        // Dividers, input borders
        border: {
          DEFAULT: '#E5E7EB',
          dark:    'rgba(255,255,255,0.1)',
        },
      },
    },
  },

  plugins: [],
};
