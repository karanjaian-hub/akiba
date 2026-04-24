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
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import Input  from '../../components/common/Input';
import { authService }  from '../../services/auth.service';
import { useAuthStore } from '../../store/auth.store';

// ─── Chip Selector ────────────────────────────────────────────────────────────

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
              backgroundColor:   isActive ? activeColor + '20' : 'transparent',
            }}
          >
            <Text style={{
              color:      isActive ? activeColor : colors.textSecondary,
              fontSize:   typography.size.sm,
              fontWeight: isActive
                ? typography.weight.semibold
                : typography.weight.regular,
            }}>
              {option}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function EditProfileScreen({ navigation }: { navigation: any }) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const user        = useAuthStore((state) => state.user);
  const updateUser  = useAuthStore((state) => state.updateUser);

  const [fullName,       setFullName]       = useState(user?.fullName       ?? '');
  const [incomeRange,    setIncomeRange]    = useState('');
  const [employmentType, setEmploymentType] = useState('');
  const [successMsg,     setSuccessMsg]     = useState('');
  const [errorMsg,       setErrorMsg]       = useState('');

  const { mutate: saveProfile, isPending } = useMutation({
    mutationFn: () => authService.updateProfile({ fullName }),
    onSuccess:  (updated) => {
      updateUser({ fullName: updated.fullName });
      setSuccessMsg('Profile updated successfully.');
      setTimeout(() => setSuccessMsg(''), 3000);
    },
    onError: (err: any) => {
      setErrorMsg(err.message ?? 'Failed to update profile.');
    },
  });

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar style="auto" />
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: spacing[6],
          paddingTop:        60,
          paddingBottom:     spacing[10],
          gap:               spacing[5],
        }}
        keyboardShouldPersistTaps="handled"
      >
        {/* Header */}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[3] }}>
          <Pressable onPress={() => navigation.goBack()}>
            <Text style={{ color: colors.primary, fontSize: typography.size.lg }}>←</Text>
          </Pressable>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.xl,
            fontWeight: typography.weight.bold,
          }}>
            Edit Profile
          </Text>
        </View>

        {/* Inputs */}
        <Input
          label="Full Name"
          value={fullName}
          onChangeText={setFullName}
        />
        <Input
          label="Email address"
          value={user?.email ?? ''}
          onChangeText={() => {}}
          disabled
        />
        <Input
          label="Phone number"
          value={user?.phone ?? ''}
          onChangeText={() => {}}
          disabled
        />

        {/* Income range */}
        <View style={{ gap: spacing[2] }}>
          <Text style={{
            color:      colors.textPrimary,
            fontWeight: typography.weight.semibold,
            fontSize:   typography.size.base,
          }}>
            Monthly Income Range
          </Text>
          <ChipSelector
            options={['Under 20K', '20K–50K', '50K–100K', '100K+']}
            selected={incomeRange}
            onSelect={setIncomeRange}
            activeColor={colors.primary}
          />
        </View>

        {/* Employment type */}
        <View style={{ gap: spacing[2] }}>
          <Text style={{
            color:      colors.textPrimary,
            fontWeight: typography.weight.semibold,
            fontSize:   typography.size.base,
          }}>
            Employment Type
          </Text>
          <ChipSelector
            options={['Employed', 'Self-employed', 'Student', 'Other']}
            selected={employmentType}
            onSelect={setEmploymentType}
            activeColor={colors.secondary}
          />
        </View>

        {/* Messages */}
        {successMsg && (
          <Text style={{
            color:     colors.accentGreen,
            fontSize:  typography.size.sm,
            textAlign: 'center',
          }}>
            ✓ {successMsg}
          </Text>
        )}
        {errorMsg && (
          <Text style={{
            color:     colors.danger,
            fontSize:  typography.size.sm,
            textAlign: 'center',
          }}>
            {errorMsg}
          </Text>
        )}

        <Button
          label="Save Changes"
          onPress={() => saveProfile()}
          variant="primary"
          fullWidth
          loading={isPending}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
