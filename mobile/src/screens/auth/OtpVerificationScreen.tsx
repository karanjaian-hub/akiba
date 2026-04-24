import React, { useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { StatusBar } from 'expo-status-bar';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import AkibaLogo from '../../components/common/AkibaLogo';
import { authService } from '../../services/auth.service';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'OtpVerification'>;

const OTP_LENGTH = 6;

// ─── Masked Email ─────────────────────────────────────────────────────────────
// Turns 'karanja@gmail.com' into 'k*****@gmail.com'

function maskEmail(email: string): string {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  return `${local[0]}${'*'.repeat(Math.max(local.length - 1, 3))}@${domain}`;
}

// ─── Single OTP Box ───────────────────────────────────────────────────────────

function OtpBox({
  value,
  isFocused,
  shakeStyle,
  inputRef,
  onChangeText,
  onKeyPress,
  onFocus,
}: {
  value:        string;
  isFocused:    boolean;
  shakeStyle:   any;
  inputRef:     React.RefObject<TextInput | null>;
  onChangeText: (text: string) => void;
  onKeyPress:   (e: any) => void;
  onFocus:      () => void;
}) {
  const colors  = useColors();
  const spacing = useSpacing();

  return (
    <Animated.View style={[
      shakeStyle,
      {
        width:           48,
        height:          56,
        borderRadius:    12,
        borderWidth:     1.5,
        borderColor:     isFocused ? colors.primary : colors.border,
        backgroundColor: colors.inputBackground,
        alignItems:      'center',
        justifyContent:  'center',
      },
    ]}>
      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={onChangeText}
        onKeyPress={onKeyPress}
        onFocus={onFocus}
        keyboardType="numeric"
        maxLength={1}
        style={{
          width:     '100%',
          height:    '100%',
          textAlign: 'center',
          fontSize:  22,
          fontWeight: '700',
          color:     colors.textPrimary,
        }}
      />
    </Animated.View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function OtpVerificationScreen({ route, navigation }: Props) {
  const { email, type } = route.params;
  const colors          = useColors();
  const spacing         = useSpacing();
  const typography      = useTypography();

  const [digits,    setDigits]    = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [focused,   setFocused]   = useState(0);
  const [loading,   setLoading]   = useState(false);
  const [apiError,  setApiError]  = useState<string | undefined>();
  const [countdown, setCountdown] = useState(60);
  const [canResend, setCanResend] = useState(false);

  // One ref per OTP box for focus management
  const inputRefs = useRef<React.RefObject<TextInput | null>[]>(
      Array.from({ length: OTP_LENGTH }, () => React.createRef<TextInput | null>())
  );

  // Shake animation shared value — all boxes share the same shake
  const shakeX = useSharedValue(0);

  const shakeStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: shakeX.value }],
  }));

  // ── Countdown timer ────────────────────────────────────────────────────────

  React.useEffect(() => {
    if (canResend) return;
    if (countdown === 0) {
      setCanResend(true);
      return;
    }
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown, canResend]);

  // ── OTP submission ─────────────────────────────────────────────────────────

  const handleVerify = async (otp: string) => {
    setApiError(undefined);
    setLoading(true);

    try {
      await authService.verifyEmailOtp({ email, otp, type:
        type === 'verify' ? 'EMAIL_VERIFICATION' : 'PASSWORD_RESET',
      });

      if (type === 'verify') {
        navigation.replace('Login');
      } else {
        navigation.replace('ResetPassword', { email, otp });
      }
    } catch (err: any) {
      setApiError(err.message ?? 'Invalid code. Please try again.');

      // Shake all boxes on error
      shakeX.value = withSequence(
        withTiming(-8, { duration: 60 }),
        withTiming( 8, { duration: 60 }),
        withTiming(-8, { duration: 60 }),
        withTiming( 8, { duration: 60 }),
        withTiming( 0, { duration: 60 }),
      );

      // Clear all boxes after shake
      setDigits(Array(OTP_LENGTH).fill(''));
      inputRefs.current[0].current?.focus();
    } finally {
      setLoading(false);
    }
  };

  // ── Digit entry ────────────────────────────────────────────────────────────

  const handleChangeText = (text: string, index: number) => {
    const newDigits = [...digits];

    // Handle paste — if user pastes 6 chars into any box
    if (text.length === OTP_LENGTH) {
      const pasted = text.slice(0, OTP_LENGTH).split('');
      setDigits(pasted);
      inputRefs.current[OTP_LENGTH - 1].current?.focus();

      // Auto-submit after 300ms debounce
      setTimeout(() => handleVerify(pasted.join('')), 300);
      return;
    }

    newDigits[index] = text;
    setDigits(newDigits);

    // Advance focus to next box on digit entry
    if (text && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1].current?.focus();
    }

    // Auto-submit when all 6 digits are filled
    const otp = newDigits.join('');
    if (otp.length === OTP_LENGTH && !newDigits.includes('')) {
      setTimeout(() => handleVerify(otp), 300);
    }
  };

  const handleKeyPress = (e: any, index: number) => {
    // Move focus back on backspace if current box is empty
    if (e.nativeEvent.key === 'Backspace' && !digits[index] && index > 0) {
      inputRefs.current[index - 1].current?.focus();
    }
  };

  // ── Resend ─────────────────────────────────────────────────────────────────

  const handleResend = async () => {
    if (!canResend) return;
    try {
      await authService.forgotPassword(email);
      setCountdown(60);
      setCanResend(false);
    } catch (err: any) {
      setApiError(err.message ?? 'Failed to resend. Try again.');
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar style="auto" />
      <View style={{
        flex:              1,
        paddingHorizontal: spacing[6],
        paddingTop:        80,
        gap:               spacing[6],
      }}>
        {/* Logo */}
        <View style={{ alignItems: 'center' }}>
          <AkibaLogo variant="icon" size="md" />
        </View>

        {/* Title */}
        <View style={{ gap: spacing[2] }}>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size['2xl'],
            fontWeight: typography.weight.bold,
          }}>
            {type === 'verify' ? 'Verify your email' : 'Reset your password'}
          </Text>
          <Text style={{
            color:    colors.textSecondary,
            fontSize: typography.size.base,
          }}>
            Enter the 6-digit code sent to{' '}
            <Text style={{ fontWeight: typography.weight.semibold }}>
              {maskEmail(email)}
            </Text>
            . Check your spam folder if needed.
          </Text>
        </View>

        {/* OTP boxes */}
        <View style={{
          flexDirection:  'row',
          justifyContent: 'space-between',
          gap:            spacing[2],
        }}>
          {digits.map((digit, index) => (
            <OtpBox
              key={index}
              value={digit}
              isFocused={focused === index}
              shakeStyle={shakeStyle}
              inputRef={inputRefs.current[index]}
              onChangeText={(text) => handleChangeText(text, index)}
              onKeyPress={(e)  => handleKeyPress(e, index)}
              onFocus={() => setFocused(index)}
            />
          ))}
        </View>

        {/* API error */}
        {apiError && (
          <Text style={{
            color:     colors.danger,
            fontSize:  typography.size.sm,
            textAlign: 'center',
          }}>
            {apiError}
          </Text>
        )}

        {/* Verify button */}
        <Button
          label="Confirm Code"
          onPress={() => handleVerify(digits.join(''))}
          variant="primary"
          fullWidth
          loading={loading}
          disabled={digits.join('').length < OTP_LENGTH}
        />

        {/* Resend */}
        <Pressable onPress={handleResend} disabled={!canResend}>
          <Text style={{
            color:     canResend ? colors.primary : colors.textMuted,
            fontSize:  typography.size.sm,
            textAlign: 'center',
            fontWeight: typography.weight.medium,
          }}>
            {canResend
              ? 'Resend code'
              : `Resend in 0:${countdown.toString().padStart(2, '0')}`}
          </Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}
