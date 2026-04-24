import React from 'react';
import { Pressable, View, ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useColors, useSpacing } from '../../theme';

// ─── Types ───────────────────────────────────────────────────────────────────

type Elevation = 'flat' | 'raised' | 'elevated';

type Props = {
  children:   React.ReactNode;
  style?:     ViewStyle;
  onPress?:   () => void;
  elevation?: Elevation;
};

// ─── Component ───────────────────────────────────────────────────────────────

export default function Card({
  children,
  style,
  onPress,
  elevation = 'raised',
}: Props) {
  const colors  = useColors();
  const spacing = useSpacing();
  const scale   = useSharedValue(1);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = () => {
    // Only animate if the card is pressable
    if (!onPress) return;
    scale.value = withSpring(0.98, { stiffness: 300, damping: 20 });
  };

  const handlePressOut = () => {
    scale.value = withSpring(1, { stiffness: 300, damping: 20 });
  };

  // Shadow depth maps to elevation prop
  const shadowStyle: Record<Elevation, ViewStyle> = {
    flat: {},
    raised: {
      shadowColor:   colors.cardShadow,
      shadowOffset:  { width: 0, height: 2 },
      shadowOpacity: 1,
      shadowRadius:  8,
      elevation:     3,
    },
    elevated: {
      shadowColor:   colors.cardShadow,
      shadowOffset:  { width: 0, height: 4 },
      shadowOpacity: 1,
      shadowRadius:  16,
      elevation:     6,
    },
  };

  const cardStyle: ViewStyle = {
    backgroundColor: colors.surface,
    borderRadius:    16,
    padding:         spacing[4],
    ...shadowStyle[elevation],
  };

  // If onPress is provided, wrap in Pressable — otherwise plain View
  if (onPress) {
    return (
      <Animated.View style={[animatedStyle, style]}>
        <Pressable
          onPress={onPress}
          onPressIn={handlePressIn}
          onPressOut={handlePressOut}
          style={cardStyle}
        >
          {children}
        </Pressable>
      </Animated.View>
    );
  }

  return (
    <View style={[cardStyle, style]}>
      {children}
    </View>
  );
}
