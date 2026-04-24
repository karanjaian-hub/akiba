import React, { useRef } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColors, useSpacing, useTypography } from '../../theme';
import Card    from '../../components/common/Card';
import Badge   from '../../components/common/Badge';
import { SkeletonCard, SkeletonText } from '../../components/skeleton/SkeletonLoader';
import { transactionService } from '../../services/transaction.service';
import { budgetService }      from '../../services/budget.service';
import { savingsService }     from '../../services/savings.service';
import { useAuthStore }       from '../../store/auth.store';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE', { minimumFractionDigits: 0 })}`;
}

// ─── Section Header ───────────────────────────────────────────────────────────

function SectionHeader({
  title,
  onSeeAll,
}: {
  title:    string;
  onSeeAll?: () => void;
}) {
  const colors     = useColors();
  const typography = useTypography();

  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
      <Text style={{
        color:      colors.textPrimary,
        fontSize:   typography.size.md,
        fontWeight: typography.weight.bold,
      }}>
        {title}
      </Text>
      {onSeeAll && (
        <Pressable onPress={onSeeAll}>
          <Text style={{
            color:    colors.primary,
            fontSize: typography.size.sm,
            fontWeight: typography.weight.medium,
          }}>
            See all
          </Text>
        </Pressable>
      )}
    </View>
  );
}

// ─── Balance Card ─────────────────────────────────────────────────────────────

