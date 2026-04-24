import React, { useState, useEffect } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useQuery } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import Card   from '../../components/common/Card';
import { paymentService, RecipientType } from '../../services/payment.service';
import { budgetService }                 from '../../services/budget.service';
import { useAuthStore }                  from '../../store/auth.store';

// ─── Types ───────────────────────────────────────────────────────────────────

type Tab      = RecipientType;
type PinState = { digits: string[]; visible: boolean };

const CATEGORIES = [
  'Food', 'Transport', 'Bills', 'Entertainment', 'Health', 'Other',
];

// ─── PIN Pad ──────────────────────────────────────────────────────────────────

function PinPad({
  pin,
  onDigit,
  onBackspace,
}: {
  pin:        string;
  onDigit:    (d: string) => void;
  onBackspace: () => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const keys = ['1','2','3','4','5','6','7','8','9','','0','⌫'];

  return (
    <View style={{ gap: spacing[3] }}>
      {/* Dot indicators */}
      <View style={{ flexDirection: 'row', justifyContent: 'center', gap: spacing[3] }}>
        {[0,1,2,3].map((i) => (
          <View
            key={i}
            style={{
              width:           16,
              height:          16,
              borderRadius:    8,
              borderWidth:     1.5,
              borderColor:     colors.primary,
              backgroundColor: i < pin.length ? colors.primary : 'transparent',
            }}
          />
        ))}
      </View>

      {/* Number grid */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }}>
        {keys.map((key, index) => (
          <Pressable
            key={index}
            onPress={() => {
              if (key === '⌫') { onBackspace(); return; }
              if (key === '')   return;
              if (pin.length < 4) onDigit(key);
            }}
            style={{
              width:           '30%',
              paddingVertical: spacing[4],
              alignItems:      'center',
              backgroundColor: key === '' ? 'transparent' : colors.surfaceElevated,
              borderRadius:    12,
            }}
          >
            <Text style={{
              color:      colors.textPrimary,
              fontSize:   typography.size.xl,
              fontWeight: typography.weight.semibold,
            }}>
              {key}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function PaymentScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const user       = useAuthStore((state) => state.user);

  const [activeTab,    setActiveTab]    = useState<Tab>('PHONE');
  const [phone,        setPhone]        = useState('');
  const [till,         setTill]         = useState('');
  const [paybill,      setPaybill]      = useState('');
  const [accountNo,    setAccountNo]    = useState('');
  const [amount,       setAmount]       = useState('');
  const [category,     setCategory]     = useState('');
  const [pin,          setPin]          = useState('');
  const [showPin,      setShowPin]      = useState(false);
  const [loading,      setLoading]      = useState(false);
  const [budgetWarning, setBudgetWarning] = useState<string | null>(null);
  const [budgetOk,     setBudgetOk]     = useState<string | null>(null);

  // Load recent recipients
  const { data: recipients } = useQuery({
    queryKey: ['recipients'],
    queryFn:  paymentService.getRecipients,
  });

  // Budget check — debounced 500ms after amount + category selected
  useEffect(() => {
    if (!amount || !category) {
      setBudgetWarning(null);
      setBudgetOk(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const check = await budgetService.checkBudget(category, Number(amount));
        if (!check.canAfford) {
          setBudgetWarning(
            `This will take your ${category} budget to ${Math.round(check.percentAfter)}%`
          );
          setBudgetOk(null);
        } else {
          const remaining = check.limit - check.currentSpent - Number(amount);
          setBudgetOk(`Ksh ${remaining.toLocaleString()} remaining in ${category} budget after this`);
          setBudgetWarning(null);
        }
      } catch {
        // Budget check is non-critical — fail silently
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [amount, category]);

  // Auto-open PIN pad when 4th digit entered
  useEffect(() => {
    if (pin.length === 4) {
      setTimeout(() => handleSubmit(), 300);
    }
  }, [pin]);

  const getRecipientDisplay = () => {
    if (activeTab === 'PHONE')   return phone;
    if (activeTab === 'TILL')    return till;
    if (activeTab === 'PAYBILL') return `${paybill}${accountNo ? ` / ${accountNo}` : ''}`;
    return '';
  };

  const canProceed = () => {
    const hasRecipient =
      (activeTab === 'PHONE'   && phone.length >= 10) ||
      (activeTab === 'TILL'    && till.length  >= 4)  ||
      (activeTab === 'PAYBILL' && paybill.length >= 4);
    return hasRecipient && Number(amount) > 0 && !!category;
  };

  const handleSubmit = async () => {
    if (!canProceed() || pin.length < 4) return;

    setLoading(true);
    try {
      const response = await paymentService.initiatePayment({
        recipientType:  activeTab,
        phone:          activeTab === 'PHONE'   ? phone   : undefined,
        till:           activeTab === 'TILL'    ? till    : undefined,
        paybill:        activeTab === 'PAYBILL' ? paybill : undefined,
        accountNumber:  activeTab === 'PAYBILL' ? accountNo : undefined,
        amount:         Number(amount),
        category,
        pin,
      });
      setShowPin(false);
      setPin('');
      navigation.navigate('PaymentConfirm', {
        paymentId: response.paymentId,
        phone:     user?.phone ?? phone,
      });
    } catch (err: any) {
      setShowPin(false);
      setPin('');
      navigation.navigate('PaymentFailed', {
        message: err.message ?? 'Payment could not be initiated.',
      });
    } finally {
      setLoading(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  if (showPin) {
    return (
      <View style={{
        flex:              1,
        backgroundColor:   colors.background,
        paddingHorizontal: spacing[6],
        paddingTop:        60,
        gap:               spacing[6],
      }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
          textAlign:  'center',
        }}>
          Enter your 4-digit PIN
        </Text>
        <Text style={{ color: colors.textSecondary, textAlign: 'center', fontSize: typography.size.sm }}>
          Sending {amount ? `Ksh ${Number(amount).toLocaleString()}` : ''} to {getRecipientDisplay()}
        </Text>
        <PinPad
          pin={pin}
          onDigit={(d) => setPin((p) => p + d)}
          onBackspace={() => setPin((p) => p.slice(0, -1))}
        />
        <Button
          label="Cancel"
          onPress={() => { setShowPin(false); setPin(''); }}
          variant="ghost"
          fullWidth
        />
      </View>
    );
  }

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
        {/* Title */}
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
        }}>
          Send Money
        </Text>

        {/* Tab selector */}
        <View style={{
          flexDirection:   'row',
          backgroundColor: colors.surfaceElevated,
          borderRadius:    12,
          padding:         4,
        }}>
          {(['PHONE', 'TILL', 'PAYBILL'] as Tab[]).map((tab) => (
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
                fontWeight: activeTab === tab
                  ? typography.weight.semibold
                  : typography.weight.regular,
                fontSize: typography.size.sm,
              }}>
                {tab === 'PHONE' ? 'Phone' : tab === 'TILL' ? 'Till' : 'Paybill'}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* Recipient input */}
        {activeTab === 'PHONE' && (
          <TextInput
            value={phone}
            onChangeText={setPhone}
            placeholder="07XX XXX XXX"
            placeholderTextColor={colors.textMuted}
            keyboardType="phone-pad"
            style={{
              backgroundColor:   colors.inputBackground,
              borderWidth:       1.5,
              borderColor:       colors.inputBorder,
              borderRadius:      12,
              padding:           spacing[4],
              color:             colors.textPrimary,
              fontSize:          typography.size.md,
            }}
          />
        )}
        {activeTab === 'TILL' && (
          <TextInput
            value={till}
            onChangeText={setTill}
            placeholder="Till Number"
            placeholderTextColor={colors.textMuted}
            keyboardType="numeric"
            style={{
              backgroundColor: colors.inputBackground,
              borderWidth:     1.5,
              borderColor:     colors.inputBorder,
              borderRadius:    12,
              padding:         spacing[4],
              color:           colors.textPrimary,
              fontSize:        typography.size.md,
            }}
          />
        )}
        {activeTab === 'PAYBILL' && (
          <View style={{ gap: spacing[3] }}>
            <TextInput
              value={paybill}
              onChangeText={setPaybill}
              placeholder="Paybill Number"
              placeholderTextColor={colors.textMuted}
              keyboardType="numeric"
              style={{
                backgroundColor: colors.inputBackground,
                borderWidth:     1.5,
                borderColor:     colors.inputBorder,
                borderRadius:    12,
                padding:         spacing[4],
                color:           colors.textPrimary,
                fontSize:        typography.size.md,
              }}
            />
            <TextInput
              value={accountNo}
              onChangeText={setAccountNo}
              placeholder="Account Number"
              placeholderTextColor={colors.textMuted}
              keyboardType="default"
              style={{
                backgroundColor: colors.inputBackground,
                borderWidth:     1.5,
                borderColor:     colors.inputBorder,
                borderRadius:    12,
                padding:         spacing[4],
                color:           colors.textPrimary,
                fontSize:        typography.size.md,
              }}
            />
          </View>
        )}

        {/* Recent recipients */}
        {recipients && recipients.length > 0 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View style={{ flexDirection: 'row', gap: spacing[2] }}>
              {recipients.map((r) => (
                <Pressable
                  key={r.id}
                  onPress={() => {
                    if (r.type === 'PHONE')   setPhone(r.phone ?? '');
                    if (r.type === 'TILL')    setTill(r.till ?? '');
                    if (r.type === 'PAYBILL') setPaybill(r.paybill ?? '');
                    setCategory(r.category);
                    setActiveTab(r.type);
                  }}
                  style={{
                    alignItems:      'center',
                    gap:             spacing[1],
                    padding:         spacing[2],
                  }}
                >
                  <View style={{
                    width:           44,
                    height:          44,
                    borderRadius:    22,
                    backgroundColor: colors.primary + '20',
                    alignItems:      'center',
                    justifyContent:  'center',
                  }}>
                    <Text style={{
                      color:      colors.primary,
                      fontWeight: typography.weight.bold,
                    }}>
                      {r.nickname.slice(0, 2).toUpperCase()}
                    </Text>
                  </View>
                  <Text style={{
                    color:    colors.textSecondary,
                    fontSize: typography.size.xs,
                  }}>
                    {r.nickname}
                  </Text>
                </Pressable>
              ))}
            </View>
          </ScrollView>
        )}

        {/* Amount input */}
        <View style={{ alignItems: 'center' }}>
          <TextInput
            value={amount}
            onChangeText={setAmount}
            placeholder="0"
            placeholderTextColor={colors.textMuted}
            keyboardType="numeric"
            style={{
              color:      colors.textPrimary,
              fontSize:   typography.size['3xl'],
              fontWeight: typography.weight.bold,
              textAlign:  'center',
            }}
          />
          <Text style={{ color: colors.textMuted, fontSize: typography.size.sm }}>
            Ksh
          </Text>
        </View>

        {/* Category chips */}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }}>
          {CATEGORIES.map((cat) => {
            const isActive = category === cat;
            return (
              <Pressable
                key={cat}
                onPress={() => setCategory(cat)}
                style={{
                  paddingHorizontal: 14,
                  paddingVertical:   6,
                  borderRadius:      20,
                  borderWidth:       1.5,
                  borderColor:       isActive ? colors.primary : colors.border,
                  backgroundColor:   isActive ? colors.primary : 'transparent',
                }}
              >
                <Text style={{
                  color:      isActive ? '#FFFFFF' : colors.textSecondary,
                  fontSize:   typography.size.sm,
                  fontWeight: typography.weight.medium,
                }}>
                  {cat}
                </Text>
              </Pressable>
            );
          })}
        </View>

        {/* Budget warning */}
        {budgetWarning && (
          <Card elevation="flat" style={{ backgroundColor: colors.gold + '15',
            borderLeftWidth: 4, borderLeftColor: colors.gold }}>
            <Text style={{ color: colors.gold, fontSize: typography.size.sm }}>
              ⚠ {budgetWarning}
            </Text>
          </Card>
        )}

        {/* Budget ok */}
        {budgetOk && (
          <Card elevation="flat" style={{ backgroundColor: colors.accentGreen + '15',
            borderLeftWidth: 4, borderLeftColor: colors.accentGreen }}>
            <Text style={{ color: colors.accentGreen, fontSize: typography.size.sm }}>
              ✓ {budgetOk}
            </Text>
          </Card>
        )}

        {/* Send button */}
        <Button
          label={
            canProceed()
              ? `Send Ksh ${Number(amount || 0).toLocaleString()} to ${getRecipientDisplay() || '...'}`
              : 'Enter recipient and amount'
          }
          onPress={() => setShowPin(true)}
          variant="primary"
          fullWidth
          disabled={!canProceed()}
          loading={loading}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
