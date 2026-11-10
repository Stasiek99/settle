import { CreateSettlementRequest } from '../../../core/models';
import { formatDate, MAX_AMOUNT_MINOR, toMajor, toMinor } from '../expense-form/expense-form.logic';

export interface SettlementInput {
  paidBy: string;
  paidTo: string;
  amount: number;
  currency: string;
  date: Date | null;
}

export interface SettlementPrefill {
  paidBy?: string;
  paidTo?: string;
  amount?: number;
}

export function buildSettlementRequest(
  input: SettlementInput,
  digits: number,
): CreateSettlementRequest {
  return {
    paidBy: input.paidBy,
    paidTo: input.paidTo,
    amount: toMinor(input.amount, digits),
    currency: input.currency,
    ...(input.date ? { settledOn: formatDate(input.date) } : {}),
  };
}

export function prefillFromParams(
  params: { from?: string; to?: string; amount?: string },
  memberIds: string[],
  digits: number,
): SettlementPrefill {
  const prefill: SettlementPrefill = {};
  const from =
    params.from !== undefined && memberIds.includes(params.from) ? params.from : undefined;
  const to = params.to !== undefined && memberIds.includes(params.to) ? params.to : undefined;
  if (from !== undefined && from !== to) {
    prefill.paidBy = from;
  }
  if (to !== undefined && from !== to) {
    prefill.paidTo = to;
  }
  if (params.amount !== undefined && /^\d{1,13}$/.test(params.amount)) {
    const minor = Number(params.amount);
    if (minor > 0 && minor <= MAX_AMOUNT_MINOR) {
      prefill.amount = toMajor(minor, digits);
    }
  }
  return prefill;
}
