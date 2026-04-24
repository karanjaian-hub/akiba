import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
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
import Button from '../../components/common/Button';
import { SkeletonCard } from '../../components/skeleton/SkeletonLoader';
import { budgetService, Budget } from '../../services/budget.service';

// ─── Constants ────────────────────────────────────────────────────────────────

const CATEGORY_COLORS: Record<string, string> = {
  Food:          '#4C1D95',
  Transport:     '#1E40AF',
  Bills:         '#065F46',
  Entertainment: '#D97706',
  Health:        '#0D9488',
  Other:         '#6B7280',
};

const CATEGORIES = Object.keys(CATEGORY_COLORS);

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE')}`;
}

// ─── Simple Donut Chart ───────────────────────────────────────────────────────
// Pure React Native — no Victory Native needed for a basic donut

function DonutChart({
  budgets,
  totalSpent,
}: {
  budgets:    Budget[];
  totalSpent: number;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <View style={{ alignItems: 'center', gap: spacing[4] }}>
      {/* Center label */}
      <View style={{
        width:           160,
        height:          160,
        borderRadius:    80,
        borderWidth:     20,
        borderColor:     colors.primary,
        alignItems:      'center',
        justifyContent:  'center',
      }}>
        <Text style={{
          color:      colors.textMuted,
          fontSize:   typography.size.xs,
        }}>
          Total spent
        </Text>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.md,
          fontWeight: typography.weight.bold,
        }}>
          {formatKsh(totalSpent)}
        </Text>
      </View>

      {/* Legend */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[3], justifyContent: 'center' }}>
        {budgets.map((b) => (
          <View key={b.id} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[1] }}>
            <View style={{
              width:           10,
              height:          10,
              borderRadius:    5,
              backgroundColor: CATEGORY_COLORS[b.category] ?? colors.primary,
            }} />
            <Text style={{ color: colors.textSecondary, fontSize: typography.size.xs }}>
              {b.category} {Math.round(b.percentage)}%
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

// ─── Budget Card ──────────────────────────────────────────────────────────────

function BudgetCard({ budget }: { budget: Budget }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const [expanded, setExpanded] = useState(false);
  const isOver     = budget.percentage >= 90;
  const catColor   = CATEGORY_COLORS[budget.category] ?? colors.primary;

  return (
    <Card elevation="raised" onPress={() => setExpanded((v) => !v)}>
      {/* Collapsed view */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
          <View style={{
            width:           10,
            height:          10,
            borderRadius:    5,
            backgroundColor: catColor,
          }} />
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.base,
            fontWeight: typography.weight.semibold,
          }}>
            {budget.category}
          </Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            {formatKsh(budget.spent)} / {formatKsh(budget.monthlyLimit)}
          </Text>
          <Badge
            label={`${Math.round(budget.percentage)}%`}
            variant={isOver ? 'danger' : budget.percentage >= 70 ? 'warning' : 'success'}
            size="sm"
          />
        </View>
      </View>

      {/* Expanded view */}
      {expanded && (
        <View style={{ gap: spacing[3], marginTop: spacing[3] }}>
          {/* Progress bar */}
          <View style={{
            height:          8,
            backgroundColor: colors.border,
            borderRadius:    4,
            overflow:        'hidden',
          }}>
            <View style={{
              height:          8,
              width:           `${Math.min(budget.percentage, 100)}%`,
              backgroundColor: isOver ? colors.danger : catColor,
              borderRadius:    4,
            }} />
          </View>

          {/* Stats row */}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <View style={{ alignItems: 'center' }}>
              <Text style={{ color: colors.textMuted,    fontSize: typography.size.xs }}>Spent</Text>
              <Text style={{ color: colors.textPrimary,  fontSize: typography.size.sm,
                fontWeight: typography.weight.semibold }}>
                {formatKsh(budget.spent)}
              </Text>
            </View>
            <View style={{ alignItems: 'center' }}>
              <Text style={{ color: colors.textMuted,    fontSize: typography.size.xs }}>Remaining</Text>
              <Text style={{ color: colors.accentGreen,  fontSize: typography.size.sm,
                fontWeight: typography.weight.semibold }}>
                {formatKsh(Math.max(budget.remaining, 0))}
              </Text>
            </View>
            <View style={{ alignItems: 'center' }}>
              <Text style={{ color: colors.textMuted,    fontSize: typography.size.xs }}>Limit</Text>
              <Text style={{ color: colors.textPrimary,  fontSize: typography.size.sm,
                fontWeight: typography.weight.semibold }}>
                {formatKsh(budget.monthlyLimit)}
              </Text>
            </View>
          </View>
        </View>
      )}
    </Card>
  );
}

// ─── Add Budget Modal ─────────────────────────────────────────────────────────

