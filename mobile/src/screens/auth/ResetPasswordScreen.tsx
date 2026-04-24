import React, { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button    from '../../components/common/Button';
import Input     from '../../components/common/Input';
import AkibaLogo from '../../components/common/AkibaLogo';
import { authService } from '../../services/auth.service';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'ResetPassword'>;

// ─── Password Strength ────────────────────────────────────────────────────────

type StrengthLevel = 0 | 1 | 2 | 3 | 4;

function getStrength(password: string): StrengthLevel {
  let score = 0;
  if (password.length >= 8)          score++;
  if (/[A-Z]/.test(password))        score++;
  if (/[0-9]/.test(password))        score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  return score as StrengthLevel;
}

const STRENGTH_LABELS: Record<StrengthLevel, string> = {
  0: '',
  1: 'Weak',
  2: 'Fair',
  3: 'Good',
  4: 'Strong',
};

const STRENGTH_COLORS: Record<StrengthLevel, string> = {
  0: 'transparent',
  1: '#DC2626',
  2: '#D97706',
  3: '#CA8A04',
  4: '#065F46',
};

function PasswordStrengthBar({ password }: { password: string }) {
  const colors   = useColors();
  const spacing  = useSpacing();
  const strength = getStrength(password);
  const color    = STRENGTH_COLORS[strength];
  const label    = STRENGTH_LABELS[strength];

  if (!password) return null;

  return (
    <View style={{ gap: spacing[1] }}>
      {/* Bar track */}
      <View style={{
        height:          6,
        backgroundColor: colors.border,
        borderRadius:    3,
        overflow:        'hidden',
      }}>
        <View style={{
          height:          6,
          width:           `${(strength / 4) * 100}%`,
          backgroundColor: color,
          borderRadius:    3,
        }} />
      </View>
      {/* Label */}
      <Text style={{
        color,
        fontSize:   12,
        fontWeight: '600',
      }}>
        {label}
      </Text>
    </View>
  );
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function ResetPasswordScreen({ route, navigation }: Props) {
  const { email, otp } = route.params;
  const colors         = useColors();
  const spacing        = useSpacing();
  const typography     = useTypography();

  const [newPassword,     setNewPassword]     = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passError,       setPassError]       = useState<string | undefined>();
  const [confirmError,    setConfirmError]    = useState<string | undefined>();
  const [apiError,        setApiError]        = useState<string | undefined>();
  const [loading,         setLoading]         = useState(false);

  const validate = (): boolean => {
    let valid = true;

    if (!newPassword) {
      setPassError('Password is required');
      valid = false;
    } else if (newPassword.length < 8) {
      setPassError('Minimum 8 characters');
      valid = false;
    } else if (getStrength(newPassword) < 2) {
      setPassError('Password is too weak');
      valid = false;
    } else {
      setPassError(undefined);
    }

    if (!confirmPassword) {
      setConfirmError('Please confirm your password');
      valid = false;
    } else if (newPassword !== confirmPassword) {
      setConfirmError('Passwords do not match');
      valid = false;
    } else {
      setConfirmError(undefined);
    }

    return valid;
  };

  const handleReset = async () => {
    if (!validate()) return;

    setApiError(undefined);
    setLoading(true);

    try {
      await authService.resetPassword({
        email,
        otp,
        newPassword,
        confirmPassword,
      });
      navigation.replace('Login');
    } catch (err: any) {
      setApiError(err.message ?? 'Reset failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar style="auto" />
      <ScrollView
        contentContainerStyle={{
          flexGrow:          1,
          paddingHorizontal: spacing[6],
          paddingTop:        80,
          paddingBottom:     spacing[10],
          gap:               spacing[6],
        }}
        keyboardShouldPersistTaps="handled"
      >
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
            Create new password
          </Text>
          <Text style={{
            color:    colors.textSecondary,
            fontSize: typography.size.base,
          }}>
            Choose a strong password for your Akiba account.
          </Text>
        </View>

        {/* Password inputs */}
        <View style={{ gap: spacing[3] }}>
          <Input
            label="New Password"
            value={newPassword}
            onChangeText={(text) => {
              setNewPassword(text);
              setPassError(undefined);
              setApiError(undefined);
            }}
            secureTextEntry
            error={passError}
          />

          {/* Strength bar appears below password input */}
          <PasswordStrengthBar password={newPassword} />

          <Input
            label="Confirm Password"
            value={confirmPassword}
            onChangeText={(text) => {
              setConfirmPassword(text);
              setConfirmError(undefined);
            }}
            secureTextEntry
            error={confirmError}
          />
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

        {/* Submit */}
        <Button
          label="Reset Password"
          onPress={handleReset}
          variant="primary"
          fullWidth
          loading={loading}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
