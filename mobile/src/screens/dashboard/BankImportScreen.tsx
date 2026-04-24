import React, { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import * as DocumentPicker from 'expo-document-picker';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import Card   from '../../components/common/Card';
import { apiPost } from '../../services/api';

// ─── Types ───────────────────────────────────────────────────────────────────

type Status = 'idle' | 'loading' | 'success' | 'error';

type ImportResult = {
  transactionCount: number;
  message:          string;
};

const BANKS = [
  { name: 'KCB',      color: '#1E40AF' },
  { name: 'Equity',   color: '#DC2626' },
  { name: 'Co-op',    color: '#065F46' },
  { name: 'NCBA',     color: '#4C1D95' },
  { name: 'Absa',     color: '#DC2626' },
  { name: 'DTB',      color: '#1E40AF' },
];

// ─── Component ───────────────────────────────────────────────────────────────

export default function BankImportScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [selectedBank, setSelectedBank] = useState<string | null>(null);
  const [fileName,     setFileName]     = useState<string | null>(null);
  const [fileUri,      setFileUri]      = useState<string | null>(null);
  const [status,       setStatus]       = useState<Status>('idle');
  const [result,       setResult]       = useState<ImportResult | null>(null);
  const [error,        setError]        = useState<string | null>(null);

  const handlePickFile = async () => {
    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type:                 'application/pdf',
        copyToCacheDirectory: true,
      });
      if (picked.canceled) return;
      const file = picked.assets[0];
      setFileName(file.name);
      setFileUri(file.uri);
    } catch {
      setError('Failed to pick file. Please try again.');
    }
  };

  const handleImport = async () => {
    if (!selectedBank || !fileUri) return;

    setStatus('loading');
    setError(null);

    try {
      const response = await apiPost<ImportResult>('/parse/bank', {
        bank:    selectedBank,
        content: fileUri,
      });
      setResult(response);
      setStatus('success');
      await AsyncStorage.setItem(
        '@akiba_last_bank_import',
        new Date().toLocaleDateString('en-KE'),
      );
    } catch (err: any) {
      setError(err.message ?? 'Import failed. Please try again.');
      setStatus('error');
    }
  };

  const canImport = !!selectedBank && !!fileUri;

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        paddingHorizontal: spacing[6],
        paddingTop:        60,
        paddingBottom:     spacing[10],
        gap:               spacing[5],
      }}
    >
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
          Import Bank Statement
        </Text>
      </View>

      {/* Bank chips */}
      <View style={{ gap: spacing[3] }}>
        <Text style={{
          color:      colors.textPrimary,
          fontWeight: typography.weight.semibold,
          fontSize:   typography.size.base,
        }}>
          Select your bank
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3] }}>
          {BANKS.map((bank) => {
            const isSelected = selectedBank === bank.name;
            return (
              <Pressable
                key={bank.name}
                onPress={() => setSelectedBank(bank.name)}
                style={{
                  flexDirection:   'row',
                  alignItems:      'center',
                  gap:             spacing[2],
                  paddingHorizontal: spacing[4],
                  paddingVertical: spacing[2],
                  borderRadius:    20,
                  borderWidth:     1.5,
                  borderColor:     isSelected ? bank.color : colors.border,
                  backgroundColor: isSelected ? bank.color + '15' : 'transparent',
                }}
              >
                {/* Bank initials circle */}
                <View style={{
                  width:           28,
                  height:          28,
                  borderRadius:    14,
                  backgroundColor: bank.color,
                  alignItems:      'center',
                  justifyContent:  'center',
                }}>
                  <Text style={{
                    color:      '#FFFFFF',
                    fontSize:   typography.size.xs,
                    fontWeight: typography.weight.bold,
                  }}>
                    {bank.name.slice(0, 2)}
                  </Text>
                </View>
                <Text style={{
                  color:      isSelected ? bank.color : colors.textSecondary,
                  fontSize:   typography.size.sm,
                  fontWeight: isSelected ? typography.weight.semibold : typography.weight.regular,
                }}>
                  {bank.name}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      {/* PDF upload zone */}
      <Pressable onPress={handlePickFile}>
        <View style={{
          borderWidth:  2,
          borderColor:  fileName ? colors.secondary : colors.border,
          borderStyle:  'dashed',
          borderRadius: 16,
          padding:      spacing[8],
          alignItems:   'center',
          gap:          spacing[3],
        }}>
          <Text style={{ fontSize: 40 }}>☁️</Text>
          <Text style={{
            color:      fileName ? colors.secondary : colors.textSecondary,
            fontSize:   typography.size.sm,
            textAlign:  'center',
            fontWeight: typography.weight.medium,
          }}>
            {fileName ?? 'Tap to select PDF'}
          </Text>
        </View>
      </Pressable>

      {/* Loading */}
      {status === 'loading' && (
        <View style={{ alignItems: 'center', gap: spacing[3] }}>
          <ActivityIndicator size="large" color={colors.secondary} />
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            Analyzing your bank statement...
          </Text>
        </View>
      )}

      {/* Success */}
      {status === 'success' && result && (
        <Card elevation="raised" style={{ borderLeftWidth: 4, borderLeftColor: colors.accentGreen }}>
          <Text style={{ fontSize: 32, textAlign: 'center' }}>✅</Text>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.md,
            fontWeight: typography.weight.bold,
            textAlign:  'center',
            marginTop:  spacing[2],
          }}>
            {result.transactionCount} transactions imported
          </Text>
          <Button
            label="View Transactions"
            onPress={() => navigation.navigate('HistoryTab')}
            variant="secondary"
            fullWidth
            style={{ marginTop: spacing[4] }}
          />
        </Card>
      )}

      {/* Error */}
      {status === 'error' && error && (
        <Card elevation="raised" style={{ borderLeftWidth: 4, borderLeftColor: colors.danger }}>
          <Text style={{ color: colors.danger, fontSize: typography.size.sm, textAlign: 'center' }}>
            {error}
          </Text>
          <Button
            label="Try Again"
            onPress={() => setStatus('idle')}
            variant="danger"
            fullWidth
            style={{ marginTop: spacing[3] }}
          />
        </Card>
      )}

      {/* Import button */}
      {status === 'idle' && (
        <Button
          label="Import"
          onPress={handleImport}
          variant="secondary"
          fullWidth
          disabled={!canImport}
        />
      )}
    </ScrollView>
  );
}