function AddBudgetModal({
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

  const [selectedCategory, setSelectedCategory] = useState('');
  const [limit,            setLimit]            = useState(5000);

  const { mutate: createBudget, isPending } = useMutation({
    mutationFn: () =>
      budgetService.createBudget({ category: selectedCategory, monthlyLimit: limit }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budgetOverview'] });
      onClose();
      setSelectedCategory('');
      setLimit(5000);
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
          backgroundColor:   colors.surface,
          borderTopLeftRadius:  20,
          borderTopRightRadius: 20,
          padding:           spacing[6],
          gap:               spacing[5],
        }}>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.lg,
            fontWeight: typography.weight.bold,
          }}>
            Add Budget
          </Text>

          {/* Category picker */}
          <View style={{ gap: spacing[2] }}>
            <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
              Category
            </Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing[2] }}>
              {CATEGORIES.map((cat) => {
                const isActive = selectedCategory === cat;
                const catColor = CATEGORY_COLORS[cat];
                return (
                  <Pressable
                    key={cat}
                    onPress={() => setSelectedCategory(cat)}
                    style={{
                      paddingHorizontal: 14,
                      paddingVertical:   6,
                      borderRadius:      20,
                      borderWidth:       1.5,
                      borderColor:       isActive ? catColor : colors.border,
                      backgroundColor:   isActive ? catColor + '20' : 'transparent',
                    }}
                  >
                    <Text style={{
                      color:      isActive ? catColor : colors.textSecondary,
                      fontSize:   typography.size.sm,
                      fontWeight: isActive
                        ? typography.weight.semibold
                        : typography.weight.regular,
                    }}>
                      {cat}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>

          {/* Amount slider */}
          <View style={{ gap: spacing[2] }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
                Monthly Limit
              </Text>
              <Text style={{
                color:      colors.primary,
                fontSize:   typography.size.sm,
                fontWeight: typography.weight.bold,
              }}>
                {formatKsh(limit)}
              </Text>
            </View>

            {/* Manual amount buttons since Slider needs extra package */}
            <View style={{ flexDirection: 'row', gap: spacing[2] }}>
              {[2000, 5000, 10000, 20000, 50000].map((val) => (
                <Pressable
                  key={val}
                  onPress={() => setLimit(val)}
                  style={{
                    flex:            1,
                    paddingVertical: spacing[2],
                    borderRadius:    8,
                    borderWidth:     1.5,
                    borderColor:     limit === val ? colors.primary : colors.border,
                    backgroundColor: limit === val ? colors.primary + '15' : 'transparent',
                    alignItems:      'center',
                  }}
                >
                  <Text style={{
                    color:    limit === val ? colors.primary : colors.textSecondary,
                    fontSize: typography.size.xs,
                    fontWeight: typography.weight.medium,
                  }}>
                    {val >= 1000 ? `${val/1000}K` : val}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>

          <View style={{ flexDirection: 'row', gap: spacing[3] }}>
            <Button
              label="Cancel"
              onPress={onClose}
              variant="ghost"
              fullWidth
            />
            <Button
              label="Save Budget"
              onPress={() => createBudget()}
              variant="primary"
              fullWidth
              loading={isPending}
              disabled={!selectedCategory}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function BudgetScreen() {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const [showModal, setShowModal] = useState(false);

  const { data: overview, isLoading } = useQuery({
    queryKey: ['budgetOverview'],
    queryFn:  budgetService.getBudgetOverview,
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: spacing[4],
          paddingTop:        60,
          paddingBottom:     100,
          gap:               spacing[4],
        }}
      >
        {/* Title */}
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
        }}>
          Budgets
        </Text>

        {isLoading ? (
          <>
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </>
        ) : (
          <>
            {/* Donut chart */}
            {overview?.budgets && overview.budgets.length > 0 && (
              <Card elevation="raised">
                <DonutChart
                  budgets={overview.budgets}
                  totalSpent={overview.totalSpent}
                />
              </Card>
            )}

            {/* Summary row */}
            {overview && (
              <View style={{ flexDirection: 'row', gap: spacing[3] }}>
                {[
                  { label: 'Total Budget', value: overview.totalBudget,  color: colors.primary },
                  { label: 'Total Spent',  value: overview.totalSpent,   color: colors.danger  },
                  { label: 'Remaining',    value: Math.max(overview.totalBudget - overview.totalSpent, 0),
                    color: colors.accentGreen },
                ].map((stat) => (
                  <Card key={stat.label} elevation="flat" style={{ flex: 1, alignItems: 'center' }}>
                    <Text style={{
                      color:      stat.color,
                      fontSize:   typography.size.sm,
                      fontWeight: typography.weight.bold,
                    }}>
                      {formatKsh(stat.value)}
                    </Text>
                    <Text style={{
                      color:    colors.textMuted,
                      fontSize: typography.size.xs,
                      marginTop: 2,
                    }}>
                      {stat.label}
                    </Text>
                  </Card>
                ))}
              </View>
            )}

            {/* Budget cards */}
            {overview?.budgets?.length === 0 ? (
              <View style={{ alignItems: 'center', paddingVertical: spacing[10], gap: spacing[3] }}>
                <Text style={{ fontSize: 48 }}>💰</Text>
                <Text style={{
                  color:     colors.textSecondary,
                  fontSize:  typography.size.base,
                  textAlign: 'center',
                }}>
                  No budgets yet. Tap + to add one.
                </Text>
              </View>
            ) : (
              overview?.budgets?.map((budget) => (
                <BudgetCard key={budget.id} budget={budget} />
              ))
            )}
          </>
        )}
      </ScrollView>

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
          backgroundColor: colors.primary,
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

      <AddBudgetModal
        visible={showModal}
        onClose={() => setShowModal(false)}
      />
    </View>
  );
}
