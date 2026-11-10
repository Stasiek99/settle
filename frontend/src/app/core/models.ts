export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface Group {
  id: string;
  name: string;
  currency: string;
  createdBy: string;
  createdAt: string;
  version: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

export type MembershipRole = 'OWNER' | 'MEMBER';

export interface Member {
  id: string;
  userId: string;
  displayName: string;
  role: MembershipRole;
  joinedAt: string;
}

export type SplitType = 'EQUAL' | 'PERCENTAGE' | 'EXACT';

export interface ExpenseSplit {
  userId: string;
  amount: number;
}

export interface Expense {
  id: string;
  groupId: string;
  paidBy: string;
  description: string;
  amount: number;
  currency: string;
  splitType: SplitType;
  expenseDate: string;
  createdAt: string;
  splits: ExpenseSplit[];
}

export interface Settlement {
  id: string;
  groupId: string;
  paidBy: string;
  paidTo: string;
  amount: number;
  currency: string;
  settledOn: string;
  createdAt: string;
}

export interface MemberBalance {
  userId: string;
  displayName: string;
  balance: number;
}

export interface GroupBalances {
  currency: string;
  balances: MemberBalance[];
}

export interface SuggestedTransfer {
  paidBy: string;
  paidTo: string;
  amount: number;
}

export interface SettlementSuggestions {
  currency: string;
  transfers: SuggestedTransfer[];
}

export interface ParticipantRequest {
  userId: string;
  basisPoints?: number;
  amount?: number;
}

export interface CreateExpenseRequest {
  description: string;
  amount: number;
  currency: string;
  paidBy: string;
  splitType: SplitType;
  expenseDate?: string;
  participants: ParticipantRequest[];
}
