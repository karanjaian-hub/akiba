import React, { useState } from 'react';
import { Pressable, Text, TextInput, View, ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  FadeInDown,
} from 'react-native-reanimated';
import { useColors, useSpacing, useTypography } from '../../theme';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = {
  label:            string;
  value:            string;
  onChangeText:     (text: string) => void;
  placeholder?:     string;
  secureTextEntry?: boolean;
  error?:           string;
  keyboardType?:    'default' | 'email-address' | 'numeric' | 'phone-pad';
  multiline?:       boolean;
  disabled?:        boolean;
  style?:           ViewStyle;
};

// ─── Component ───────────────────────────────────────────────────────────────

export default function Input({
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry = false,
  error,
  keyboardType    = 'default',
  multiline       = false,
  disabled        = false,
  style,
}: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [isFocused,   setIsFocused]   = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  // Shared values drive the floating label animation
  const labelY    = useSharedValue(value ? -22 : 0);
  const labelSize = useSharedValue(value ? 12 : 15);
  const borderAnim = useSharedValue(0); // 0 = default, 1 = focused

  const handleFocus = () => {
    setIsFocused(true);
    labelY.value    = withTiming(-22, { duration: 200 });
    labelSize.value = withTiming(12,  { duration: 200 });
    borderAnim.value = withTiming(1,  { duration: 200 });
  };

  const handleBlur = () => {
    setIsFocused(false);
    // Only float the label up if there's a value — drop it back if empty
    if (!value) {
      labelY.value    = withTiming(0,  { duration: 200 });
      labelSize.value = withTiming(15, { duration: 200 });
    }
    borderAnim.value = withTiming(0, { duration: 200 });
  };

  const animatedLabel = useAnimatedStyle(() => ({
    transform:  [{ translateY: labelY.value }],
    fontSize:   labelSize.value,
  }));

  const animatedBorder = useAnimatedStyle(() => ({
    borderColor: borderAnim.value === 1
      ? colors.inputBorderActive
      : error
        ? colors.danger
        : colors.inputBorder,
  }));

  return (
    <View style={[{ width: '100%' }, style]}>
      <Animated.View style={[
        {
          backgroundColor: disabled ? colors.surfaceElevated : colors.inputBackground,
          borderWidth:     1.5,
          borderRadius:    12,
          paddingHorizontal: spacing[4],
          paddingTop:      spacing[5],
          paddingBottom:   spacing[3],
          flexDirection:   'row',
          alignItems:      'center',
        },
        animatedBorder,
      ]}>
        {/* Floating label */}
        <Animated.Text style={[
          animatedLabel,
          {
            position: 'absolute',
            left:     spacing[4],
            color:    isFocused
              ? colors.inputBorderActive
              : error
                ? colors.danger
                : colors.textSecondary,
            fontWeight: typography.weight.regular,
          },
        ]}>
          {label}
        </Animated.Text>

        {/* Text input */}
        <TextInput
          value={value}
          onChangeText={onChangeText}
          onFocus={handleFocus}
          onBlur={handleBlur}
          placeholder={isFocused ? placeholder : ''}
          placeholderTextColor={colors.textMuted}
          secureTextEntry={secureTextEntry && !showPassword}
          keyboardType={keyboardType}
          multiline={multiline}
          editable={!disabled}
          style={{
            flex:      1,
            color:     colors.textPrimary,
            fontSize:  typography.size.base,
            fontWeight: typography.weight.regular,
            paddingTop: spacing[2],
            minHeight: multiline ? 80 : undefined,
          }}
        />

        {/* Show/hide password toggle */}
        {secureTextEntry && (
          <Pressable
            onPress={() => setShowPassword((prev) => !prev)}
            style={{ padding: spacing[1] }}
          >
            <Text style={{ color: colors.textSecondary, fontSize: 12 }}>
              {showPassword ? 'HIDE' : 'SHOW'}
            </Text>
          </Pressable>
        )}
      </Animated.View>

      {/* Error message slides in from above when error prop is set */}
      {error && (
        <Animated.Text
          entering={FadeInDown.duration(200)}
          style={{
            color:     colors.danger,
            fontSize:  typography.size.sm,
            marginTop: spacing[1],
            marginLeft: spacing[2],
          }}
        >
          {error}
        </Animated.Text>
      )}
    </View>
  );
}
