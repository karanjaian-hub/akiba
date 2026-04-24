import React, { useState } from 'react';
import {
  FlatList,
  Pressable,
  RefreshControl,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useColors, useSpacing, useTypography } from '../../theme';
import { apiGet, apiPut, apiDelete } from '../../services/api';

// ─── Types ───────────────────────────────────────────────────────────────────

type NotificationType =
  | 'PAYMENT_CONFIRMED'
  | 'BUDGET_EXCEEDED'
  | 'GOAL_ACHIEVED'
  | 'SAVINGS_NUDGE'
  | 'REPORT_READY'
  | 'SYSTEM_ERROR';

type AppNotification = {
  id:        string;
  type:      NotificationType;
  title:     string;
  message:   string;
  isRead:    boolean;
  createdAt: string;
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getNotificationIcon(type: NotificationType): string {
  const icons: Record<NotificationType, string> = {
    PAYMENT_CONFIRMED: '💸',
    BUDGET_EXCEEDED:   '⚠️',
    GOAL_ACHIEVED:     '🏆',
    SAVINGS_NUDGE:     '🎯',
    REPORT_READY:      '📄',
    SYSTEM_ERROR:      '🚨',
  };
  return icons[type] ?? '🔔';
}

function getNotificationColor(type: NotificationType, colors: any): string {
  const map: Record<NotificationType, string> = {
    PAYMENT_CONFIRMED: colors.secondary,
    BUDGET_EXCEEDED:   colors.gold,
    GOAL_ACHIEVED:     colors.accentGreen,
    SAVINGS_NUDGE:     colors.primary,
    REPORT_READY:      colors.secondary,
    SYSTEM_ERROR:      colors.danger,
  };
  return map[type] ?? colors.primary;
}

function timeAgo(dateStr: string): string {
  const diff    = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  const hours   = Math.floor(diff / 3600000);
  const days    = Math.floor(diff / 86400000);

  if (minutes < 1)  return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours   < 24) return `${hours}h ago`;
  return               `${days}d ago`;
}

// ─── Notification Row ─────────────────────────────────────────────────────────

function NotificationRow({
  notification,
  onMarkRead,
  onDelete,
}: {
  notification: AppNotification;
  onMarkRead:   (id: string) => void;
  onDelete:     (id: string) => void;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const iconColor  = getNotificationColor(notification.type, colors);

  return (
    <View style={{
      flexDirection:   'row',
      alignItems:      'center',
      gap:             spacing[3],
      paddingVertical: spacing[3],
      paddingHorizontal: spacing[4],
      backgroundColor: notification.isRead
        ? colors.surface
        : colors.primary + '08',
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    }}>
      {/* Icon circle */}
      <View style={{
        width:           44,
        height:          44,
        borderRadius:    22,
        backgroundColor: iconColor + '20',
        alignItems:      'center',
        justifyContent:  'center',
        flexShrink:      0,
      }}>
        <Text style={{ fontSize: 20 }}>
          {getNotificationIcon(notification.type)}
        </Text>
      </View>

      {/* Content */}
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={{
          color:      colors.textPrimary,
          fontSize:   typography.size.sm,
          fontWeight: notification.isRead
            ? typography.weight.regular
            : typography.weight.bold,
        }}
          numberOfLines={1}
        >
          {notification.title}
        </Text>
        <Text style={{
          color:    colors.textSecondary,
          fontSize: typography.size.xs,
        }}
          numberOfLines={2}
        >
          {notification.message}
        </Text>
        <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
          {timeAgo(notification.createdAt)}
        </Text>
      </View>

      {/* Right side — unread dot + delete */}
      <View style={{ alignItems: 'center', gap: spacing[2] }}>
        {!notification.isRead && (
          <Pressable onPress={() => onMarkRead(notification.id)}>
            <View style={{
              width:           10,
              height:          10,
              borderRadius:    5,
              backgroundColor: colors.secondary,
            }} />
          </Pressable>
        )}
        <Pressable onPress={() => onDelete(notification.id)}>
          <Text style={{ color: colors.textMuted, fontSize: 16 }}>×</Text>
        </Pressable>
      </View>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function NotificationsScreen() {
  const colors      = useColors();
  const spacing     = useSpacing();
  const typography  = useTypography();
  const queryClient = useQueryClient();

  const {
    data:        notifications,
    isLoading,
    isRefetching,
    refetch,
  } = useQuery({
    queryKey: ['notifications'],
    queryFn:  () => apiGet<AppNotification[]>('/notifications'),
  });

  const { mutate: markAllRead } = useMutation({
    mutationFn: () => apiPut('/notifications/read-all', {}),
    onSuccess:  () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const { mutate: markOneRead } = useMutation({
    mutationFn: (id: string) => apiPut(`/notifications/${id}/read`, {}),
    onSuccess:  () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const { mutate: deleteNotification } = useMutation({
    mutationFn: (id: string) => apiDelete(`/notifications/${id}`),
    onSuccess:  () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const unreadCount = notifications?.filter((n) => !n.isRead).length ?? 0;

  // Sort — unread first
  const sorted = [...(notifications ?? [])].sort((a, b) => {
    if (a.isRead === b.isRead) return 0;
    return a.isRead ? 1 : -1;
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar style="auto" />

      {/* Header */}
      <View style={{
        flexDirection:     'row',
        justifyContent:    'space-between',
        alignItems:        'center',
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        paddingBottom:     spacing[3],
        backgroundColor:   colors.background,
        borderBottomWidth: 1,
        borderBottomColor: colors.border,
      }}>
        <View>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.xl,
            fontWeight: typography.weight.bold,
          }}>
            Notifications
          </Text>
          {unreadCount > 0 && (
            <Text style={{ color: colors.textMuted, fontSize: typography.size.xs }}>
              {unreadCount} unread
            </Text>
          )}
        </View>
        {unreadCount > 0 && (
          <Pressable onPress={() => markAllRead()}>
            <Text style={{
              color:      colors.primary,
              fontSize:   typography.size.sm,
              fontWeight: typography.weight.medium,
            }}>
              Mark all read
            </Text>
          </Pressable>
        )}
      </View>

      {/* List */}
      {isLoading ? (
        <View style={{
          flex:           1,
          alignItems:     'center',
          justifyContent: 'center',
        }}>
          <Text style={{ color: colors.textMuted }}>Loading...</Text>
        </View>
      ) : sorted.length === 0 ? (
        <View style={{
          flex:           1,
          alignItems:     'center',
          justifyContent: 'center',
          gap:            spacing[3],
        }}>
          <Text style={{ fontSize: 48 }}>🔔</Text>
          <Text style={{
            color:    colors.textSecondary,
            fontSize: typography.size.base,
          }}>
            No notifications yet
          </Text>
        </View>
      ) : (
        <FlatList
          data={sorted}
          keyExtractor={(item) => item.id}
          refreshControl={
            <RefreshControl
              refreshing={isRefetching}
              onRefresh={refetch}
              colors={[colors.primary]}
              tintColor={colors.primary}
            />
          }
          renderItem={({ item }) => (
            <NotificationRow
              notification={item}
              onMarkRead={(id) => markOneRead(id)}
              onDelete={(id)   => deleteNotification(id)}
            />
          )}
        />
      )}
    </View>
  );
}
