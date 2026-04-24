import React from 'react';
import { View } from 'react-native';
import Svg, { Path, Circle, Text, G } from 'react-native-svg';
import { useColors } from '../../theme';

// ─── Types ───────────────────────────────────────────────────────────────────

type LogoSize    = 'sm' | 'md' | 'lg' | 'xl';
type LogoVariant = 'full' | 'icon' | 'wordmark';

type Props = {
    size?:        LogoSize;
    variant?:     LogoVariant;
    showTagline?: boolean;
    color?:       string; // overrides primary color — use on dark backgrounds
};

// ─── Size Scale ──────────────────────────────────────────────────────────────

const ICON_SIZE: Record<LogoSize, number> = {
  sm: 32,
  md: 48,
  lg: 64,
  xl: 96,
};

// ─── Icon — Stylized 'A' with coin at crossbar ────────────────────────────────

function AkibaIcon({ size, primaryColor, goldColor }: {
  size:         number;
  primaryColor: string;
  goldColor:    string;
}) {
  const s = size;
  const cx = s / 2;      // horizontal center
  const coinR = s * 0.1; // coin radius scales with icon size

  return (
    <Svg width={s} height={s} viewBox="0 0 100 100">
      {/* The 'A' shape — two diagonal legs meeting at a peak */}
      <Path
        d="M50 8 L88 88 H68 L58 64 H42 L32 88 H12 Z"
        fill={primaryColor}
        strokeLinejoin="round"
      />
      {/* Cutout inside the A to make it hollow */}
      <Path
        d="M50 28 L68 76 H32 Z"
        fill="white"
        opacity={0.15}
      />
      {/* Gold coin at the crossbar of the A */}
      <Circle
        cx={50}
        cy={58}
        r={12}
        fill={goldColor}
      />
      {/* Shilling symbol inside the coin */}
      <Text
        x={50}
        y={63}
        textAnchor="middle"
        fontSize={14}
        fontWeight="bold"
        fill="white"
      >
        S
      </Text>
    </Svg>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function AkibaLogo({
                                      size        = 'md',
                                      variant     = 'full',
                                      showTagline = false,
                                      color,
                                  }: Props) {
    const colors      = useColors();
    const primaryColor = color ?? colors.primary;
  const iconSize = ICON_SIZE[size];

  // Font sizes scale with icon size
  const wordmarkSize = iconSize * 0.35;
  const taglineSize  = iconSize * 0.18;

  const icon = (
    <AkibaIcon
      size={iconSize}
      primaryColor={primaryColor}
      goldColor={colors.gold}
    />
  );

  // ── icon only ──────────────────────────────────────────────────────────────
  if (variant === 'icon') {
    return icon;
  }

  // ── icon stacked above wordmark ────────────────────────────────────────────
  if (variant === 'wordmark') {
    return (
      <View style={{ alignItems: 'center' }}>
        {icon}
        <Svg
          width={iconSize * 1.8}
          height={wordmarkSize + 8}
          viewBox={`0 0 ${iconSize * 1.8} ${wordmarkSize + 8}`}
          style={{ marginTop: 6 }}
        >
          <Text
            x={(iconSize * 1.8) / 2}
            y={wordmarkSize}
            textAnchor="middle"
            fontSize={wordmarkSize}
            fontWeight="bold"
            fill={colors.primary}
            letterSpacing={4}
          >
            AKIBA
          </Text>
        </Svg>
        {showTagline && (
          <Svg
            width={iconSize * 3}
            height={taglineSize + 8}
            viewBox={`0 0 ${iconSize * 3} ${taglineSize + 8}`}
            style={{ marginTop: 2 }}
          >
            <Text
              x={(iconSize * 3) / 2}
              y={taglineSize}
              textAnchor="middle"
              fontSize={taglineSize}
              fontStyle="italic"
              fill={colors.gold}
            >
              Every shilling has a story.
            </Text>
          </Svg>
        )}
      </View>
    );
  }

  // ── icon + wordmark side by side (default: 'full') ─────────────────────────
  return (
    <View style={{ alignItems: 'center' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        {icon}
        <Svg
          width={iconSize * 1.8}
          height={wordmarkSize + 8}
          viewBox={`0 0 ${iconSize * 1.8} ${wordmarkSize + 8}`}
        >
          <Text
            x={0}
            y={wordmarkSize}
            fontSize={wordmarkSize}
            fontWeight="bold"
            fill={colors.primary}
            letterSpacing={4}
          >
            AKIBA
          </Text>
        </Svg>
      </View>
      {showTagline && (
        <Svg
          width={iconSize * 3}
          height={taglineSize + 8}
          viewBox={`0 0 ${iconSize * 3} ${taglineSize + 8}`}
          style={{ marginTop: 2 }}
        >
          <Text
            x={(iconSize * 3) / 2}
            y={taglineSize}
            textAnchor="middle"
            fontSize={taglineSize}
            fontStyle="italic"
            fill={colors.gold}
          >
            Every shilling has a story.
          </Text>
        </Svg>
      )}
    </View>
  );
}
