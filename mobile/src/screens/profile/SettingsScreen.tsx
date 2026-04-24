import React from 'react';
import {
  Pressable,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useColors, useSpacing, useTypography, useTheme, ColorScheme } from '../../theme';
import Card from '../../components/common/Card';

// ─── Theme Option ─────────────────────────────────────────────────────────────

function ThemeOption({
  icon,
  label,
  description,
  isActive,
  onSelect,
}: {
  icon:        string;
  label:       string;
  description: string;
  isActive:    boolean;
  onSelect:    () => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <Pressable
      onPress={onSelect}
      style={{
        flexDirection:   'row',
        alignItems:      'center',
        gap:             spacing[3],
        paddingVertical: spacing[3],
        borderBottomWidth: 1,
        borderBottomColor: colors.border,
      }}
    >
      <Text style={{ fontSize: 24, width: 32, textAlign: 'center' }}>{icon}</Text>
      <View style={{ flex: 1 }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.base,
          fontWeight: typography.weight.semibold,
        }}>
          {label}
        </Text>
        <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
          {description}
        </Text>
      </View>
      {isActive && (
        <View style={{
          width:           24,
          height:          24,
          borderRadius:    12,
          backgroundColor: colors.primary,
          alignItems:      'center',
          justifyContent:  'center',
        }}>
          <Text style={{ color: '#FFFFFF', fontSize: 14, fontWeight: '700' }}>✓</Text>
        </View>
      )}
    </Pressable>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function SettingsScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const { scheme, setScheme } = useTheme();

  const OPTIONS: {
    icon:        string;
    label:       string;
    description: string;
    value:       ColorScheme;
  }[] = [
    {
      icon:        '⚙️',
      label:       'Auto',
      description: 'Follows your device setting',
      value:       'auto',
    },
    {
      icon:        '☀️',
      label:       'Light Mode',
      description: 'Always use light theme',
      value:       'light',
    },
    {
      icon:        '🌙',
      label:       'Dark Mode',
      description: 'Always use dark theme',
      value:       'dark',
    },
  ];

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      <View style={{
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        gap:               spacing[5],
      }}>
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
            Appearance
          </Text>
        </View>

        {/* Options */}
        <Card elevation="raised" style={{ gap: 0, padding: 0, paddingHorizontal: spacing[4] }}>
          {OPTIONS.map((opt) => (
            <ThemeOption
              key={opt.value}
              icon={opt.icon}
              label={opt.label}
              description={opt.description}
              isActive={scheme === opt.value}
              onSelect={() => setScheme(opt.value)}
            />
          ))}
        </Card>

        <Text style={{
          color:     colors.textMuted,
          fontSize:  typography.size.xs,
          textAlign: 'center',
        }}>
          Your preference is saved automatically.
        </Text>
      </View>
    </View>
  );
}
