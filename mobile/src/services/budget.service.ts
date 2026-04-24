import { apiGet, apiPost } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type Budget = {
  id:           string;
  category:     string;
  monthlyLimit: number;
  spent:        number;
  remaining:    number;
  percentage:   number;
};

export type BudgetOverview = {
  totalBudget:  number;
  totalSpent:   number;
  totalSaved:   number;
  budgets:      Budget[];
};

export type BudgetCheck = {
  category:    string;
  amount:      number;
  canAfford:   boolean;
  currentSpent: number;
  limit:        number;
  percentAfter: number;
};

// ─── Service ──────────────────────────────────────────────────────────────────

export const budgetService = {

  getBudgets: () =>
    apiGet<Budget[]>('/budgets'),

  createBudget: (data: { category: string; monthlyLimit: number }) =>
    apiPost<Budget>('/budgets', data),

  getBudgetOverview: () =>
    apiGet<BudgetOverview>('/budgets/overview'),

  checkBudget: (category: string, amount: number) =>
    apiGet<BudgetCheck>(`/budgets/${category}/check`, { params: { amount } }),
};
