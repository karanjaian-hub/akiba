import React, { useState } from 'react';
import {
  Alert,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColors, useSpacing, useTypography } from '../../theme';
import Button    from '../../components/common/Button';
import Card      from '../../components/common/Card';
import AkibaLogo from '../../components/common/AkibaLogo';
import { useAuthStore } from '../../store/auth.store';
import { authService }  from '../../services/auth.service';

// ─── Menu Item ────────────────────────────────────────────────────────────────

function MenuItem({
  icon,
  label,
  value,
  onPress,
  danger,
}: {
  icon:    string;
  label:   string;
  value?:  string;
  onPress: () => void;
  danger?: boolean;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  return (
    <Pressable
      onPress={onPress}
      style={{
        flexDirection:  'row',
        alignItems:     'center',
        gap:            spacing[3],
        paddingVertical: spacing[3],
        borderBottomWidth: 1,
        borderBottomColor: colors.border,
      }}
    >
      <Text style={{ fontSize: 20, width: 28, textAlign: 'center' }}>{icon}</Text>
      <Text style={{
        flex:       1,
        color:      danger ? colors.danger : colors.textPrimary,
        fontSize:   typography.size.base,
        fontWeight: typography.weight.medium,
      }}>
        {label}
      </Text>
      {value && (
        <Text style={{ color: colors.textMuted, fontSize: typography.size.sm }}>
          {value}
        </Text>
      )}
      <Text style={{ color: colors.textMuted, fontSize: typography.size.lg }}>›</Text>
    </Pressable>
  );
}

// ─── Logout Confirmation Modal ────────────────────────────────────────────────

function LogoutModal({
  visible,
  onConfirm,
  onCancel,
  loading,
}: {
  visible:   boolean;
  onConfirm: () => void;
  onCancel:  () => void;
  loading:   boolean;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

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
          gap:                  spacing[4],
        }}>
          <Text style={{
            color:      colors.textPrimary,
            fontSize:   typography.size.lg,
            fontWeight: typography.weight.bold,
            textAlign:  'center',
          }}>
            Sign out of Akiba?
          </Text>
          <Text style={{
            color:     colors.textSecondary,
            fontSize:  typography.size.base,
            textAlign: 'center',
          }}>
            You will need to sign in again.
          </Text>
          <View style={{ gap: spacing[3] }}>
            <Button
              label="Sign Out"
              onPress={onConfirm}
              variant="danger"
              fullWidth
              loading={loading}
            />
            <Button
              label="Cancel"
              onPress={onCancel}
              variant="ghost"
              fullWidth
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function ProfileScreen({ navigation }: { navigation: any }) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();

  const user    = useAuthStore((state) => state.user);
  const logout  = useAuthStore((state) => state.logout);

  const [showLogout,   setShowLogout]   = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);

  const firstName = user?.fullName?.split(' ')[0] ?? '';
  const initials  = user?.fullName
    ?.split(' ')
    .map((n) => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase() ?? '?';

  const memberSince = user?.createdAt
    ? new Date(user.createdAt).toLocaleDateString('en-KE', {
        month: 'long', year: 'numeric',
      })
    : '';

  const handleLogout = async () => {
    setLogoutLoading(true);
    try {
      const refreshToken = await AsyncStorage.getItem('@akiba_refresh_token');
      if (refreshToken) await authService.logout(refreshToken);
    } catch {
      // Logout API failure is non-fatal — clear local state regardless
    } finally {
      logout();
      setLogoutLoading(false);
      setShowLogout(false);
    }
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{ paddingBottom: spacing[10] }}
    >
      <StatusBar style="light" />

      {/* Purple header section */}
      <View style={{
        backgroundColor:   colors.primary,
        paddingTop:        60,
        paddingBottom:     spacing[8],
        alignItems:        'center',
        gap:               spacing[3],
      }}>
        {/* Avatar */}
        <View style={{
          width:           80,
          height:          80,
          borderRadius:    40,
          backgroundColor: colors.gold,
          alignItems:      'center',
          justifyContent:  'center',
          borderWidth:     3,
          borderColor:     'rgba(255,255,255,0.3)',
        }}>
          <Text style={{
            color:      '#FFFFFF',
            fontSize:   typography.size.xl,
            fontWeight: typography.weight.bold,
          }}>
            {initials}
          </Text>
        </View>

        {/* User info */}
        <Text style={{
          color:      '#FFFFFF',
          fontSize:   typography.size.xl,
          fontWeight: typography.weight.bold,
        }}>
          {user?.fullName ?? 'User'}
        </Text>
        <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.sm }}>
          {user?.email}
        </Text>
        <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: typography.size.xs }}>
          Member since {memberSince}
        </Text>

        {/* Logo watermark */}
        <View style={{ marginTop: spacing[2] }}>
          <AkibaLogo variant="icon" size="sm" color="rgba(255,255,255,0.2)" />
        </View>
      </View>

      {/* Menu sections */}
      <View style={{ paddingHorizontal: spacing[4], gap: spacing[4], marginTop: spacing[4] }}>

        {/* Account */}
        <Card elevation="raised" style={{ gap: 0, padding: 0, paddingHorizontal: spacing[4] }}>
          <Text style={{
            color:         colors.textMuted,
            fontSize:      typography.size.xs,
            fontWeight:    typography.weight.bold,
            letterSpacing: 1,
            paddingTop:    spacing[3],
            paddingBottom: spacing[2],
          }}>
            ACCOUNT
          </Text>
          <MenuItem
            icon="✏️"
            label="Edit Profile"
            onPress={() => navigation.navigate('EditProfile')}
          />
          <MenuItem
            icon="🔒"
            label="Change Password"
            onPress={() => navigation.navigate('ForgotPassword')}
          />
          <MenuItem
            icon="🔔"
            label="Notifications"
            onPress={() => navigation.navigate('Notifications')}
          />
        </Card>

        {/* Preferences */}
        <Card elevation="raised" style={{ gap: 0, padding: 0, paddingHorizontal: spacing[4] }}>
          <Text style={{
            color:         colors.textMuted,
            fontSize:      typography.size.xs,
            fontWeight:    typography.weight.bold,
            letterSpacing: 1,
            paddingTop:    spacing[3],
            paddingBottom: spacing[2],
          }}>
            PREFERENCES
          </Text>
          <MenuItem
            icon="🎨"
            label="Appearance"
            onPress={() => navigation.navigate('AppearanceSettings')}
          />
          <MenuItem
            icon="🌍"
            label="Language"
            value="English (Kenya)"
            onPress={() => {}}
          />
        </Card>

        {/* About */}
        <Card elevation="raised" style={{ gap: 0, padding: 0, paddingHorizontal: spacing[4] }}>
          <Text style={{
            color:         colors.textMuted,
            fontSize:      typography.size.xs,
            fontWeight:    typography.weight.bold,
            letterSpacing: 1,
            paddingTop:    spacing[3],
            paddingBottom: spacing[2],
          }}>
            ABOUT
          </Text>
          <MenuItem
            icon="🛡️"
            label="Privacy Policy"
            onPress={() => {}}
          />
          <MenuItem
            icon="📋"
            label="Terms of Service"
            onPress={() => {}}
          />
          <MenuItem
            icon="ℹ️"
            label="App Version"
            value="1.0.0 (Build 1)"
            onPress={() => {}}
          />
        </Card>

        {/* Logout */}
        <Button
          label="Sign Out"
          onPress={() => setShowLogout(true)}
          variant="danger"
          fullWidth
        />
      </View>

      <LogoutModal
        visible={showLogout}
        onConfirm={handleLogout}
        onCancel={() => setShowLogout(false)}
        loading={logoutLoading}
      />
    </ScrollView>
  );
}
