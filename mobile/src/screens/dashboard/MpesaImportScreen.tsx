import React, { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  Text,
  TextInput,
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

type Tab    = 'sms' | 'pdf';
type Status = 'idle' | 'loading' | 'success' | 'error';

type ImportResult = {
  transactionCount: number;
  message:          string;
};

// ─── Component ───────────────────────────────────────────────────────────────

export default function MpesaImportScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [activeTab,  setActiveTab]  = useState<Tab>('sms');
  const [smsText,    setSmsText]    = useState('');
  const [fileName,   setFileName]   = useState<string | null>(null);
  const [fileBase64, setFileBase64] = useState<string | null>(null);
  const [status,     setStatus]     = useState<Status>('idle');
  const [result,     setResult]     = useState<ImportResult | null>(null);
  const [error,      setError]      = useState<string | null>(null);

  // ── File picker ────────────────────────────────────────────────────────────

  const handlePickFile = async () => {
    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type:       'application/pdf',
        copyToCacheDirectory: true,
      });

      if (picked.canceled) return;

      const file = picked.assets[0];
      setFileName(file.name);
      // Store URI — will be sent as base64 to API
      setFileBase64(file.uri);
    } catch {
      setError('Failed to pick file. Please try again.');
    }
  };

  // ── Import ─────────────────────────────────────────────────────────────────

  const handleImport = async () => {
    const hasContent = activeTab === 'sms' ? !!smsText.trim() : !!fileBase64;
    if (!hasContent) return;

    setStatus('loading');
    setError(null);

    try {
      const payload = activeTab === 'sms'
        ? { type: 'MPESA_SMS', content: smsText }
        : { type: 'MPESA_PDF', content: fileBase64 };

      const response = await apiPost<ImportResult>('/parse/mpesa', payload);
      setResult(response);
      setStatus('success');

      // Save last import date
      await AsyncStorage.setItem(
        '@akiba_last_mpesa_import',
        new Date().toLocaleDateString('en-KE'),
      );
    } catch (err: any) {
      setError(err.message ?? 'Import failed. Please try again.');
      setStatus('error');
    }
  };

  const canImport = activeTab === 'sms' ? !!smsText.trim() : !!fileBase64;

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        paddingHorizontal: spacing[6],
        paddingTop:        60,
        paddingBottom:     spacing[10],
        gap:               spacing[5],
      }}
      keyboardShouldPersistTaps="handled"
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
          Import M-Pesa
        </Text>
      </View>

      {/* Tab selector */}
      <View style={{
        flexDirection:   'row',
        backgroundColor: colors.surfaceElevated,
        borderRadius:    12,
        padding:         4,
      }}>
        {(['sms', 'pdf'] as Tab[]).map((tab) => (
          <Pressable
            key={tab}
            onPress={() => setActiveTab(tab)}
            style={{
              flex:            1,
              paddingVertical: spacing[2],
              borderRadius:    10,
              alignItems:      'center',
              backgroundColor: activeTab === tab ? colors.surface : 'transparent',
            }}
          >
            <Text style={{
              color:      activeTab === tab ? colors.primary : colors.textSecondary,
              fontWeight: activeTab === tab ? typography.weight.semibold : typography.weight.regular,
              fontSize:   typography.size.sm,
            }}>
              {tab === 'sms' ? 'Paste SMS' : 'Upload PDF'}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* Tab content */}
      {activeTab === 'sms' ? (
        <View style={{ gap: spacing[2] }}>
          <TextInput
            value={smsText}
            onChangeText={setSmsText}
            placeholder="Paste your M-Pesa SMS messages here..."
            placeholderTextColor={colors.textMuted}
            multiline
            style={{
              backgroundColor: colors.inputBackground,
              borderWidth:     1.5,
              borderColor:     colors.inputBorder,
              borderRadius:    12,
              padding:         spacing[4],
              color:           colors.textPrimary,
              fontSize:        typography.size.sm,
              minHeight:       150,
              textAlignVertical: 'top',
            }}
          />
          <Text style={{
            color:     colors.textMuted,
            fontSize:  typography.size.xs,
            textAlign: 'right',
          }}>
            {smsText.length} characters
          </Text>
        </View>
      ) : (
        <Pressable onPress={handlePickFile}>
          <View style={{
            borderWidth:     2,
            borderColor:     fileName ? colors.primary : colors.border,
            borderStyle:     'dashed',
            borderRadius:    16,
            padding:         spacing[8],
            alignItems:      'center',
            gap:             spacing[3],
          }}>
            <Text style={{ fontSize: 40 }}>☁️</Text>
            <Text style={{
              color:     fileName ? colors.primary : colors.textSecondary,
              fontSize:  typography.size.sm,
              textAlign: 'center',
              fontWeight: typography.weight.medium,
            }}>
              {fileName ?? 'Tap to select PDF'}
            </Text>
          </View>
        </Pressable>
      )}

      {/* Loading state */}
      {status === 'loading' && (
        <View style={{ alignItems: 'center', gap: spacing[3] }}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            Analyzing your M-Pesa transactions...
          </Text>
        </View>
      )}

      {/* Success state */}
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
          <View style={{ flexDirection: 'row', gap: spacing[3], marginTop: spacing[4] }}>
            <Button
              label="View Transactions"
              onPress={() => navigation.navigate('HistoryTab')}
              variant="secondary"
              fullWidth
            />
          </View>
          <Button
            label="Import More"
            onPress={() => {
              setStatus('idle');
              setSmsText('');
              setFileName(null);
              setFileBase64(null);
              setResult(null);
            }}
            variant="ghost"
            fullWidth
          />
        </Card>
      )}

      {/* Error state */}
      {status === 'error' && error && (
        <Card elevation="raised" style={{ borderLeftWidth: 4, borderLeftColor: colors.danger }}>
          <Text style={{ fontSize: 32, textAlign: 'center' }}>❌</Text>
          <Text style={{
            color:     colors.danger,
            fontSize:  typography.size.sm,
            textAlign: 'center',
            marginTop: spacing[2],
          }}>
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
          variant="primary"
          fullWidth
          disabled={!canImport}
        />
      )}
    </ScrollView>
  );
}
