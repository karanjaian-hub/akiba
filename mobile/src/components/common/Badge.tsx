import React from 'react';
import { Text, View } from 'react-native';
import { useColors, useTypography } from '../../theme';

// ─── Types ───────────────────────────────────────────────────────────────────

type Variant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';
type Size    = 'sm' | 'md';

type Props = {
  label:    string;
  variant?: Variant;
  size?:    Size;
};

// ─── Component ───────────────────────────────────────────────────────────────

export default function Badge({
  label,
  variant = 'neutral',
  size    = 'md',
}: Props) {
  const colors     = useColors();
  const typography = useTypography();

  // Background and text color pairs per variant
  const variantColors: Record<Variant, { bg: string; text: string }> = {
    success: { bg: colors.accentGreen + '20', text: colors.accentGreen },
    warning: { bg: colors.gold      + '20', text: colors.gold       },
    danger:  { bg: colors.danger    + '20', text: colors.danger     },
    info:    { bg: colors.secondary + '20', text: colors.secondary  },
    neutral: { bg: colors.border,           text: colors.textSecondary },
  };

  const { bg, text } = variantColors[variant];

  return (
    <View style={{
      backgroundColor:  bg,
      borderRadius:     999, // pill shape
      paddingHorizontal: size === 'sm' ? 8 : 12,
      paddingVertical:  size === 'sm' ? 2 : 4,
      alignSelf:        'flex-start',
    }}>
      <Text style={{
        color:      text,
        fontSize:   size === 'sm'
          ? typography.size.xs
          : typography.size.sm,
        fontWeight: typography.weight.semibold,
      }}>
        {label}
      </Text>
    </View>
  );
}
