import { apiGet, apiPost } from './api';

// ─── Types ───────────────────────────────────────────────────────────────────

export type MessageRole = 'user' | 'assistant';

export type ChatMessage = {
  role:    MessageRole;
  content: string;
};

export type Conversation = {
  id:        string;
  messages:  ChatMessage[];
  createdAt: string;
};

export type AiInsight = {
  insight:   string;
  category?: string;
};

export type AiReport = {
  month:     number;
  year:      number;
  summary:   string;
  insights:  string[];
};

// ─── Service ──────────────────────────────────────────────────────────────────

export const aiService = {

  chat: (messages: ChatMessage[]) =>
    apiPost<{ message: string }>('/ai/chat', { messages }),

  getInsight: () =>
    apiPost<AiInsight>('/ai/insights', {}),

  getConversations: () =>
    apiGet<Conversation[]>('/ai/conversations'),

  getReport: (month: number, year: number) =>
    apiGet<AiReport>(`/ai/reports/${month}/${year}`),
};
