import React, { useEffect } from 'react';
import { View } from 'react-native';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withTiming,
  withDelay,
  withSequence,
  withRepeat,
  runOnJS,
} from 'react-native-reanimated';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useColors } from '../../theme';
import AkibaLogo from '../../components/common/AkibaLogo';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'Splash'>;

// ─── Component ───────────────────────────────────────────────────────────────

export default function SplashScreen({ navigation }: Props) {
  const colors = useColors();

  // Animation shared values
  const iconScale   = useSharedValue(0.6);
  const iconOpacity = useSharedValue(0);
  const wordmarkY   = useSharedValue(20);
  const wordmarkOp  = useSharedValue(0);
  const taglineOp   = useSharedValue(0);
  const dot1Scale   = useSharedValue(0.7);
  const dot2Scale   = useSharedValue(0.7);
  const dot3Scale   = useSharedValue(0.7);

  // ── Navigation logic ───────────────────────────────────────────────────────

  const navigateAfterSplash = async () => {
    try {
      const [token, firstLaunch] = await Promise.all([
        AsyncStorage.getItem('@akiba_access_token'),
        AsyncStorage.getItem('@akiba_first_launch'),
      ]);

      if (token) {
        // Logged in — go straight to app (navigation handled by RootNavigator)
        navigation.replace('Login');
        return;
      }

      if (!firstLaunch) {
        // First time ever opening the app
        await AsyncStorage.setItem('@akiba_first_launch', 'done');
        navigation.replace('Onboarding');
        return;
      }

      // Returning user, not logged in
      navigation.replace('Login');

    } catch {
      // AsyncStorage failed — default to Login
      navigation.replace('Login');
    }
  };

  // ── Animation sequence ─────────────────────────────────────────────────────

  useEffect(() => {
    // Step 1 — icon springs in
    iconScale.value   = withSpring(1, { stiffness: 100, damping: 15 });
    iconOpacity.value = withTiming(1, { duration: 400 });

    // Step 2 — wordmark slides up after 400ms
    wordmarkY.value  = withDelay(400, withTiming(0,  { duration: 500 }));
    wordmarkOp.value = withDelay(400, withTiming(1,  { duration: 500 }));

    // Step 3 — tagline fades in after 800ms
    taglineOp.value = withDelay(800, withTiming(1, { duration: 400 }));

    // Step 4 — loading dots pulse after 1200ms
    const dotConfig = { duration: 400 };
    const dotAnim   = withRepeat(
      withSequence(
        withTiming(1.2, dotConfig),
        withTiming(0.7, dotConfig),
      ),
      -1,
      true,
    );

    dot1Scale.value = withDelay(1200, dotAnim);
    dot2Scale.value = withDelay(1400, dotAnim);
    dot3Scale.value = withDelay(1600, dotAnim);

    // Step 5 — navigate after 2500ms
    const timer = setTimeout(() => {
      runOnJS(navigateAfterSplash)();
    }, 2500);

    return () => clearTimeout(timer);
  }, []);

  // ── Animated styles ────────────────────────────────────────────────────────

  const iconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: iconScale.value }],
    opacity:   iconOpacity.value,
  }));

  const wordmarkStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: wordmarkY.value }],
    opacity:   wordmarkOp.value,
  }));

  const taglineStyle = useAnimatedStyle(() => ({
    opacity: taglineOp.value,
  }));

  const dot1Style = useAnimatedStyle(() => ({
    transform: [{ scale: dot1Scale.value }],
  }));
  const dot2Style = useAnimatedStyle(() => ({
    transform: [{ scale: dot2Scale.value }],
  }));
  const dot3Style = useAnimatedStyle(() => ({
    transform: [{ scale: dot3Scale.value }],
  }));

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <View style={{
      flex:            1,
      backgroundColor: colors.primary,
      alignItems:      'center',
      justifyContent:  'center',
      gap:             16,
    }}>
      <StatusBar style="light" />

      {/* Animated icon */}
      <Animated.View style={iconStyle}>
        <AkibaLogo variant="icon" size="xl" color="white" />
      </Animated.View>

      {/* Animated wordmark */}
      <Animated.View style={wordmarkStyle}>
        <AkibaLogo variant="wordmark" size="md" color="white" />
      </Animated.View>

      {/* Animated tagline */}
      <Animated.View style={taglineStyle}>
        <AkibaLogo variant="icon" size="sm" color={colors.gold} />
      </Animated.View>

      {/* Loading dots */}
      <View style={{
        flexDirection: 'row',
        gap:           12,
        marginTop:     32,
      }}>
        <Animated.View style={[dot1Style, {
          width: 10, height: 10, borderRadius: 5,
          backgroundColor: colors.gold,
        }]} />
        <Animated.View style={[dot2Style, {
          width: 10, height: 10, borderRadius: 5,
          backgroundColor: colors.secondary,
        }]} />
        <Animated.View style={[dot3Style, {
          width: 10, height: 10, borderRadius: 5,
          backgroundColor: colors.accentGreen,
        }]} />
      </View>
    </View>
  );
}
