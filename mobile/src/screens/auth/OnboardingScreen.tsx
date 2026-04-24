import React, { useRef, useState } from 'react';
import {
  Dimensions,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import Svg, { Circle, Line, Path, Rect } from 'react-native-svg';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button from '../../components/common/Button';
import { AuthStackParams } from '../../navigation/types';

// ─── Types ───────────────────────────────────────────────────────────────────

type Props = NativeStackScreenProps<AuthStackParams, 'Onboarding'>;

const { width: SCREEN_WIDTH } = Dimensions.get('window');

// ─── Slide Data ───────────────────────────────────────────────────────────────

const SLIDES = [
  {
    key:      'import',
    title:    'Import your history',
    subtitle: 'Paste your M-Pesa SMS or upload a bank PDF — Akiba reads it all.',
  },
  {
    key:      'ai',
    title:    'AI tracks every shilling',
    subtitle: 'Personalized insights powered by AI — know exactly where your money goes.',
  },
  {
    key:      'save',
    title:    'Pay smart, save smarter',
    subtitle: 'Make M-Pesa payments and hit your savings goals from one app.',
  },
];

// ─── SVG Illustrations ────────────────────────────────────────────────────────

function ImportIllustration({ color }: { color: string }) {
  return (
    <Svg width={200} height={180} viewBox="0 0 200 180">
      {/* Phone body */}
      <Rect x={70} y={20} width={60} height={100} rx={10} fill={color} opacity={0.9} />
      <Rect x={75} y={30} width={50} height={70} rx={4} fill="white" opacity={0.3} />
      {/* M-Pesa SMS lines */}
      <Rect x={80} y={38} width={35} height={4} rx={2} fill="white" opacity={0.8} />
      <Rect x={80} y={48} width={28} height={4} rx={2} fill="white" opacity={0.6} />
      <Rect x={80} y={58} width={32} height={4} rx={2} fill="white" opacity={0.6} />
      {/* Floating bank icon left */}
      <Rect x={20} y={50} width={36} height={36} rx={8} fill={color} opacity={0.6} />
      <Rect x={28} y={60} width={20} height={3} rx={1} fill="white" opacity={0.8} />
      <Rect x={28} y={67} width={20} height={3} rx={1} fill="white" opacity={0.6} />
      <Rect x={28} y={74} width={20} height={3} rx={1} fill="white" opacity={0.6} />
      {/* Floating coin right */}
      <Circle cx={160} cy={60} r={20} fill="#D97706" opacity={0.8} />
      <Line x1={160} y1={50} x2={160} y2={70} stroke="white" strokeWidth={2} />
      <Line x1={153} y1={55} x2={167} y2={55} stroke="white" strokeWidth={2} />
    </Svg>
  );
}

function AiIllustration({ color }: { color: string }) {
  return (
    <Svg width={200} height={180} viewBox="0 0 200 180">
      {/* Brain outline */}
      <Path
        d="M100 30 C60 30 40 55 40 80 C40 105 55 120 70 125 L70 140 L130 140 L130 125 C145 120 160 105 160 80 C160 55 140 30 100 30Z"
        fill={color}
        opacity={0.85}
      />
      {/* Brain detail lines */}
      <Path d="M80 60 Q100 50 120 60" stroke="white" strokeWidth={2} fill="none" opacity={0.6} />
      <Path d="M70 80 Q100 70 130 80" stroke="white" strokeWidth={2} fill="none" opacity={0.6} />
      <Path d="M75 100 Q100 90 125 100" stroke="white" strokeWidth={2} fill="none" opacity={0.6} />
      {/* Chart trend lines flowing out */}
      <Path d="M40 150 L70 130 L100 140 L130 110 L160 120" stroke="#D97706" strokeWidth={3} fill="none" />
      <Circle cx={160} cy={120} r={5} fill="#D97706" />
    </Svg>
  );
}

function SaveIllustration({ color }: { color: string }) {
  return (
    <Svg width={200} height={180} viewBox="0 0 200 180">
      {/* Upward arrow */}
      <Path d="M100 20 L130 60 L115 60 L115 120 L85 120 L85 60 L70 60 Z" fill={color} opacity={0.9} />
      {/* Savings bar */}
      <Rect x={40} y={130} width={120} height={16} rx={8} fill="white" opacity={0.2} />
      <Rect x={40} y={130} width={85}  height={16} rx={8} fill="#10B981" opacity={0.8} />
      {/* Gold coins */}
      <Circle cx={55}  cy={108} r={10} fill="#D97706" opacity={0.9} />
      <Circle cx={145} cy={108} r={10} fill="#D97706" opacity={0.9} />
      <Circle cx={100} cy={155} r={12} fill="#D97706" opacity={0.7} />
    </Svg>
  );
}

const ILLUSTRATIONS = [ImportIllustration, AiIllustration, SaveIllustration];

// ─── Dot Indicator ────────────────────────────────────────────────────────────

function DotIndicator({
  count,
  active,
  activeColor,
  inactiveColor,
}: {
  count:         number;
  active:        number;
  activeColor:   string;
  inactiveColor: string;
}) {
  return (
    <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
      {Array.from({ length: count }).map((_, i) => {
        const isActive = i === active;
        return (
          <Animated.View
            key={i}
            style={{
              height:          8,
              width:           isActive ? 28 : 8,
              borderRadius:    4,
              backgroundColor: isActive ? activeColor : inactiveColor,
            }}
          />
        );
      })}
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function OnboardingScreen({ navigation }: Props) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const [currentSlide, setCurrentSlide] = useState(0);
  const scrollRef = useRef<ScrollView>(null);
  const isLast    = currentSlide === SLIDES.length - 1;

  const handleFinish = async () => {
    await AsyncStorage.setItem('@akiba_first_launch', 'done');
    navigation.replace('Login');
  };

  const handleNext = () => {
    if (isLast) {
      handleFinish();
      return;
    }
    const next = currentSlide + 1;
    scrollRef.current?.scrollTo({ x: SCREEN_WIDTH * next, animated: true });
    setCurrentSlide(next);
  };

  const handleScroll = (e: any) => {
    const slide = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
    setCurrentSlide(slide);
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      {/* Skip button */}
      {!isLast && (
        <Pressable
          onPress={handleFinish}
          style={{
            position: 'absolute',
            top:      56,
            right:    spacing[6],
            zIndex:   10,
          }}
        >
          <Text style={{
            color:      colors.textSecondary,
            fontSize:   typography.size.base,
            fontWeight: typography.weight.medium,
          }}>
            Skip
          </Text>
        </Pressable>
      )}

      {/* Slides */}
      <ScrollView
        ref={scrollRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={handleScroll}
        scrollEventThrottle={16}
        style={{ flex: 1 }}
      >
        {SLIDES.map((slide, index) => {
          const Illustration = ILLUSTRATIONS[index];
          return (
            <View
              key={slide.key}
              style={{
                width:           SCREEN_WIDTH,
                flex:            1,
                alignItems:      'center',
                justifyContent:  'center',
                paddingHorizontal: spacing[8],
                gap:             spacing[6],
              }}
            >
              {/* SVG illustration */}
              <View style={{
                backgroundColor: colors.primary + '15',
                borderRadius:    120,
                padding:         spacing[8],
              }}>
                <Illustration color={colors.primary} />
              </View>

              {/* Text */}
              <View style={{ alignItems: 'center', gap: spacing[3] }}>
                <Text style={{
                  color:      colors.textPrimary,
                  fontSize:   typography.size.xl,
                  fontWeight: typography.weight.bold,
                  textAlign:  'center',
                }}>
                  {slide.title}
                </Text>
                <Text style={{
                  color:      colors.textSecondary,
                  fontSize:   typography.size.base,
                  textAlign:  'center',
                  lineHeight: typography.size.base * typography.lineHeight.relaxed,
                }}>
                  {slide.subtitle}
                </Text>
              </View>
            </View>
          );
        })}
      </ScrollView>

      {/* Bottom controls */}
      <View style={{
        paddingHorizontal: spacing[6],
        paddingBottom:     spacing[10],
        alignItems:        'center',
        gap:               spacing[6],
      }}>
        <DotIndicator
          count={SLIDES.length}
          active={currentSlide}
          activeColor={colors.primary}
          inactiveColor={colors.border}
        />
        <Button
          label={isLast ? 'Get Started' : 'Next'}
          onPress={handleNext}
          variant="primary"
          fullWidth
        />
      </View>
    </View>
  );
}
