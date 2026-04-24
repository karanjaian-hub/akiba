import React from 'react';
import {
  ActivityIndicator,
  Pressable,
  Text,
  ViewStyle,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useColors, useSpacing, useTypography } from '../../theme';

// ─── Types ───────────────────────────────────────────────────────────────────

type Variant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
type Size    = 'sm' | 'md' | 'lg';

type Props = {
  label:      string;
  onPress:    () => void;
  variant?:   Variant;
  size?:      Size;
  loading?:   boolean;
  disabled?:  boolean;
  fullWidth?: boolean;
  style?:     ViewStyle;
};

// ─── Size scale ──────────────────────────────────────────────────────────────

const PADDING: Record<Size, { horizontal: number; vertical: number }> = {
  sm: { horizontal: 12, vertical: 8  },
  md: { horizontal: 20, vertical: 12 },
  lg: { horizontal: 28, vertical: 16 },
};

const FONT_SIZE: Record<Size, number> = {
  sm: 13,
  md: 15,
  lg: 17,
};

// ─── Component ───────────────────────────────────────────────────────────────

export default function Button({
  label,
  onPress,
  variant   = 'primary',
  size      = 'md',
  loading   = false,
  disabled  = false,
  fullWidth = false,
  style,
}: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const scale      = useSharedValue(1);

  // Spring scale animation on press — feels physical
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = () => {
    // Don't animate if disabled or loading — no false feedback
    if (disabled || loading) return;
    scale.value = withSpring(0.95, { stiffness: 300, damping: 20 });
  };

  const handlePressOut = () => {
    scale.value = withSpring(1, { stiffness: 300, damping: 20 });
  };

  // Resolve background and text colors from variant
  const bgColor: Record<Variant, string> = {
    primary:   colors.primary,
    secondary: colors.secondary,
    outline:   'transparent',
    ghost:     'transparent',
    danger:    colors.danger,
  };

  const textColor: Record<Variant, string> = {
    primary:   '#FFFFFF',
    secondary: '#FFFFFF',
    outline:   colors.primary,
    ghost:     colors.primary,
    danger:    '#FFFFFF',
  };

  const borderColor: Record<Variant, string> = {
    primary:   'transparent',
    secondary: 'transparent',
    outline:   colors.primary,
    ghost:     'transparent',
    danger:    'transparent',
  };

  const pad = PADDING[size];

  return (
    <Animated.View style={[
      animatedStyle,
      fullWidth && { width: '100%' },
      style,
    ]}>
      <Pressable
        onPress={onPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        disabled={disabled || loading}
        style={{
          backgroundColor:  bgColor[variant],
          borderColor:      borderColor[variant],
          borderWidth:      variant === 'outline' ? 1.5 : 0,
          borderRadius:     12,
          paddingHorizontal: pad.horizontal,
          paddingVertical:  pad.vertical,
          alignItems:       'center',
          justifyContent:   'center',
          opacity:          disabled ? 0.5 : 1,
          flexDirection:    'row',
          gap:              spacing[2],
        }}
      >
        {loading ? (
          <ActivityIndicator
            size="small"
            color={textColor[variant]}
          />
        ) : (
          <Text style={{
            color:      textColor[variant],
            fontSize:   FONT_SIZE[size],
            fontWeight: typography.weight.semibold,
            textAlign:  'center',
          }}>
            {label}
          </Text>
        )}
      </Pressable>
    </Animated.View>
  );
}
