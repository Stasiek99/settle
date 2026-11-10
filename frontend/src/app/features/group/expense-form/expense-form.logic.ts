import { CreateExpenseRequest, SplitType } from '../../../core/models';

export const FULL_BASIS_POINTS = 10_000;
export const MAX_AMOUNT_MINOR = 1_000_000_000_000;

export interface ParticipantInput {
  userId: string;
  included: boolean;
  percent: number | null;
  amount: number | null;
}

export interface ExpenseInput {
  description: string;
  amount: number;
  currency: string;
  paidBy: string;
  splitType: SplitType;
  date: Date | null;
  participants: ParticipantInput[];
}

export function toMinor(major: number, digits: number): number {
  return Math.round(Number((major * 10 ** digits).toPrecision(15)));
}

export function toMajor(minor: number, digits: number): number {
  return minor / 10 ** digits;
}

export function toBasisPoints(percent: number): number {
  return Math.round(Number((percent * 100).toPrecision(15)));
}

export function formatDate(date: Date): string {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function spread(total: number, count: number): number[] {
  if (count <= 0) {
    return [];
  }
  const base = Math.floor(total / count);
  const remainder = total - base * count;
  return Array.from({ length: count }, (_, index) => base + (index < remainder ? 1 : 0));
}

export function evenPercents(count: number): number[] {
  return spread(FULL_BASIS_POINTS, count).map((basisPoints) => basisPoints / 100);
}

export function evenAmounts(totalMinor: number, count: number, digits: number): number[] {
  return spread(totalMinor, count).map((minor) => toMajor(minor, digits));
}

export function includedOf(participants: ParticipantInput[]): ParticipantInput[] {
  return participants.filter((participant) => participant.included);
}

export function allocated(
  splitType: SplitType,
  participants: ParticipantInput[],
  digits: number,
): number {
  const included = includedOf(participants);
  if (splitType === 'PERCENTAGE') {
    return included.reduce((sum, p) => sum + toBasisPoints(p.percent ?? 0), 0);
  }
  if (splitType === 'EXACT') {
    return included.reduce((sum, p) => sum + toMinor(p.amount ?? 0, digits), 0);
  }
  return 0;
}

export function splitProblem(
  splitType: SplitType,
  participants: ParticipantInput[],
  totalMinor: number | null,
  digits: number,
): string | null {
  const included = includedOf(participants);
  if (included.length === 0) {
    return 'Choose at least one person to split with.';
  }
  if (splitType === 'PERCENTAGE') {
    if (included.some((p) => p.percent === null || p.percent < 0 || p.percent > 100)) {
      return 'Enter a percentage between 0 and 100 for everyone included.';
    }
    if (allocated(splitType, participants, digits) !== FULL_BASIS_POINTS) {
      return 'Percentages must add up to 100%.';
    }
  }
  if (splitType === 'EXACT') {
    if (included.some((p) => p.amount === null || p.amount < 0)) {
      return 'Enter an amount for everyone included.';
    }
    if (totalMinor !== null && allocated(splitType, participants, digits) !== totalMinor) {
      return 'Amounts must add up to the total.';
    }
  }
  return null;
}

export function buildRequest(input: ExpenseInput, digits: number): CreateExpenseRequest {
  const participants = includedOf(input.participants).map((participant) => {
    switch (input.splitType) {
      case 'PERCENTAGE':
        return { userId: participant.userId, basisPoints: toBasisPoints(participant.percent ?? 0) };
      case 'EXACT':
        return { userId: participant.userId, amount: toMinor(participant.amount ?? 0, digits) };
      default:
        return { userId: participant.userId };
    }
  });

  return {
    description: input.description.trim(),
    amount: toMinor(input.amount, digits),
    currency: input.currency,
    paidBy: input.paidBy,
    splitType: input.splitType,
    ...(input.date ? { expenseDate: formatDate(input.date) } : {}),
    participants,
  };
}
