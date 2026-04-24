import { apiGet, apiPost, apiPut } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type TransactionType   = 'CREDIT' | 'DEBIT';
export type TransactionSource = 'MPESA' | 'BANK' | 'MANUAL';

export type Transaction = {
  id:          string;
  merchant:    string;
  amount:      number;
  type:        TransactionType;
  category:    string;
  source:      TransactionSource;
  date:        string;
  reference?:  string;
  rawText?:    string;
  isAnomalous: boolean;
};

export type TransactionSummary = {
  income:        number;
  expenses:      number;
  balance:       number;
  topCategories: {
    category: string;
    amount:   number;
    count:    number;
  }[];
};

export type TopMerchant = {
  merchant: string;
  amount:   number;
  count:    number;
  category: string;
};

export type PaginatedResponse<T> = {
  content:       T[];
  totalElements: number;
  totalPages:    number;
  page:          number;
  size:          number;
};

export type TransactionParams = {
  page?:     number;
  size?:     number;
  category?: string;
  dateFrom?: string;
  dateTo?:   string;
  type?:     TransactionType;
  source?:   TransactionSource;
  search?:   string;
};

// ─── Service ──────────────────────────────────────────────────────────────────

export const transactionService = {

  getTransactions: (params: TransactionParams = {}) =>
    apiGet<PaginatedResponse<Transaction>>('/transactions', { params }),

  getTransaction: (id: string) =>
    apiGet<Transaction>(`/transactions/${id}`),

  getSummary: () =>
    apiGet<TransactionSummary>('/transactions/summary'),

  getTopMerchants: () =>
    apiGet<TopMerchant[]>('/transactions/top-merchants'),

  createTransaction: (data: Partial<Transaction>) =>
    apiPost<Transaction>('/transactions', data),

  updateCategory: (id: string, category: string) =>
    apiPut<Transaction>(`/transactions/${id}/category`, { category }),
};
