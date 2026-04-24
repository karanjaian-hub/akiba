import React, { useState } from 'react';
import {
  FlatList,
  Modal,
  Pressable,
  RefreshControl,
  Text,
  TextInput,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import Card   from '../../components/common/Card';
import Badge  from '../../components/common/Badge';
import Button from '../../components/common/Button';
import { SkeletonCard } from '../../components/skeleton/SkeletonLoader';
import { savingsService, SavingsGoal } from '../../services/savings.service';

// ─── Constants ────────────────────────────────────────────────────────────────

const EMOJIS = [
  '💻','🚗','🏠','✈️','💊','🎓','💍','👶',
  '📱','🌍','🏋️','💰','🎁','🛒','🐕','🎵',
];

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE')}`;
}

// ─── Goal Status Badge ────────────────────────────────────────────────────────

function getStatusBadge(goal: SavingsGoal): { label: string; variant: any } {
  if (goal.status === 'COMPLETED')       return { label: 'Completed',   variant: 'success' };
  if (goal.percentComplete >= 80)        return { label: 'Almost there', variant: 'info'    };
  if (goal.daysRemaining < 7)            return { label: 'Behind pace', variant: 'warning'  };
  return                                        { label: 'On track',    variant: 'success'  };
}

// ─── Goal Card ────────────────────────────────────────────────────────────────

