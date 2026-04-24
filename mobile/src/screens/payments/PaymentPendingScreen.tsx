import React, { useEffect, useRef } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useColors, useSpacing, useTypography } from '../../theme';
import { paymentService } from '../../services/payment.service';

export default function PaymentPendingScreen({ route, navigation }: { route: any; navigation: any }) {
  const { paymentId, phone } = route.params;
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timeoutRef  = useRef<ReturnType<typeof setTimeout>  | null>(null);

  useEffect(() => {
    // Poll every 3 seconds
    intervalRef.current = setInterval(async () => {
      try {
        const status = await paymentService.getPaymentStatus(paymentId);
        if (status.status === 'COMPLETED') {
          cleanup();
          navigation.replace('PaymentSuccess', { paymentId });
        } else if (status.status === 'FAILED') {
          cleanup();
          navigation.replace('PaymentFailed', { message: status.message ?? 'Payment failed.' });
        }
      } catch {
        // Polling errors are non-fatal — keep trying
      }
    }, 3000);

    // Timeout after 120 seconds
    timeoutRef.current = setTimeout(() => {
      cleanup();
      navigation.replace('PaymentFailed', { message: 'Payment timed out. Please check your M-Pesa.' });
    }, 120_000);

    return cleanup;
  }, []);

  const cleanup = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (timeoutRef.current)  clearTimeout(timeoutRef.current);
  };

  return (
    <View style={{
      flex:            1,
      backgroundColor: colors.primary,
      alignItems:      'center',
      justifyContent:  'center',
      gap:             spacing[6],
      paddingHorizontal: spacing[6],
    }}>
      <StatusBar style="light" />

      {/* Pulsing ring */}
      <View style={{ alignItems: 'center', justifyContent: 'center' }}>
        <View style={{
          width:           100,
          height:          100,
          borderRadius:    50,
          backgroundColor: 'rgba(255,255,255,0.2)',
          alignItems:      'center',
          justifyContent:  'center',
        }}>
          <View style={{
            width:           72,
            height:          72,
            borderRadius:    36,
            backgroundColor: 'rgba(255,255,255,0.3)',
            alignItems:      'center',
            justifyContent:  'center',
          }}>
            <Text style={{ fontSize: 32 }}>📱</Text>
          </View>
        </View>
      </View>

      <ActivityIndicator size="large" color="rgba(255,255,255,0.8)" />

      <View style={{ alignItems: 'center', gap: spacing[2] }}>
        <Text style={{
          color:      '#FFFFFF',
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
          textAlign:  'center',
        }}>
          Check your M-Pesa
        </Text>
        <Text style={{
          color:     'rgba(255,255,255,0.7)',
          fontSize:  typography.size.base,
          textAlign: 'center',
        }}>
          on {phone}
        </Text>
        <Text style={{
          color:     'rgba(255,255,255,0.6)',
          fontSize:  typography.size.sm,
          textAlign: 'center',
          marginTop: spacing[2],
        }}>
          Enter your M-Pesa PIN when prompted
        </Text>
      </View>

      <Pressable onPress={() => { cleanup(); navigation.navigate('PaymentMain'); }}>
        <Text style={{
          color:      'rgba(255,255,255,0.6)',
          fontSize:   typography.size.sm,
          fontWeight: typography.weight.medium,
        }}>
          Cancel
        </Text>
      </Pressable>
    </View>
  );
}
