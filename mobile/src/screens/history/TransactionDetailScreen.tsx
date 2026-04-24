import React, { useState } from 'react';
import {
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import Card  from '../../components/common/Card';
import Badge from '../../components/common/Badge';
import { SkeletonCard } from '../../components/skeleton/SkeletonLoader';
import { transactionService } from '../../services/transaction.service';

// ─── Category Emoji ───────────────────────────────────────────────────────────

const CATEGORY_EMOJI: Record<string, string> = {
  Food:          '🍽',
  Transport:     '🚗',
  Bills:         '⚡',
  Entertainment: '🎬',
  Health:        '💊',
  Other:         '💰',
};

const CATEGORIES = Object.keys(CATEGORY_EMOJI);

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE')}`;
}

// ─── Component ───────────────────────────────────────────────────────────────

export default function TransactionDetailScreen({
  route,
  navigation,
}: {
  route:      any;
  navigation: any;
}) {
  const { transactionId } = route.params;
  const colors            = useColors();
  const spacing           = useSpacing();
  const typography        = useTypography();
  const queryClient       = useQueryClient();

  const [showRaw,       setShowRaw]       = useState(false);
  const [showCatPicker, setShowCatPicker] = useState(false);

  const { data: tx, isLoading } = useQuery({
    queryKey: ['transaction', transactionId],
    queryFn:  () => transactionService.getTransaction(transactionId),
  });

  const { mutate: updateCategory, isPending } = useMutation({
    mutationFn: (category: string) =>
      transactionService.updateCategory(transactionId, category),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transaction', transactionId] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      setShowCatPicker(false);
    },
  });

  if (isLoading) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.background, padding: spacing[4], paddingTop: 60 }}>
        <SkeletonCard />
      </View>
    );
  }

  if (!tx) return null;

  const isCredit = tx.type === 'CREDIT';

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        paddingBottom:     spacing[10],
        gap:               spacing[4],
      }}
    >
      <StatusBar style="auto" />

      {/* Back button */}
      <Pressable
        onPress={() => navigation.goBack()}
        style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}
      >
        <Text style={{ color: colors.primary, fontSize: typography.size.lg }}>←</Text>
        <Text style={{ color: colors.primary, fontSize: typography.size.base }}>Back</Text>
      </Pressable>

      {/* Main card */}
      <Card elevation="raised" style={{ alignItems: 'center', gap: spacing[4] }}>
        {/* Icon */}
        <View style={{
          width:           72,
          height:          72,
          borderRadius:    36,
          backgroundColor: isCredit
            ? colors.accentGreen + '20'
            : colors.danger + '20',
          alignItems:      'center',
          justifyContent:  'center',
        }}>
          <Text style={{ fontSize: 32 }}>
            {CATEGORY_EMOJI[tx.category] ?? '💰'}
          </Text>
        </View>

        {/* Merchant */}
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
          textAlign:  'center',
        }}>
          {tx.merchant}
        </Text>

        {/* Amount */}
        <Text style={{
          color:      isCredit ? colors.accentGreen : colors.danger,
          fontSize:   typography.size['3xl'],
          fontWeight: typography.weight.bold,
        }}>
          {isCredit ? '+' : '-'}{formatKsh(tx.amount)}
        </Text>

        {/* Type badge */}
        <Badge
          label={isCredit ? 'Credit' : 'Debit'}
          variant={isCredit ? 'success' : 'danger'}
        />
      </Card>

      {/* Details card */}
      <Card elevation="raised" style={{ gap: spacing[3] }}>
        <DetailRow label="Date" value={
          new Date(tx.date).toLocaleDateString('en-KE', {
            weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
          })
        } colors={colors} typography={typography} />

        <DetailRow label="Time" value={
          new Date(tx.date).toLocaleTimeString('en-KE', {
            hour: '2-digit', minute: '2-digit',
          })
        } colors={colors} typography={typography} />

        <DetailRow label="Source" value={tx.source} colors={colors} typography={typography} />

        {tx.reference && (
          <DetailRow label="Reference" value={tx.reference} colors={colors} typography={typography} />
        )}

        {/* Divider */}
        <View style={{ height: 1, backgroundColor: colors.border }} />

        {/* Category row with change button */}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            Category
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
            <Badge label={tx.category} variant="info" />
            <Pressable onPress={() => setShowCatPicker((v) => !v)}>
              <Text style={{
                color:      colors.primary,
                fontSize:   typography.size.sm,
                fontWeight: typography.weight.medium,
              }}>
                Change
              </Text>
            </Pressable>
          </View>
        </View>

        {/* Category picker */}
        {showCatPicker && (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }}>
            {CATEGORIES.map((cat) => (
              <Pressable
                key={cat}
                onPress={() => updateCategory(cat)}
                style={{
                  paddingHorizontal: 12,
                  paddingVertical:   6,
                  borderRadius:      20,
                  borderWidth:       1.5,
                  borderColor:       tx.category === cat ? colors.primary : colors.border,
                  backgroundColor:   tx.category === cat ? colors.primary + '15' : 'transparent',
                  opacity:           isPending ? 0.5 : 1,
                }}
              >
                <Text style={{
                  color:    tx.category === cat ? colors.primary : colors.textSecondary,
                  fontSize: typography.size.sm,
                }}>
                  {CATEGORY_EMOJI[cat]} {cat}
                </Text>
              </Pressable>
            ))}
          </View>
        )}
      </Card>

      {/* Raw text — expandable */}
      {tx.rawText && (
        <Card elevation="flat" style={{ gap: spacing[2] }}>
          <Pressable onPress={() => setShowRaw((v) => !v)}>
            <Text style={{
              color:      colors.primary,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.medium,
            }}>
              {showRaw ? 'Hide original' : 'Show original'}
            </Text>
          </Pressable>
          {showRaw && (
            <Text style={{
              color:      colors.textSecondary,
              fontSize:   typography.size.xs,
              fontFamily: 'monospace',
            }}>
              {tx.rawText}
            </Text>
          )}
        </Card>
      )}
    </ScrollView>
  );
}

// ─── Detail Row ───────────────────────────────────────────────────────────────

function DetailRow({
  label,
  value,
  colors,
  typography,
}: {
  label:      string;
  value:      string;
  colors:     any;
  typography: any;
}) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
      <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
        {label}
      </Text>
      <Text style={{
        color:      colors.textPrimary,
        fontSize:   typography.size.sm,
        fontWeight: typography.weight.medium,
        maxWidth:   '60%',
        textAlign:  'right',
      }}>
        {value}
      </Text>
    </View>
  );
}
