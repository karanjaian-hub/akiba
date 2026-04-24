// Every screen in the app is typed here.
// This gives us autocomplete and type safety when navigating.

export type AuthStackParams = {
  Splash:           undefined;
  Onboarding:       undefined;
  Login:            undefined;
  Register:         undefined;
  ForgotPassword:   undefined;
  OtpVerification:  { email: string; type: 'verify' | 'reset' };
  ResetPassword:    { email: string; otp: string };
};

export type HomeStackParams = {
  Dashboard:    undefined;
  Import:       undefined;
  MpesaImport:  undefined;
  BankImport:   undefined;
  AiChat:       undefined;
};

export type PaymentStackParams = {
  PaymentMain:    undefined;
  PaymentConfirm: { paymentId: string; phone: string };
  PaymentSuccess: { paymentId: string };
  PaymentFailed:  { message: string };
};

export type GoalsStackParams = {
  GoalsList:      undefined;
  BudgetMain:     undefined;
  GoalDetail:     { goalId: string };
  GoalContribute: { goalId: string };
};

export type HistoryStackParams = {
  TransactionList:   undefined;
  TransactionDetail: { transactionId: string };
  EditCategory:      { transactionId: string };
};

export type ProfileStackParams = {
  ProfileMain:        undefined;
  EditProfile:        undefined;
  Settings:           undefined;
  Notifications:      undefined;
  AppearanceSettings: undefined;
};

// Bottom tab param list — each tab is a nested stack
export type AppTabParams = {
  HomeTab:    undefined;
  PaymentTab: undefined;
  GoalsTab:   undefined;
  HistoryTab: undefined;
  ProfileTab: undefined;
};
