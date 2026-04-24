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
import Button   from '../../components/common/Button';
import Input    from '../../components/common/Input';
import AkibaLogo from '../../components/common/AkibaLogo';
import { authService } from '../../services/auth.service';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'ForgotPassword'>;

// ─── Component ───────────────────────────────────────────────────────────────

export default function ForgotPasswordScreen({ navigation }: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [email,      setEmail]      = useState('');
  const [emailError, setEmailError] = useState<string | undefined>();
  const [apiError,   setApiError]   = useState<string | undefined>();
  const [loading,    setLoading]    = useState(false);

  const validate = (): boolean => {
    if (!email.trim()) {
      setEmailError('Email is required');
      return false;
    }
    if (!/\S+@\S+\.\S+/.test(email)) {
      setEmailError('Enter a valid email address');
      return false;
    }
    return true;
  };

  const handleSend = async () => {
    if (!validate()) return;

    setApiError(undefined);
    setLoading(true);

    try {
      await authService.forgotPassword(email);
      navigation.navigate('OtpVerification', { email, type: 'reset' });
    } catch (err: any) {
      setApiError(err.message ?? 'Failed to send reset code. Please try again.');
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
            Forgot your password?
          </Text>
          <Text style={{
            color:    colors.textSecondary,
            fontSize: typography.size.base,
          }}>
            Enter your email address and we will send you a 6-digit reset code.
          </Text>
        </View>

        {/* Input */}
        <Input
          label="Email address"
          value={email}
          onChangeText={(text) => {
            setEmail(text);
            setEmailError(undefined);
            setApiError(undefined);
          }}
          keyboardType="email-address"
          error={emailError}
        />

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

        {/* Buttons */}
        <View style={{ gap: spacing[3] }}>
          <Button
            label="Send Reset Code"
            onPress={handleSend}
            variant="primary"
            fullWidth
            loading={loading}
          />
          <Button
            label="Back to Login"
            onPress={() => navigation.navigate('Login')}
            variant="ghost"
            fullWidth
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
