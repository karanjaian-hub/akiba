import { apiGet, apiPost, apiPut } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type RecipientType  = 'PHONE' | 'TILL' | 'PAYBILL';
export type PaymentStatus  = 'PENDING' | 'COMPLETED' | 'FAILED';

export type PaymentRequest = {
  recipientType:  RecipientType;
  phone?:         string;
  till?:          string;
  paybill?:       string;
  accountNumber?: string;
  amount:         number;
  category:       string;
  pin:            string;
};

export type PaymentStatusResponse = {
  paymentId: string;
  status:    PaymentStatus;
  message?:  string;
};

export type Recipient = {
  id:             string;
  type:           RecipientType;
  phone?:         string;
  till?:          string;
  paybill?:       string;
  accountNumber?: string;
  nickname:       string;
  category:       string;
  useCount:       number;
  lastUsed:       string;
};

export type PaymentRecord = {
  id:        string;
  type:      RecipientType;
  amount:    number;
  recipient: string;
  category:  string;
  status:    PaymentStatus;
  createdAt: string;
};

export type InitiatePaymentResponse = {
  paymentId: string;
  message:   string;
};

// ─── Service ──────────────────────────────────────────────────────────────────

export const paymentService = {

  initiatePayment: (data: PaymentRequest) =>
    apiPost<InitiatePaymentResponse>('/payments/initiate', data),

  getPaymentStatus: (paymentId: string) =>
    apiGet<PaymentStatusResponse>(`/payments/status/${paymentId}`),

  getPaymentHistory: (params: { page: number; size: number }) =>
    apiGet<PaymentRecord[]>('/payments/history', { params }),

  getRecipients: () =>
    apiGet<Recipient[]>('/payments/recipients'),

  saveRecipient: (data: Partial<Recipient>) =>
    apiPost<Recipient>('/payments/recipients', data),

  updateRecipient: (id: string, data: Partial<Recipient>) =>
    apiPut<Recipient>(`/payments/recipients/${id}`, data),
};
