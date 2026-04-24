import { apiGet, apiPost, apiPut, apiDelete } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';

export type SavingsGoal = {
  id:              string;
  name:            string;
  emoji:           string;
  targetAmount:    number;
  currentAmount:   number;
  percentComplete: number;
  deadline:        string;
  daysRemaining:   number;
  weeklyNeeded:    number;
  status:          GoalStatus;
};

export type GoalContribution = {
  id:        string;
  goalId:    string;
  amount:    number;
  createdAt: string;
};

export type CreateGoalRequest = {
  name:         string;
  emoji:        string;
  targetAmount: number;
  deadline:     string;
};

// ─── Service ──────────────────────────────────────────────────────────────────

export const savingsService = {

  getGoals: () =>
    apiGet<SavingsGoal[]>('/savings/goals'),

  createGoal: (data: CreateGoalRequest) =>
    apiPost<SavingsGoal>('/savings/goals', data),

  updateGoal: (id: string, data: Partial<CreateGoalRequest>) =>
    apiPut<SavingsGoal>(`/savings/goals/${id}`, data),

  deleteGoal: (id: string) =>
    apiDelete<{ message: string }>(`/savings/goals/${id}`),

  contribute: (goalId: string, amount: number) =>
    apiPost<GoalContribution>(`/savings/goals/${goalId}/contribute`, { amount }),

  getGoalHistory: (id: string) =>
    apiGet<GoalContribution[]>(`/savings/goals/${id}/history`),
};