function BalanceCard({
  income,
  expenses,
  balance,
  hidden,
  onToggleHidden,
}: {
  income:         number;
  expenses:       number;
  balance:        number;
  hidden:         boolean;
  onToggleHidden: () => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <View style={{
      backgroundColor: colors.primary,
      borderRadius:    20,
      padding:         spacing[6],
      gap:             spacing[4],
    }}>
      {/* Top row */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.sm }}>
          Total Balance
        </Text>
        <Pressable onPress={onToggleHidden}>
          <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.sm }}>
            {hidden ? '👁' : '🙈'}
          </Text>
        </Pressable>
      </View>

      {/* Balance amount */}
      <Text style={{
        color:      '#FFFFFF',
        fontSize:   typography.size['3xl'],
        fontWeight: typography.weight.bold,
      }}>
        {hidden ? 'Ksh ••••••' : formatKsh(balance)}
      </Text>

      {/* Mini stat row */}
      <View style={{ flexDirection: 'row', gap: spacing[3] }}>
        {[
          { label: 'Income',   amount: income,   color: '#10B981' },
          { label: 'Expenses', amount: expenses,  color: '#F87171' },
          { label: 'Saved',    amount: Math.max(income - expenses, 0), color: colors.gold },
        ].map((stat) => (
          <View
            key={stat.label}
            style={{
              flex:            1,
              backgroundColor: 'rgba(255,255,255,0.1)',
              borderRadius:    12,
              padding:         spacing[3],
              gap:             spacing[1],
            }}
          >
            <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: typography.size.xs }}>
              {stat.label}
            </Text>
            <Text style={{
              color:      stat.color,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.bold,
            }}>
              {hidden ? '••••' : formatKsh(stat.amount)}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

// ─── Quick Actions ────────────────────────────────────────────────────────────

function QuickActions({ onNavigate }: { onNavigate: (screen: string) => void }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const actions = [
    { label: 'Send',     icon: '↑', color: colors.primary,     screen: 'PaymentTab'  },
    { label: 'Receive',  icon: '↓', color: colors.secondary,   screen: 'ProfileTab'  },
    { label: 'Pay Bill', icon: '⚡', color: colors.accentGreen, screen: 'PaymentTab'  },
    { label: 'Goals',    icon: '🎯', color: colors.gold,        screen: 'GoalsTab'    },
  ];

  return (
    <View style={{ flexDirection: 'row', gap: spacing[3] }}>
      {actions.map((action) => (
        <Pressable
          key={action.label}
          onPress={() => onNavigate(action.screen)}
          style={{ flex: 1, alignItems: 'center', gap: spacing[2] }}
        >
          <View style={{
            width:           52,
            height:          52,
            borderRadius:    16,
            backgroundColor: action.color + '20',
            alignItems:      'center',
            justifyContent:  'center',
          }}>
            <Text style={{ fontSize: 22 }}>{action.icon}</Text>
          </View>
          <Text style={{
            color:    colors.textSecondary,
            fontSize: typography.size.xs,
            fontWeight: typography.weight.medium,
          }}>
            {action.label}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

// ─── Budget Row ───────────────────────────────────────────────────────────────

function BudgetRow({
  category,
  spent,
  limit,
  percentage,
}: {
  category:   string;
  spent:      number;
  limit:      number;
  percentage: number;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const isOver     = percentage >= 90;

  return (
    <View style={{ gap: spacing[2] }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Text style={{ color: colors.textPrimary, fontSize: typography.size.sm, fontWeight: typography.weight.medium }}>
          {category}
        </Text>
        <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
          {formatKsh(spent)} / {formatKsh(limit)}
        </Text>
      </View>
      <View style={{ height: 6, backgroundColor: colors.border, borderRadius: 3, overflow: 'hidden' }}>
        <View style={{
          height:          6,
          width:           `${Math.min(percentage, 100)}%`,
          backgroundColor: isOver ? colors.danger : colors.primary,
          borderRadius:    3,
        }} />
      </View>
    </View>
  );
}

// ─── Goal Row ─────────────────────────────────────────────────────────────────

function GoalRow({
  name,
  emoji,
  currentAmount,
  targetAmount,
  percentComplete,
  daysRemaining,
}: {
  name:            string;
  emoji:           string;
  currentAmount:   number;
  targetAmount:    number;
  percentComplete: number;
  daysRemaining:   number;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <View style={{ gap: spacing[2] }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ color: colors.textPrimary, fontSize: typography.size.sm, fontWeight: typography.weight.medium }}>
          {emoji} {name}
        </Text>
        <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
          {daysRemaining}d left
        </Text>
      </View>
      <View style={{ height: 6, backgroundColor: colors.border, borderRadius: 3, overflow: 'hidden' }}>
        <View style={{
          height:          6,
          width:           `${Math.min(percentComplete, 100)}%`,
          backgroundColor: colors.gold,
          borderRadius:    3,
        }} />
      </View>
      <Text style={{ color: colors.textSecondary, fontSize: typography.size.xs }}>
        {formatKsh(currentAmount)} / {formatKsh(targetAmount)} ({Math.round(percentComplete)}%)
      </Text>
    </View>
  );
}

// ─── Transaction Row ──────────────────────────────────────────────────────────

function TransactionRow({
  merchant,
  category,
  date,
  amount,
  type,
}: {
  merchant: string;
  category: string;
  date:     string;
  amount:   number;
  type:     'CREDIT' | 'DEBIT';
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const isCredit   = type === 'CREDIT';

  return (
    <View style={{
      flexDirection:  'row',
      alignItems:     'center',
      gap:            spacing[3],
      paddingVertical: spacing[2],
    }}>
      {/* Category icon circle */}
      <View style={{
        width:           40,
        height:          40,
        borderRadius:    20,
        backgroundColor: colors.primary + '20',
        alignItems:      'center',
        justifyContent:  'center',
      }}>
        <Text style={{ fontSize: 16 }}>
          {category === 'Food'        ? '🍽' :
           category === 'Transport'   ? '🚗' :
           category === 'Bills'       ? '⚡' :
           category === 'Entertainment' ? '🎬' :
           category === 'Health'      ? '💊' : '💰'}
        </Text>
      </View>

      {/* Details */}
      <View style={{ flex: 1 }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.sm,
          fontWeight: typography.weight.medium,
        }}>
          {merchant}
        </Text>
        <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
          {new Date(date).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' })}
        </Text>
      </View>

      {/* Amount */}
      <Text style={{
        color:      isCredit ? colors.accentGreen : colors.danger,
        fontSize:   typography.size.sm,
        fontWeight: typography.weight.semibold,
      }}>
        {isCredit ? '+' : '-'}{formatKsh(amount)}
      </Text>
    </View>
  );
}

// ─── Empty State ──────────────────────────────────────────────────────────────

function EmptyState({ onImport }: { onImport: () => void }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <View style={{ alignItems: 'center', gap: spacing[4], paddingVertical: spacing[10] }}>
      <Text style={{ fontSize: 64 }}>📊</Text>
      <Text style={{
        color:      colors.textPrimary,
        fontSize:   typography.size.lg,
        fontWeight: typography.weight.bold,
        textAlign:  'center',
      }}>
        Every shilling has a story.
      </Text>
      <Text style={{
        color:     colors.textSecondary,
        fontSize:  typography.size.sm,
        textAlign: 'center',
      }}>
        Import your M-Pesa or bank statement to start telling yours.
      </Text>
      <Pressable
        onPress={onImport}
        style={{
          backgroundColor: colors.primary,
          borderRadius:    12,
          paddingHorizontal: spacing[6],
          paddingVertical:   spacing[3],
        }}
      >
        <Text style={{ color: '#FFFFFF', fontWeight: typography.weight.semibold }}>
          Import Statement
        </Text>
      </Pressable>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function DashboardScreen({ navigation }: { navigation: any }) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();
  const user        = useAuthStore((state) => state.user);

  const [balanceHidden, setBalanceHidden] = React.useState(false);

  // Restore hide preference on mount
  React.useEffect(() => {
    AsyncStorage.getItem('@akiba_balance_hidden').then((val) => {
      if (val === 'true') setBalanceHidden(true);
    });
  }, []);

  const toggleBalanceHidden = () => {
    const next = !balanceHidden;
    setBalanceHidden(next);
    AsyncStorage.setItem('@akiba_balance_hidden', String(next));
  };

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['summary'],
    queryFn:  transactionService.getSummary,
  });

  const { data: budgetOverview, isLoading: budgetLoading } = useQuery({
    queryKey: ['budgetOverview'],
    queryFn:  budgetService.getBudgetOverview,
  });

  const { data: goals, isLoading: goalsLoading } = useQuery({
    queryKey: ['goals'],
    queryFn:  savingsService.getGoals,
  });

  const { data: transactions, isLoading: txLoading } = useQuery({
    queryKey: ['transactions'],
    queryFn:  () => transactionService.getTransactions({ page: 0, size: 5 }),
  });

  const isRefreshing = summaryLoading || budgetLoading || goalsLoading || txLoading;

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['summary'] });
    queryClient.invalidateQueries({ queryKey: ['budgetOverview'] });
    queryClient.invalidateQueries({ queryKey: ['goals'] });
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
  };

  const firstName = user?.fullName?.split(' ')[0] ?? 'there';
  const hasTransactions = (transactions?.content?.length ?? 0) > 0;

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        paddingBottom:     spacing[10],
        gap:               spacing[5],
      }}
      showsVerticalScrollIndicator={false}
      refreshControl={
        <RefreshControl
          refreshing={isRefreshing}
          onRefresh={handleRefresh}
          colors={[colors.primary]}
          tintColor={colors.primary}
        />
      }
    >
      <StatusBar style="auto" />

      {/* ── Section 1: Header ── */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.sm }}>
            {getGreeting()},
          </Text>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.lg,
            fontWeight: typography.weight.bold,
          }}>
            {firstName} 👋
          </Text>
        </View>
        <Pressable onPress={() => navigation.navigate('ProfileTab')}>
          <View style={{
            width:           44,
            height:          44,
            borderRadius:    22,
            backgroundColor: colors.primary,
            alignItems:      'center',
            justifyContent:  'center',
          }}>
            <Text style={{ color: '#FFFFFF', fontWeight: typography.weight.bold }}>
              {firstName[0]?.toUpperCase()}
            </Text>
          </View>
        </Pressable>
      </View>

      {/* ── Section 2: Balance Card ── */}
      {summaryLoading ? (
        <SkeletonCard />
      ) : (
        <BalanceCard
          income={summary?.income   ?? 0}
          expenses={summary?.expenses ?? 0}
          balance={summary?.balance  ?? 0}
          hidden={balanceHidden}
          onToggleHidden={toggleBalanceHidden}
        />
      )}

      {/* ── Section 3: Quick Actions ── */}
      <Card elevation="flat">
        <QuickActions onNavigate={(screen) => navigation.navigate(screen)} />
      </Card>

      {/* ── Section 4: AI Insight ── */}
      <Card elevation="raised" style={{ backgroundColor: colors.secondary }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[3] }}>
          <View style={{
            width:           40,
            height:          40,
            borderRadius:    20,
            backgroundColor: colors.gold,
            alignItems:      'center',
            justifyContent:  'center',
          }}>
            <Text style={{ color: '#FFFFFF', fontWeight: typography.weight.bold, fontSize: 12 }}>
              AI
            </Text>
          </View>
          <Text style={{
            flex:      1,
            color:     '#FFFFFF',
            fontSize:  typography.size.sm,
            fontStyle: 'italic',
          }}>
            Your top spend this month is Food. Consider setting a budget limit to save more.
          </Text>
        </View>
        <Pressable
          onPress={() => navigation.navigate('AiChat')}
          style={{ marginTop: spacing[3] }}
        >
          <Text style={{
            color:      colors.gold,
            fontSize:   typography.size.sm,
            fontWeight: typography.weight.semibold,
          }}>
            Ask Akiba →
          </Text>
        </Pressable>
      </Card>

      {/* ── Section 5: Budget Overview ── */}
      <View style={{ gap: spacing[3] }}>
        <SectionHeader
          title="Budget Overview"
          onSeeAll={() => navigation.navigate('GoalsTab')}
        />
        {budgetLoading ? (
          <SkeletonCard />
        ) : budgetOverview?.budgets?.length ? (
          <Card elevation="raised">
            <View style={{ gap: spacing[4] }}>
              {budgetOverview.budgets.slice(0, 3).map((b) => (
                <BudgetRow
                  key={b.id}
                  category={b.category}
                  spent={b.spent}
                  limit={b.monthlyLimit}
                  percentage={b.percentage}
                />
              ))}
            </View>
          </Card>
        ) : (
          <Text style={{ color: colors.textMuted, fontSize: typography.size.sm }}>
            No budgets set up yet.
          </Text>
        )}
      </View>

      {/* ── Section 6: Savings Goals ── */}
      <View style={{ gap: spacing[3] }}>
        <SectionHeader
          title="Savings Goals"
          onSeeAll={() => navigation.navigate('GoalsTab')}
        />
        {goalsLoading ? (
          <SkeletonCard />
        ) : goals?.length ? (
          <Card elevation="raised">
            <View style={{ gap: spacing[4] }}>
              {goals.slice(0, 2).map((goal) => (
                <GoalRow
                  key={goal.id}
                  name={goal.name}
                  emoji={goal.emoji}
                  currentAmount={goal.currentAmount}
                  targetAmount={goal.targetAmount}
                  percentComplete={goal.percentComplete}
                  daysRemaining={goal.daysRemaining}
                />
              ))}
            </View>
          </Card>
        ) : (
          <Text style={{ color: colors.textMuted, fontSize: typography.size.sm }}>
            No savings goals yet.
          </Text>
        )}
      </View>

      {/* ── Section 7: Recent Transactions ── */}
      <View style={{ gap: spacing[3] }}>
        <SectionHeader
          title="Recent Transactions"
          onSeeAll={() => navigation.navigate('HistoryTab')}
        />
        {txLoading ? (
          <>
            <SkeletonCard />
            <SkeletonCard />
          </>
        ) : hasTransactions ? (
          <Card elevation="raised">
            <View style={{ gap: spacing[1] }}>
              {transactions!.content.map((tx) => (
                <TransactionRow
                  key={tx.id}
                  merchant={tx.merchant}
                  category={tx.category}
                  date={tx.date}
                  amount={tx.amount}
                  type={tx.type}
                />
              ))}
            </View>
          </Card>
        ) : (
          <EmptyState onImport={() => navigation.navigate('Import')} />
        )}
      </View>
    </ScrollView>
  );
}
