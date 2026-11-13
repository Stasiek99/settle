import { inject, LOCALE_ID, Pipe, PipeTransform } from '@angular/core';

const DEFAULT_DIGITS = 2;

export const CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP', 'CHF', 'CZK'];

export function isKnownCurrency(code: string): boolean {
  try {
    return Intl.supportedValuesOf('currency').includes(code);
  } catch {
    return false;
  }
}

export function currencyChoices(...extra: (string | undefined)[]): string[] {
  const codes = [...extra, ...CURRENCIES].filter((code): code is string => !!code);
  return [...new Set(codes)];
}

export function minorUnitDigits(currency: string): number {
  try {
    return (
      new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? DEFAULT_DIGITS
    );
  } catch {
    return DEFAULT_DIGITS;
  }
}

export function formatMoney(minor: number, currency: string, locale?: string): string {
  const digits = minorUnitDigits(currency);
  const value = minor / 10 ** digits;
  try {
    return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(value);
  } catch {
    return `${value.toFixed(digits)} ${currency}`;
  }
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(minor: number, currency: string): string {
    return formatMoney(minor, currency, this.locale);
  }
}
