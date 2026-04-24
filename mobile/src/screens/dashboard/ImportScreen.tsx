import React, { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColors, useSpacing, useTypography } from '../../theme';
import Card from '../../components/common/Card';

export default function ImportScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [lastMpesa, setLastMpesa] = useState<string | null>(null);
  const [lastBank,  setLastBank]  = useState<string | null>(null);

  useEffect(() => {
    AsyncStorage.getItem('@akiba_last_mpesa_import').then(setLastMpesa);
    AsyncStorage.getItem('@akiba_last_bank_import').then(setLastBank);
  }, []);

  return (
    <View style={{
      flex:              1,
      backgroundColor:   colors.background,
      paddingHorizontal: spacing[6],
      paddingTop:        60,
      gap:               spacing[4],
    }}>
      <StatusBar style="auto" />

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
          Import Statements
        </Text>
      </View>

      {/* M-Pesa card */}
      <Card
        elevation="raised"
        onPress={() => navigation.navigate('MpesaImport')}
        style={{ borderLeftWidth: 4, borderLeftColor: colors.primary }}
      >
        <View style={{ gap: spacing[2] }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ fontSize: 32 }}>📱</Text>
            <Text style={{ color: colors.textMuted, fontSize: typography.size.lg }}>›</Text>
          </View>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.md,
            fontWeight: typography.weight.bold,
          }}>
            Import M-Pesa
          </Text>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            Last imported: {lastMpesa ?? 'Never imported'}
          </Text>
        </View>
      </Card>

      {/* Bank card */}
      <Card
        elevation="raised"
        onPress={() => navigation.navigate('BankImport')}
        style={{ borderLeftWidth: 4, borderLeftColor: colors.secondary }}
      >
        <View style={{ gap: spacing[2] }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ fontSize: 32 }}>🏦</Text>
            <Text style={{ color: colors.textMuted, fontSize: typography.size.lg }}>›</Text>
          </View>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.md,
            fontWeight: typography.weight.bold,
          }}>
            Import Bank Statement
          </Text>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            Last imported: {lastBank ?? 'Never imported'}
          </Text>
        </View>
      </Card>
    </View>
  );
}
