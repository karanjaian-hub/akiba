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
import { authService, RegisterRequest } from '../../services/auth.service';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'Register'>;

const STEP_NAMES = [
  'Basic Info',
  'Financial Profile',
  'Budget Setup',
  'Bank Account',
];

// ─── Chip Selector ────────────────────────────────────────────────────────────
// Reusable chip row for single-select options

function ChipSelector({
  options,
  selected,
  onSelect,
  activeColor,
}: {
  options:     string[];
  selected:    string;
  onSelect:    (val: string) => void;
  activeColor: string;
}) {
  const colors     = useColors();
  const typography = useTypography();

  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
      {options.map((option) => {
        const isActive = option === selected;
        return (
          <Pressable
            key={option}
            onPress={() => onSelect(option)}
            style={{
              paddingHorizontal: 16,
              paddingVertical:   8,
              borderRadius:      20,
              borderWidth:       1.5,
              borderColor:       isActive ? activeColor : colors.border,
              backgroundColor:   isActive ? activeColor : 'transparent',
            }}
          >
            <Text style={{
              color:      isActive ? '#FFFFFF' : colors.textSecondary,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.medium,
            }}>
              {option}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// ─── Progress Bar ─────────────────────────────────────────────────────────────

function ProgressBar({
  step,
  total,
}: {
  step:  number;
  total: number;
}) {
  const colors  = useColors();
  const spacing = useSpacing();

  return (
    <View style={{ gap: spacing[2] }}>
      <View style={{
        height:          6,
        backgroundColor: colors.border,
        borderRadius:    3,
        overflow:        'hidden',
      }}>
        <View style={{
          height:          6,
          width:           `${(step / total) * 100}%`,
          backgroundColor: colors.primary,
          borderRadius:    3,
        }} />
      </View>
      <Text style={{
        color:    colors.textMuted,
        fontSize: 12,
      }}>
        Step {step} of {total} — {STEP_NAMES[step - 1]}
      </Text>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function RegisterScreen({ navigation }: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [step,    setStep]    = useState(1);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState<string | undefined>();

  // ── Step 1 state ───────────────────────────────────────────────────────────
  const [fullName,        setFullName]        = useState('');
  const [email,           setEmail]           = useState('');
  const [phone,           setPhone]           = useState('');
  const [password,        setPassword]        = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Step 1 errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  // ── Step 2 state ───────────────────────────────────────────────────────────
  const [incomeRange,     setIncomeRange]     = useState('');
  const [employmentType,  setEmploymentType]  = useState('');
  const [primaryGoal,     setPrimaryGoal]     = useState('');

  // ── Step 4 state ───────────────────────────────────────────────────────────
  const [bankName,        setBankName]        = useState('');
  const [accountNickname, setAccountNickname] = useState('');
  const [accountType,     setAccountType]     = useState('Savings');

  // ── Validation ─────────────────────────────────────────────────────────────

  const validateStep1 = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!fullName.trim())           newErrors.fullName        = 'Full name is required';
    if (!email.trim())              newErrors.email           = 'Email is required';
    if (!/\S+@\S+\.\S+/.test(email)) newErrors.email         = 'Enter a valid email';
    if (!phone.trim())              newErrors.phone           = 'Phone is required';
    if (!/^07\d{8}$/.test(phone.replace(/\s/g, '')))
                                    newErrors.phone           = 'Enter a valid Kenyan number';
    if (!password)                  newErrors.password        = 'Password is required';
    if (password.length < 8)        newErrors.password        = 'Minimum 8 characters';
    if (password !== confirmPassword) newErrors.confirmPassword = 'Passwords do not match';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // ── Navigation between steps ───────────────────────────────────────────────

  const handleNext = () => {
    if (step === 1 && !validateStep1()) return;
    setStep((prev) => prev + 1);
  };

  const handleBack = () => setStep((prev) => prev - 1);

  // ── Final submit ───────────────────────────────────────────────────────────

  const handleSubmit = async () => {
    setApiError(undefined);
    setLoading(true);

    const data: RegisterRequest = {
      fullName,
      email,
      phone: phone.replace(/\s/g, ''),
      password,
      confirmPassword,
      incomeRange:     incomeRange     || undefined,
      employmentType:  employmentType  || undefined,
      primaryGoal:     primaryGoal     || undefined,
      bankName:        bankName        || undefined,
      accountNickname: accountNickname || undefined,
      accountType:     accountType     || undefined,
    };

    try {
      await authService.register(data);
      navigation.navigate('OtpVerification', { email, type: 'verify' });
    } catch (err: any) {
      setApiError(err.message ?? 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // ── Render steps ───────────────────────────────────────────────────────────

  const renderStep1 = () => (
    <View style={{ gap: spacing[4] }}>
      <Input
        label="Full Name"
        value={fullName}
        onChangeText={(t) => { setFullName(t); setErrors((e) => ({ ...e, fullName: '' })); }}
        error={errors.fullName}
      />
      <Input
        label="Email address"
        value={email}
        onChangeText={(t) => { setEmail(t); setErrors((e) => ({ ...e, email: '' })); }}
        keyboardType="email-address"
        error={errors.email}
      />
      <Input
        label="Phone (07XX XXX XXX)"
        value={phone}
        onChangeText={(t) => { setPhone(t); setErrors((e) => ({ ...e, phone: '' })); }}
        keyboardType="phone-pad"
        error={errors.phone}
      />
      <Input
        label="Password"
        value={password}
        onChangeText={(t) => { setPassword(t); setErrors((e) => ({ ...e, password: '' })); }}
        secureTextEntry
        error={errors.password}
      />
      <Input
        label="Confirm Password"
        value={confirmPassword}
        onChangeText={(t) => { setConfirmPassword(t); setErrors((e) => ({ ...e, confirmPassword: '' })); }}
        secureTextEntry
        error={errors.confirmPassword}
      />
    </View>
  );

  const renderStep2 = () => (
    <View style={{ gap: spacing[6] }}>
      <View style={{ gap: spacing[3] }}>
        <Text style={{ color: colors.textPrimary, fontWeight: typography.weight.semibold }}>
          Monthly Income Range
        </Text>
        <ChipSelector
          options={['Under 20K', '20K–50K', '50K–100K', '100K+']}
          selected={incomeRange}
          onSelect={setIncomeRange}
          activeColor={colors.primary}
        />
      </View>
      <View style={{ gap: spacing[3] }}>
        <Text style={{ color: colors.textPrimary, fontWeight: typography.weight.semibold }}>
          Employment Type
        </Text>
        <ChipSelector
          options={['Employed', 'Self-employed', 'Student', 'Other']}
          selected={employmentType}
          onSelect={setEmploymentType}
          activeColor={colors.secondary}
        />
      </View>
      <View style={{ gap: spacing[3] }}>
        <Text style={{ color: colors.textPrimary, fontWeight: typography.weight.semibold }}>
          Primary Goal
        </Text>
        <ChipSelector
          options={['Save More', 'Reduce Debt', 'Track Spending', 'Build Emergency Fund']}
          selected={primaryGoal}
          onSelect={setPrimaryGoal}
          activeColor={colors.accentGreen}
        />
      </View>
    </View>
  );

  const renderStep3 = () => (
    <View style={{ gap: spacing[4] }}>
      <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
        Budget setup is available in the app after registration. You can configure your
        category budgets from the Budget screen.
      </Text>
      <View style={{
        backgroundColor: colors.primary + '15',
        borderRadius:    12,
        padding:         spacing[4],
      }}>
        <Text style={{
          color:      colors.primary,
          fontWeight: typography.weight.semibold,
          fontSize:   typography.size.base,
        }}>
          You can set up budgets after signing in.
        </Text>
        <Text style={{
          color:     colors.textSecondary,
          fontSize:  typography.size.sm,
          marginTop: spacing[2],
        }}>
          Head to the Budget tab once you're logged in to set spending limits per category.
        </Text>
      </View>
    </View>
  );

  const renderStep4 = () => (
    <View style={{ gap: spacing[4] }}>
      <View style={{ gap: spacing[3] }}>
        <Text style={{ color: colors.textPrimary, fontWeight: typography.weight.semibold }}>
          Bank Name
        </Text>
        <ChipSelector
          options={['KCB', 'Equity', 'Co-op', 'NCBA', 'Absa', 'DTB', 'StanChart', 'I&M']}
          selected={bankName}
          onSelect={setBankName}
          activeColor={colors.secondary}
        />
      </View>
      <Input
        label="Account Nickname (optional)"
        value={accountNickname}
        onChangeText={setAccountNickname}
      />
      <View style={{ gap: spacing[3] }}>
        <Text style={{ color: colors.textPrimary, fontWeight: typography.weight.semibold }}>
          Account Type
        </Text>
        <ChipSelector
          options={['Savings', 'Current']}
          selected={accountType}
          onSelect={setAccountType}
          activeColor={colors.primary}
        />
      </View>
    </View>
  );

  const STEP_RENDERERS = [renderStep1, renderStep2, renderStep3, renderStep4];

  // ── Main render ────────────────────────────────────────────────────────────

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
          paddingTop:        60,
          paddingBottom:     spacing[10],
          gap:               spacing[6],
        }}
        keyboardShouldPersistTaps="handled"
      >
        {/* Header */}
        <View style={{ gap: spacing[2] }}>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size['2xl'],
            fontWeight: typography.weight.bold,
          }}>
            Create Account
          </Text>
          <ProgressBar step={step} total={4} />
        </View>

        {/* Step content */}
        {STEP_RENDERERS[step - 1]()}

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

        {/* Navigation buttons */}
        <View style={{ gap: spacing[3], marginTop: 'auto' }}>
          {step < 4 ? (
            <>
              <Button
                label="Next"
                onPress={handleNext}
                variant="primary"
                fullWidth
              />
              {step > 1 && (
                <Button
                  label="Back"
                  onPress={handleBack}
                  variant="ghost"
                  fullWidth
                />
              )}
              {step > 1 && (
                <Pressable onPress={() => setStep((p) => p + 1)}>
                  <Text style={{
                    color:     colors.textMuted,
                    fontSize:  typography.size.sm,
                    textAlign: 'center',
                  }}>
                    Skip for now
                  </Text>
                </Pressable>
              )}
            </>
          ) : (
            <>
              <Button
                label="Create Account"
                onPress={handleSubmit}
                variant="primary"
                fullWidth
                loading={loading}
              />
              <Button
                label="Back"
                onPress={handleBack}
                variant="ghost"
                fullWidth
              />
            </>
          )}
        </View>

        {/* Back to login */}
        {step === 1 && (
          <Pressable onPress={() => navigation.navigate('Login')}>
            <Text style={{
              color:     colors.textMuted,
              fontSize:  typography.size.sm,
              textAlign: 'center',
            }}>
              Already have an account?{' '}
              <Text style={{ color: colors.primary, fontWeight: typography.weight.semibold }}>
                Sign In
              </Text>
            </Text>
          </Pressable>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
