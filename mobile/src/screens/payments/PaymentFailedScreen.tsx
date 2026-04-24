import React from 'react';
import { Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';

export default function PaymentFailedScreen({ route, navigation }: { route: any; navigation: any }) {
  const { message } = route.params ?? {};
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();

  return (
    <View style={{
      flex:              1,
      backgroundColor:   colors.danger,
      alignItems:        'center',
      justifyContent:    'center',
      paddingHorizontal: spacing[6],
      gap:               spacing[6],
    }}>
      <StatusBar style="light" />

      {/* Failed icon */}
      <Text style={{ fontSize: 80 }}>❌</Text>

      <View style={{ alignItems: 'center', gap: spacing[2] }}>
        <Text style={{
          color:      '#FFFFFF',
          fontSize:   typography.size['2xl'],
          fontWeight: typography.weight.bold,
          textAlign:  'center',
        }}>
          Payment Failed
        </Text>
        <Text style={{
          color:     'rgba(255,255,255,0.8)',
          fontSize:  typography.size.base,
          textAlign: 'center',
        }}>
          {message ?? 'Something went wrong. Please try again.'}
        </Text>
      </View>

      <View style={{ width: '100%', gap: spacing[3] }}>
        <Button
          label="Try Again"
          onPress={() => navigation.navigate('PaymentMain')}
          variant="outline"
          fullWidth
          style={{ borderColor: '#FFFFFF' }}
        />
        <Button
          label="Go Home"
          onPress={() => navigation.navigate('HomeTab')}
          variant="ghost"
          fullWidth
        />
      </View>
    </View>
  );
}
