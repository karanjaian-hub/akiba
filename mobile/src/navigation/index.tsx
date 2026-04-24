import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import AsyncStorage from '@react-native-async-storage/async-storage';

import SplashScreen from '../screens/auth/SplashScreen';
import OnboardingScreen from '../screens/auth/OnboardingScreen';
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';
import OtpVerificationScreen from '../screens/auth/OtpVerificationScreen';
import ForgotPasswordScreen from '../screens/auth/ForgotPasswordScreen';
import ResetPasswordScreen  from '../screens/auth/ResetPasswordScreen';

import DashboardScreen from '../screens/dashboard/DashboardScreen';
import ImportScreen      from '../screens/dashboard/ImportScreen';
import MpesaImportScreen from '../screens/dashboard/MpesaImportScreen';
import BankImportScreen  from '../screens/dashboard/BankImportScreen';

import PaymentScreen        from '../screens/payments/PaymentScreen';
import PaymentPendingScreen from '../screens/payments/PaymentPendingScreen';
import PaymentSuccessScreen from '../screens/payments/PaymentSuccessScreen';
import PaymentFailedScreen  from '../screens/payments/PaymentFailedScreen';

import TransactionListScreen   from '../screens/history/TransactionListScreen';
import TransactionDetailScreen from '../screens/history/TransactionDetailScreen';

import BudgetScreen from '../screens/budgets/BudgetScreen';

import GoalsScreen      from '../screens/savings/GoalsScreen';
import GoalDetailScreen from '../screens/savings/GoalDetailScreen';

import AiChatScreen from '../screens/ai/AiChatScreen';

import NotificationsScreen from '../screens/profile/NotificationsScreen';

import ProfileScreen      from '../screens/profile/ProfileScreen';
import EditProfileScreen  from '../screens/profile/EditProfileScreen';
import SettingsScreen     from '../screens/profile/SettingsScreen';

import { useColors, useTypography } from '../theme';
import {
  AuthStackParams,
  AppTabParams,
  HomeStackParams,
  PaymentStackParams,
  GoalsStackParams,
  HistoryStackParams,
  ProfileStackParams,
} from './types';

// ─── Stack & Tab Creators ─────────────────────────────────────────────────────

const AuthStack    = createNativeStackNavigator<AuthStackParams>();
const AppTab       = createBottomTabNavigator<AppTabParams>();
const HomeStack    = createNativeStackNavigator<HomeStackParams>();
const PaymentStack = createNativeStackNavigator<PaymentStackParams>();
const GoalsStack   = createNativeStackNavigator<GoalsStackParams>();
const HistoryStack = createNativeStackNavigator<HistoryStackParams>();
const ProfileStack = createNativeStackNavigator<ProfileStackParams>();

// ─── Placeholder Screen ───────────────────────────────────────────────────────
// Temporary screen used for all routes until real screens are built in phases 3-7

function PlaceholderScreen({ route }: { route: any }) {
  const colors = useColors();
  return (
    <View style={{
      flex:            1,
      backgroundColor: colors.background,
      alignItems:      'center',
      justifyContent:  'center',
    }}>
      <Text style={{ color: colors.primary, fontSize: 20, fontWeight: '700' }}>
        {route.name}
      </Text>
      <Text style={{ color: colors.textSecondary, fontSize: 14, marginTop: 8 }}>
        Coming in a future phase
      </Text>
    </View>
  );
}

// ─── Auth Stack ───────────────────────────────────────────────────────────────

function AuthNavigator() {
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false }}>
        <AuthStack.Screen name="Splash" component={SplashScreen} />
        <AuthStack.Screen name="Onboarding" component={OnboardingScreen} />
        <AuthStack.Screen name="Login" component={LoginScreen} />
        <AuthStack.Screen name="Register" component={RegisterScreen} />
        <AuthStack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
        <AuthStack.Screen name="OtpVerification" component={OtpVerificationScreen} />
        <AuthStack.Screen name="ResetPassword"  component={ResetPasswordScreen}  />
    </AuthStack.Navigator>
  );
}

// ─── Nested Tab Stacks ────────────────────────────────────────────────────────

function HomeNavigator() {
  return (
    <HomeStack.Navigator screenOptions={{ headerShown: false }}>
        <HomeStack.Screen name="Dashboard" component={DashboardScreen} />
        <HomeStack.Screen name="Import"      component={ImportScreen}      />
        <HomeStack.Screen name="MpesaImport" component={MpesaImportScreen} />
        <HomeStack.Screen name="BankImport"  component={BankImportScreen}  />
        <HomeStack.Screen name="AiChat" component={AiChatScreen} />
    </HomeStack.Navigator>
  );
}

