import React, { useState } from 'react';
import {
  FlatList,
  Modal,
  Pressable,
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
import { savingsService } from '../../services/savings.service';

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE')}`;
}

// ─── Contribute Modal ─────────────────────────────────────────────────────────

function ContributeModal({
  goalId,
  visible,
  onClose,
}: {
  goalId:  string;
  visible: boolean;
  onClose: () => void;
}) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('');

  const { mutate: contribute, isPending } = useMutation({
    mutationFn: () => savingsService.contribute(goalId, Number(amount)),
    onSuccess:  () => {
      queryClient.invalidateQueries({ queryKey: ['goal', goalId] });
      queryClient.invalidateQueries({ queryKey: ['goalHistory', goalId] });
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      onClose();
      setAmount('');
    },
  });

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
            Add Contribution
          </Text>

          <TextInput
            value={amount}
            onChangeText={setAmount}
            placeholder="Amount (Ksh)"
            placeholderTextColor={colors.textMuted}
            keyboardType="numeric"
            style={{
              backgroundColor: colors.inputBackground,
              borderWidth:     1.5,
              borderColor:     colors.inputBorder,
              borderRadius:    12,
              padding:         spacing[4],
              color:           colors.textPrimary,
              fontSize:        typography.size.xl,
              textAlign:       'center',
            }}
          />

          <View style={{ flexDirection: 'row', gap: spacing[3] }}>
            <Button
              label="Cancel"
              onPress={onClose}
              variant="ghost"
              fullWidth
            />
            <Button
              label="Add"
              onPress={() => contribute()}
              variant="primary"
              fullWidth
              loading={isPending}
              disabled={!amount || Number(amount) <= 0}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function GoalDetailScreen({
  route,
  navigation,
}: {
  route:      any;
  navigation: any;
}) {
  const { goalId }  = route.params;
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();
  const [showContribute, setShowContribute] = useState(false);

  const { data: goal, isLoading: goalLoading } = useQuery({
    queryKey: ['goal', goalId],
    queryFn:  () => savingsService.getGoals().then(
      (goals) => goals.find((g) => g.id === goalId)
    ),
  });

  const { data: history, isLoading: historyLoading } = useQuery({
    queryKey: ['goalHistory', goalId],
    queryFn:  () => savingsService.getGoalHistory(goalId),
  });

  const { mutate: deleteGoal, isPending: deleting } = useMutation({
    mutationFn: () => savingsService.deleteGoal(goalId),
    onSuccess:  () => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      navigation.goBack();
    },
  });

  if (goalLoading) {
    return (
      <View style={{
        flex:            1,
        backgroundColor: colors.background,
        padding:         spacing[4],
        paddingTop:      60,
      }}>
        <SkeletonCard />
      </View>
    );
  }

  if (!goal) return null;

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      <FlatList
        data={history ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{
          paddingHorizontal: spacing[4],
          paddingTop:        60,
          paddingBottom:     100,
          gap:               spacing[4],
        }}
        ListHeaderComponent={() => (
          <View style={{ gap: spacing[4] }}>
            {/* Back */}
            <Pressable
              onPress={() => navigation.goBack()}
              style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}
            >
              <Text style={{ color: colors.primary, fontSize: typography.size.lg }}>←</Text>
              <Text style={{ color: colors.primary, fontSize: typography.size.base }}>Back</Text>
            </Pressable>

            {/* Goal header */}
            <Card elevation="raised" style={{ alignItems: 'center', gap: spacing[4] }}>
              <Text style={{ fontSize: 56 }}>{goal.emoji}</Text>
              <Text style={{
                color:      colors.textPrimary,
                fontSize:   typography.size.xl,
                fontWeight: typography.weight.bold,
                textAlign:  'center',
              }}>
                {goal.name}
              </Text>

              {/* Progress circle placeholder */}
              <View style={{
                width:           120,
                height:          120,
                borderRadius:    60,
                borderWidth:     12,
                borderColor:     colors.gold,
                alignItems:      'center',
                justifyContent:  'center',
              }}>
                <Text style={{
                  color:      colors.textPrimary,
                  fontSize:   typography.size.xl,
                  fontWeight: typography.weight.bold,
                }}>
                  {Math.round(goal.percentComplete)}%
                </Text>
              </View>

              {/* Stats */}
              <View style={{ flexDirection: 'row', gap: spacing[4], width: '100%' }}>
                {[
                  { label: 'Target',    value: formatKsh(goal.targetAmount),  color: colors.textPrimary },
                  { label: 'Saved',     value: formatKsh(goal.currentAmount),  color: colors.accentGreen },
                  { label: 'Remaining', value: formatKsh(Math.max(goal.targetAmount - goal.currentAmount, 0)),
                    color: colors.gold },
                  { label: 'Days Left', value: `${goal.daysRemaining}d`,       color: colors.secondary },
                ].map((stat) => (
                  <View key={stat.label} style={{ flex: 1, alignItems: 'center' }}>
                    <Text style={{
                      color:      stat.color,
                      fontSize:   typography.size.sm,
                      fontWeight: typography.weight.bold,
                    }}>
                      {stat.value}
                    </Text>
                    <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
                      {stat.label}
                    </Text>
                  </View>
                ))}
              </View>

              {/* Status badge */}
              <Badge
                label={goal.status === 'COMPLETED' ? 'Completed 🎉' : 'Active'}
                variant={goal.status === 'COMPLETED' ? 'success' : 'info'}
              />
            </Card>

            {/* Contribute button */}
            {goal.status === 'ACTIVE' && (
              <Button
                label="Add Contribution"
                onPress={() => setShowContribute(true)}
                variant="primary"
                fullWidth
                style={{ backgroundColor: colors.accentGreen }}
              />
            )}

            {/* History header */}
            <Text style={{
              color:      colors.textPrimary,
              fontSize:   typography.size.md,
              fontWeight: typography.weight.bold,
            }}>
              Contribution History
            </Text>

            {historyLoading && <SkeletonCard />}
          </View>
        )}
        ListEmptyComponent={() =>
          !historyLoading ? (
            <Text style={{
              color:     colors.textMuted,
              fontSize:  typography.size.sm,
              textAlign: 'center',
              paddingVertical: spacing[4],
            }}>
              No contributions yet.
            </Text>
          ) : null
        }
        renderItem={({ item }) => (
          <View style={{
            flexDirection:  'row',
            justifyContent: 'space-between',
            alignItems:     'center',
            paddingVertical: spacing[3],
            borderBottomWidth: 1,
            borderBottomColor: colors.border,
          }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
              <Text style={{ fontSize: 16 }}>✅</Text>
              <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
                {new Date(item.createdAt).toLocaleDateString('en-KE', {
                  day: 'numeric', month: 'short', year: 'numeric',
                })}
              </Text>
            </View>
            <Text style={{
              color:      colors.accentGreen,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.semibold,
            }}>
              +{formatKsh(item.amount)}
            </Text>
          </View>
        )}
        ListFooterComponent={() => (
          <View style={{ gap: spacing[3], marginTop: spacing[6] }}>
            <Button
              label="Archive Goal"
              onPress={() => deleteGoal()}
              variant="outline"
              fullWidth
              loading={deleting}
            />
          </View>
        )}
      />

      <ContributeModal
        goalId={goalId}
        visible={showContribute}
        onClose={() => setShowContribute(false)}
      />
    </View>
  );
}
