import React, { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import Input  from '../../components/common/Input';
import AkibaLogo from '../../components/common/AkibaLogo';
import { authService } from '../../services/auth.service';
import { useAuthStore } from '../../store/auth.store';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'Login'>;

// ─── Validation ───────────────────────────────────────────────────────────────

function validateEmail(email: string): string | undefined {
  if (!email) return 'Email is required';
  if (!/\S+@\S+\.\S+/.test(email)) return 'Enter a valid email address';
}

function validatePassword(password: string): string | undefined {
  if (!password) return 'Password is required';
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function LoginScreen({ navigation }: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const login      = useAuthStore((state) => state.login);

  const [email,       setEmail]       = useState('');
  const [password,    setPassword]    = useState('');
  const [emailError,  setEmailError]  = useState<string | undefined>();
  const [passError,   setPassError]   = useState<string | undefined>();
  const [apiError,    setApiError]    = useState<string | undefined>();
  const [loading,     setLoading]     = useState(false);

  // ── Handlers ───────────────────────────────────────────────────────────────

  const handleLogin = async () => {
    // Validate before touching the network
    const eErr = validateEmail(email);
    const pErr = validatePassword(password);
    setEmailError(eErr);
    setPassError(pErr);
    if (eErr || pErr) return;

    setApiError(undefined);
    setLoading(true);

    try {
      const response = await authService.login({ email, password });
      // Store token + user globally — RootNavigator will re-render to AppStack
      login(response.accessToken, response.user);
    } catch (err: any) {
      setApiError(err.message ?? 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────

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
          paddingBottom:     spacing[10],
        }}
        keyboardShouldPersistTaps="handled"
      >
        {/* Logo section */}
        <View style={{
          alignItems:     'center',
          paddingTop:     80,
          paddingBottom:  spacing[10],
        }}>
          <AkibaLogo variant="wordmark" size="lg" showTagline />
        </View>

        {/* Form */}
        <View style={{ gap: spacing[4] }}>
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

          <Input
            label="Password"
            value={password}
            onChangeText={(text) => {
              setPassword(text);
              setPassError(undefined);
              setApiError(undefined);
            }}
            secureTextEntry
            error={passError}
          />

          {/* Forgot password link */}
          <Pressable
            onPress={() => navigation.navigate('ForgotPassword')}
            style={{ alignSelf: 'flex-end' }}
          >
            <Text style={{
              color:      colors.gold,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.medium,
            }}>
              Forgot Password?
            </Text>
          </Pressable>

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

          <Button
            label="Sign In"
            onPress={handleLogin}
            variant="primary"
            fullWidth
            loading={loading}
          />

          {/* Divider */}
          <View style={{
            flexDirection: 'row',
            alignItems:    'center',
            gap:           spacing[3],
            marginVertical: spacing[2],
          }}>
            <View style={{ flex: 1, height: 1, backgroundColor: colors.border }} />
            <Text style={{ color: colors.textMuted, fontSize: typography.size.sm }}>
              or
            </Text>
            <View style={{ flex: 1, height: 1, backgroundColor: colors.border }} />
          </View>

          <Button
            label="Create Account"
            onPress={() => navigation.navigate('Register')}
            variant="outline"
            fullWidth
          />
        </View>

        {/* Tagline */}
        <Text style={{
          color:      colors.textMuted,
          fontSize:   typography.size.sm,
          fontStyle:  'italic',
          textAlign:  'center',
          marginTop:  spacing[10],
        }}>
          Every shilling has a story.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