function PaymentNavigator() {
  return (
    <PaymentStack.Navigator screenOptions={{ headerShown: false }}>
        <PaymentStack.Screen name="PaymentMain"    component={PaymentScreen}        />
        <PaymentStack.Screen name="PaymentConfirm" component={PaymentPendingScreen} />
        <PaymentStack.Screen name="PaymentSuccess" component={PaymentSuccessScreen} />
        <PaymentStack.Screen name="PaymentFailed"  component={PaymentFailedScreen}  />
    </PaymentStack.Navigator>
  );
}

function GoalsNavigator() {
    return (
        <GoalsStack.Navigator screenOptions={{ headerShown: false }}>
            <GoalsStack.Screen name="GoalsList"      component={GoalsScreen}       />
            <GoalsStack.Screen name="BudgetMain"     component={BudgetScreen}      />
            <GoalsStack.Screen name="GoalDetail"     component={GoalDetailScreen}  />
            <GoalsStack.Screen name="GoalContribute" component={PlaceholderScreen} />
        </GoalsStack.Navigator>
    );
}

function HistoryNavigator() {
  return (
    <HistoryStack.Navigator screenOptions={{ headerShown: false }}>
        <HistoryStack.Screen name="TransactionList"   component={TransactionListScreen}   />
        <HistoryStack.Screen name="TransactionDetail" component={TransactionDetailScreen} />
        <HistoryStack.Screen name="EditCategory" component={PlaceholderScreen} />
    </HistoryStack.Navigator>
  );
}

function ProfileNavigator() {
  return (
    <ProfileStack.Navigator screenOptions={{ headerShown: false }}>
        <ProfileStack.Screen name="ProfileMain"        component={ProfileScreen}       />
        <ProfileStack.Screen name="EditProfile"        component={EditProfileScreen}   />
        <ProfileStack.Screen name="Settings"           component={SettingsScreen}      />
        <ProfileStack.Screen name="Notifications"      component={NotificationsScreen} />
        <ProfileStack.Screen name="AppearanceSettings" component={SettingsScreen}      />
    </ProfileStack.Navigator>
  );
}

// ─── Bottom Tab Navigator ─────────────────────────────────────────────────────

function AppNavigator() {
  const colors     = useColors();
  const typography = useTypography();

  return (
    <AppTab.Navigator
      screenOptions={{
        headerShown:     false,
        tabBarStyle: {
          backgroundColor: colors.tabBar,
          borderTopColor:  colors.borderLight,
          borderTopWidth:  1,
          height:          64,
        },
        tabBarActiveTintColor:   colors.tabBarActive,
        tabBarInactiveTintColor: colors.tabBarInactive,
        tabBarLabelStyle: {
          fontSize:   11,
          fontWeight: typography.weight.medium,
          marginBottom: 6,
        },
      }}
    >
      <AppTab.Screen
        name="HomeTab"
        component={HomeNavigator}
        options={{ tabBarLabel: 'Home' }}
      />
      <AppTab.Screen
        name="PaymentTab"
        component={PaymentNavigator}
        options={{ tabBarLabel: 'Payments' }}
      />
      <AppTab.Screen
        name="GoalsTab"
        component={GoalsNavigator}
        options={{ tabBarLabel: 'Goals' }}
      />
      <AppTab.Screen
        name="HistoryTab"
        component={HistoryNavigator}
        options={{ tabBarLabel: 'History' }}
      />
      <AppTab.Screen
        name="ProfileTab"
        component={ProfileNavigator}
        options={{ tabBarLabel: 'Profile' }}
      />
    </AppTab.Navigator>
  );
}

// ─── Root Navigator ───────────────────────────────────────────────────────────

function RootNavigator() {
  const colors = useColors();

  // null = still checking, true = has token, false = no token
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);

  useEffect(() => {
    AsyncStorage.getItem('@akiba_access_token').then((token) => {
      setIsAuthenticated(!!token);
    });
  }, []);

  // Show a simple centered loader while checking AsyncStorage
  if (isAuthenticated === null) {
    return (
      <View style={{
        flex:            1,
        backgroundColor: colors.primary,
        alignItems:      'center',
        justifyContent:  'center',
      }}>
        <ActivityIndicator size="large" color="#FFFFFF" />
      </View>
    );
  }

  return isAuthenticated ? <AppNavigator /> : <AuthNavigator />;
}

// ─── Main Export ──────────────────────────────────────────────────────────────

export default function Navigation() {
  return (
    <NavigationContainer>
      <RootNavigator />
    </NavigationContainer>
  );
}
