import React from 'react';
import { Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';

export default function PaymentSuccessScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <View style={{
      flex:              1,
      backgroundColor:   colors.accentGreen,
      alignItems:        'center',
      justifyContent:    'center',
      paddingHorizontal: spacing[6],
      gap:               spacing[6],
    }}>
      <StatusBar style="light" />

      {/* Success icon */}
      <Text style={{ fontSize: 80 }}>✅</Text>

      <View style={{ alignItems: 'center', gap: spacing[2] }}>
        <Text style={{
          color:      '#FFFFFF',
          fontSize:   typography.size['2xl'],
          fontWeight: typography.weight.bold,
          textAlign:  'center',
        }}>
          Payment Successful
        </Text>
        <Text style={{
          color:     'rgba(255,255,255,0.7)',
          fontSize:  typography.size.base,
          textAlign: 'center',
        }}>
          Your payment was completed successfully.
        </Text>
      </View>

      <View style={{ width: '100%', gap: spacing[3] }}>
        <Button
          label="Done"
          onPress={() => navigation.navigate('HomeTab')}
          variant="outline"
          fullWidth
          style={{ borderColor: '#FFFFFF' }}
        />
        <Button
          label="New Payment"
          onPress={() => navigation.navigate('PaymentMain')}
          variant="ghost"
          fullWidth
        />
      </View>
    </View>
  );
}
