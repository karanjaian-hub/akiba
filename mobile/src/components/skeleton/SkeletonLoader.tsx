import React, { useEffect } from 'react';
import { View, ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useColors, useSpacing } from '../../theme';

// ─── Base Shimmer ─────────────────────────────────────────────────────────────

type SkeletonProps = {
  width:         number | string;
  height:        number;
  borderRadius?: number;
  style?:        ViewStyle;
};

export function SkeletonLoader({
  width,
  height,
  borderRadius = 8,
  style,
}: SkeletonProps): React.ReactElement {
  const colors  = useColors();
  const opacity = useSharedValue(1);

  useEffect(() => {
    opacity.value = withRepeat(
      withSequence(
        withTiming(0.4, { duration: 800 }),
        withTiming(1.0, { duration: 800 }),
      ),
      -1,
      true,
    );
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  return (
    <Animated.View
      style={[
        animatedStyle,
        {
          width:           width as any,
          height,
          borderRadius,
          backgroundColor: colors.surfaceElevated,
        },
        style,
      ]}
    />
  );
}

// ─── Composed Skeletons ───────────────────────────────────────────────────────

export function SkeletonCard({ style }: { style?: ViewStyle }): React.ReactElement {
  const colors  = useColors();
  const spacing = useSpacing();

  return (
    <View style={[{
      backgroundColor: colors.surface,
      borderRadius:    16,
      padding:         spacing[4],
      gap:             spacing[3],
    }, style]}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[3] }}>
        <SkeletonLoader width={48} height={48} borderRadius={24} />
        <View style={{ gap: spacing[2], flex: 1 }}>
          <SkeletonLoader width="70%" height={14} />
          <SkeletonLoader width="40%" height={12} />
        </View>
      </View>
      <SkeletonLoader width="100%" height={12} />
      <SkeletonLoader width="80%"  height={12} />
    </View>
  );
}

export function SkeletonText({
  width = '100%',
  style,
}: {
  width?: number | string;
  style?: ViewStyle;
}): React.ReactElement {
  return (
    <SkeletonLoader width={width} height={14} borderRadius={6} style={style} />
  );
}

export function SkeletonAvatar({
  size = 48,
  style,
}: {
  size?:  number;
  style?: ViewStyle;
}): React.ReactElement {
  return (
    <SkeletonLoader width={size} height={size} borderRadius={size / 2} style={style} />
  );
}