function GoalCard({
  goal,
  onPress,
}: {
  goal:    SavingsGoal;
  onPress: () => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const badge      = getStatusBadge(goal);

  return (
    <Card elevation="raised" onPress={onPress} style={{ gap: spacing[3] }}>
      {/* Top row */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
          <Text style={{ fontSize: 28 }}>{goal.emoji}</Text>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.base,
            fontWeight: typography.weight.bold,
          }}>
            {goal.name}
          </Text>
        </View>
        <Badge label={badge.label} variant={badge.variant} size="sm" />
      </View>

      {/* Progress bar */}
      <View style={{
        height:          8,
        backgroundColor: colors.border,
        borderRadius:    4,
        overflow:        'hidden',
      }}>
        <View style={{
          height:          8,
          width:           `${Math.min(goal.percentComplete, 100)}%`,
          backgroundColor: colors.gold,
          borderRadius:    4,
        }} />
      </View>

      {/* Stats */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
          {formatKsh(goal.currentAmount)} / {formatKsh(goal.targetAmount)}
          {'  '}
          <Text style={{ color: colors.gold, fontWeight: typography.weight.semibold }}>
            {Math.round(goal.percentComplete)}%
          </Text>
        </Text>
        <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
          {goal.daysRemaining}d left
        </Text>
      </View>

      {/* Weekly needed */}
      <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
        {formatKsh(goal.weeklyNeeded)}/week needed to reach goal
      </Text>
    </Card>
  );
}

// ─── Add Goal Modal ───────────────────────────────────────────────────────────

function AddGoalModal({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();

  const [name,          setName]          = useState('');
  const [targetAmount,  setTargetAmount]  = useState('');
  const [deadline,      setDeadline]      = useState('');
  const [selectedEmoji, setSelectedEmoji] = useState('💰');

  const { mutate: createGoal, isPending } = useMutation({
    mutationFn: () => savingsService.createGoal({
      name,
      emoji:        selectedEmoji,
      targetAmount: Number(targetAmount),
      deadline,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      onClose();
      setName('');
      setTargetAmount('');
      setDeadline('');
      setSelectedEmoji('💰');
    },
  });

  const canCreate = !!name.trim() && Number(targetAmount) > 0 && !!deadline;

  return (
    <Modal visible={visible} transparent animationType="slide">
      <View style={{
        flex:            1,
        backgroundColor: 'rgba(0,0,0,0.5)',
        justifyContent:  'flex-end',
      }}>
        <View style={{
          backgroundColor:      colors.surface,
          borderTopLeftRadius:  20,
          borderTopRightRadius: 20,
          padding:              spacing[6],
          gap:                  spacing[5],
        }}>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.lg,
            fontWeight: typography.weight.bold,
          }}>
            New Savings Goal
          </Text>

          {/* Goal name */}
          <TextInput
            value={name}
            onChangeText={setName}
            placeholder="Goal name (e.g. New Laptop)"
            placeholderTextColor={colors.textMuted}
            style={{
              backgroundColor:   colors.inputBackground,
              borderWidth:       1.5,
              borderColor:       colors.inputBorder,
              borderRadius:      12,
              padding:           spacing[4],
              color:             colors.textPrimary,
              fontSize:          typography.size.base,
            }}
          />

          {/* Target amount */}
          <TextInput
            value={targetAmount}
            onChangeText={setTargetAmount}
            placeholder="Target amount (Ksh)"
            placeholderTextColor={colors.textMuted}
            keyboardType="numeric"
            style={{
              backgroundColor: colors.inputBackground,
              borderWidth:     1.5,
              borderColor:     colors.inputBorder,
              borderRadius:    12,
              padding:         spacing[4],
              color:           colors.textPrimary,
              fontSize:        typography.size.base,
            }}
          />

          {/* Deadline */}
          <TextInput
            value={deadline}
            onChangeText={setDeadline}
            placeholder="Deadline (YYYY-MM-DD)"
            placeholderTextColor={colors.textMuted}
            style={{
              backgroundColor: colors.inputBackground,
              borderWidth:     1.5,
              borderColor:     colors.inputBorder,
              borderRadius:    12,
              padding:         spacing[4],
              color:           colors.textPrimary,
              fontSize:        typography.size.base,
            }}
          />

          {/* Emoji picker */}
          <View style={{ gap: spacing[2] }}>
            <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
              Choose an icon
            </Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }}>
              {EMOJIS.map((emoji) => (
                <Pressable
                  key={emoji}
                  onPress={() => setSelectedEmoji(emoji)}
                  style={{
                    width:           44,
                    height:          44,
                    borderRadius:    12,
                    borderWidth:     1.5,
                    borderColor:     selectedEmoji === emoji
                      ? colors.primary
                      : colors.border,
                    backgroundColor: selectedEmoji === emoji
                      ? colors.primary + '15'
                      : 'transparent',
                    alignItems:      'center',
                    justifyContent:  'center',
                  }}
                >
                  <Text style={{ fontSize: 22 }}>{emoji}</Text>
                </Pressable>
              ))}
            </View>
          </View>

          {/* Buttons */}
          <View style={{ flexDirection: 'row', gap: spacing[3] }}>
            <Button
              label="Cancel"
              onPress={onClose}
              variant="ghost"
              fullWidth
            />
            <Button
              label="Create Goal"
              onPress={() => createGoal()}
              variant="primary"
              fullWidth
              loading={isPending}
              disabled={!canCreate}
              style={{ backgroundColor: colors.accentGreen }}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// ─── Summary Card ─────────────────────────────────────────────────────────────

function SummaryCard({ goals }: { goals: SavingsGoal[] }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const totalSaved  = goals.reduce((sum, g) => sum + g.currentAmount, 0);
  const activeCount = goals.filter((g) => g.status === 'ACTIVE').length;

  return (
    <View style={{
      backgroundColor: colors.accentGreen,
      borderRadius:    20,
      padding:         spacing[6],
      gap:             spacing[2],
    }}>
      <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.sm }}>
        Total Saved
      </Text>
      <Text style={{
        color:      '#FFFFFF',
        fontSize:   typography.size['2xl'],
        fontWeight: typography.weight.bold,
      }}>
        {formatKsh(totalSaved)}
      </Text>
      <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.sm }}>
        across {activeCount} active {activeCount === 1 ? 'goal' : 'goals'}
      </Text>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function GoalsScreen({ navigation }: { navigation: any }) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);

  const { data: goals, isLoading, isRefetching, refetch } = useQuery({
    queryKey: ['goals'],
    queryFn:  savingsService.getGoals,
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      <FlatList
        data={goals ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{
          paddingHorizontal: spacing[4],
          paddingTop:        60,
          paddingBottom:     100,
          gap:               spacing[4],
        }}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetch}
            colors={[colors.accentGreen]}
            tintColor={colors.accentGreen}
          />
        }
        ListHeaderComponent={() => (
          <View style={{ gap: spacing[4], marginBottom: spacing[2] }}>
            <Text style={{
              color:      colors.textPrimary,
              fontSize:   typography.size.xl,
              fontWeight: typography.weight.bold,
            }}>
              Savings Goals
            </Text>
            {goals && goals.length > 0 && <SummaryCard goals={goals} />}
          </View>
        )}
        ListEmptyComponent={() =>
          isLoading ? (
            <View style={{ gap: spacing[3] }}>
              <SkeletonCard />
              <SkeletonCard />
            </View>
          ) : (
            <View style={{
              alignItems:     'center',
              paddingVertical: spacing[10],
              gap:             spacing[3],
            }}>
              <Text style={{ fontSize: 48 }}>🎯</Text>
              <Text style={{
                color:     colors.textSecondary,
                fontSize:  typography.size.base,
                textAlign: 'center',
              }}>
                No savings goals yet.{'\n'}Tap + to create your first goal.
              </Text>
            </View>
          )
        }
        renderItem={({ item }) => (
          <GoalCard
            goal={item}
            onPress={() => navigation.navigate('GoalDetail', { goalId: item.id })}
          />
        )}
      />

      {/* FAB */}
      <Pressable
        onPress={() => setShowModal(true)}
        style={{
          position:        'absolute',
          bottom:          spacing[8],
          right:           spacing[6],
          width:           56,
          height:          56,
          borderRadius:    28,
          backgroundColor: colors.accentGreen,
          alignItems:      'center',
          justifyContent:  'center',
          shadowColor:     colors.cardShadow,
          shadowOffset:    { width: 0, height: 4 },
          shadowOpacity:   1,
          shadowRadius:    8,
          elevation:       6,
        }}
      >
        <Text style={{ color: '#FFFFFF', fontSize: 28, lineHeight: 32 }}>+</Text>
      </Pressable>

      <AddGoalModal
        visible={showModal}
        onClose={() => setShowModal(false)}
      />
    </View>
  );
}
