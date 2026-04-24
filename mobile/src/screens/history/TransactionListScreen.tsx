import React, { useState, useCallback } from 'react';
import {
  FlatList,
  Pressable,
  RefreshControl,
  SectionList,
  Text,
  TextInput,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import { SkeletonCard } from '../../components/skeleton/SkeletonLoader';
import {
  transactionService,
  Transaction,
  TransactionType,
} from '../../services/transaction.service';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatKsh(amount: number): string {
  return `Ksh ${amount.toLocaleString('en-KE')}`;
}

function groupByDate(transactions: Transaction[]): { title: string; data: Transaction[] }[] {
  const groups: Record<string, Transaction[]> = {};
  const today     = new Date().toDateString();
  const yesterday = new Date(Date.now() - 86400000).toDateString();

  transactions.forEach((tx) => {
    const date    = new Date(tx.date);
    const dateStr = date.toDateString();
    let label: string;

    if (dateStr === today)     label = 'Today';
    else if (dateStr === yesterday) label = 'Yesterday';
    else label = date.toLocaleDateString('en-KE', {
      weekday: 'short', day: 'numeric', month: 'short', year: 'numeric',
    });

    if (!groups[label]) groups[label] = [];
    groups[label].push(tx);
  });

  return Object.entries(groups).map(([title, data]) => ({ title, data }));
}

const CATEGORY_EMOJI: Record<string, string> = {
  Food:          '🍽',
  Transport:     '🚗',
  Bills:         '⚡',
  Entertainment: '🎬',
  Health:        '💊',
  Other:         '💰',
};

const FILTER_CHIPS = ['All', 'Income', 'Expenses', ...Object.keys(CATEGORY_EMOJI)];

// ─── Transaction Row ──────────────────────────────────────────────────────────

function TransactionRow({
  transaction,
  onPress,
}: {
  transaction: Transaction;
  onPress:     () => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const isCredit   = transaction.type === 'CREDIT';

  return (
    <Pressable
      onPress={onPress}
      style={{
        flexDirection:  'row',
        alignItems:     'center',
        gap:            spacing[3],
        paddingVertical: spacing[3],
        paddingHorizontal: spacing[4],
        backgroundColor: colors.surface,
      }}
    >
      {/* Category icon */}
      <View style={{
        width:           44,
        height:          44,
        borderRadius:    22,
        backgroundColor: colors.primary + '15',
        alignItems:      'center',
        justifyContent:  'center',
      }}>
        <Text style={{ fontSize: 20 }}>
          {CATEGORY_EMOJI[transaction.category] ?? '💰'}
        </Text>
      </View>

      {/* Details */}
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.sm,
          fontWeight: typography.weight.semibold,
        }}>
          {transaction.merchant}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing[2] }}>
          <Text style={{
            color:    colors.textMuted,
            fontSize: typography.size.xs,
          }}>
            {transaction.category}
          </Text>
          <Text style={{ color: colors.border }}>•</Text>
          <Text style={{
            color:    colors.textMuted,
            fontSize: typography.size.xs,
          }}>
            {new Date(transaction.date).toLocaleTimeString('en-KE', {
              hour: '2-digit', minute: '2-digit',
            })}
          </Text>
        </View>
      </View>

      {/* Amount + anomaly flag */}
      <View style={{ alignItems: 'flex-end', gap: 2 }}>
        <Text style={{
          color:      isCredit ? colors.accentGreen : colors.danger,
          fontSize:   typography.size.sm,
          fontWeight: typography.weight.semibold,
        }}>
          {isCredit ? '+' : '-'}{formatKsh(transaction.amount)}
        </Text>
        {transaction.isAnomalous && (
          <Text style={{ fontSize: 12 }}>⚠️</Text>
        )}
      </View>
    </Pressable>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function TransactionListScreen({ navigation }: { navigation: any }) {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();

  const [search,      setSearch]      = useState('');
  const [activeFilter, setActiveFilter] = useState('All');
  const [searchDebounced, setSearchDebounced] = useState('');

  // Debounce search input
  const handleSearch = useCallback((text: string) => {
    setSearch(text);
    const timer = setTimeout(() => setSearchDebounced(text), 400);
    return () => clearTimeout(timer);
  }, []);

  // Build query params from active filter
  const getParams = () => {
    const params: any = { page: 0, size: 20 };
    if (searchDebounced)            params.search   = searchDebounced;
    if (activeFilter === 'Income')  params.type     = 'CREDIT' as TransactionType;
    if (activeFilter === 'Expenses') params.type    = 'DEBIT'  as TransactionType;
    if (Object.keys(CATEGORY_EMOJI).includes(activeFilter))
                                    params.category = activeFilter;
    return params;
  };

  const {
    data,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
    isRefetching,
  } = useInfiniteQuery({
    queryKey: ['transactions', activeFilter, searchDebounced],
    queryFn:  ({ pageParam = 0 }) =>
      transactionService.getTransactions({ ...getParams(), page: pageParam }),
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    initialPageParam: 0,
  });

  const allTransactions = data?.pages.flatMap((p) => p.content) ?? [];
  const sections        = groupByDate(allTransactions);

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      {/* Header */}
      <View style={{
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        paddingBottom:     spacing[3],
        gap:               spacing[3],
        backgroundColor:   colors.background,
      }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
        }}>
          History
        </Text>

        {/* Search bar */}
        <View style={{
          flexDirection:   'row',
          alignItems:      'center',
          backgroundColor: colors.inputBackground,
          borderWidth:     1.5,
          borderColor:     colors.inputBorder,
          borderRadius:    12,
          paddingHorizontal: spacing[3],
          gap:             spacing[2],
        }}>
          <Text style={{ fontSize: 16 }}>🔍</Text>
          <TextInput
            value={search}
            onChangeText={handleSearch}
            placeholder="Search transactions..."
            placeholderTextColor={colors.textMuted}
            style={{
              flex:    1,
              color:   colors.textPrimary,
              fontSize: typography.size.sm,
              paddingVertical: spacing[3],
            }}
          />
        </View>

        {/* Filter chips */}
        <FlatList
          data={FILTER_CHIPS}
          horizontal
          showsHorizontalScrollIndicator={false}
          keyExtractor={(item) => item}
          contentContainerStyle={{ gap: spacing[2] }}
          renderItem={({ item }) => {
            const isActive = activeFilter === item;
            return (
              <Pressable
                onPress={() => setActiveFilter(item)}
                style={{
                  paddingHorizontal: 14,
                  paddingVertical:   6,
                  borderRadius:      20,
                  borderWidth:       1.5,
                  borderColor:       isActive ? colors.primary : colors.border,
                  backgroundColor:   isActive ? colors.primary : 'transparent',
                }}
              >
                <Text style={{
                  color:      isActive ? '#FFFFFF' : colors.textSecondary,
                  fontSize:   typography.size.sm,
                  fontWeight: isActive
                    ? typography.weight.semibold
                    : typography.weight.regular,
                }}>
                  {item}
                </Text>
              </Pressable>
            );
          }}
        />
      </View>

      {/* Transaction list */}
      {isLoading ? (
        <View style={{ padding: spacing[4], gap: spacing[3] }}>
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </View>
      ) : sections.length === 0 ? (
        <View style={{
          flex:           1,
          alignItems:     'center',
          justifyContent: 'center',
          gap:            spacing[3],
        }}>
          <Text style={{ fontSize: 48 }}>📭</Text>
          <Text style={{ color: colors.textSecondary, fontSize: typography.size.base }}>
            No transactions found
          </Text>
        </View>
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={(item) => item.id}
          stickySectionHeadersEnabled
          refreshControl={
            <RefreshControl
              refreshing={isRefetching}
              onRefresh={refetch}
              colors={[colors.primary]}
              tintColor={colors.primary}
            />
          }
          onEndReached={() => {
            if (hasNextPage && !isFetchingNextPage) fetchNextPage();
          }}
          onEndReachedThreshold={0.3}
          renderSectionHeader={({ section: { title } }) => (
            <View style={{
              backgroundColor:   colors.background,
              paddingHorizontal: spacing[4],
              paddingVertical:   spacing[2],
            }}>
              <Text style={{
                color:         colors.textMuted,
                fontSize:      typography.size.xs,
                fontWeight:    typography.weight.bold,
                letterSpacing: 1,
                textTransform: 'uppercase',
              }}>
                {title}
              </Text>
            </View>
          )}
          renderItem={({ item }) => (
            <TransactionRow
              transaction={item}
              onPress={() =>
                navigation.navigate('TransactionDetail', {
                  transactionId: item.id,
                })
              }
            />
          )}
          ListFooterComponent={
            isFetchingNextPage ? (
              <View style={{ padding: spacing[4] }}>
                <SkeletonCard />
              </View>
            ) : null
          }
        />
      )}
    </View>
  );
}
