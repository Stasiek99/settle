import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';

import { formatMoney, MoneyPipe, minorUnitDigits } from './money';

describe('minorUnitDigits', () => {
  it.each([
    ['PLN', 2],
    ['EUR', 2],
    ['JPY', 0],
    ['KWD', 3],
  ])('%s has %i minor unit digits', (currency, digits) => {
    expect(minorUnitDigits(currency)).toBe(digits);
  });

  it('falls back to two digits for a code Intl does not know', () => {
    expect(minorUnitDigits('not-a-currency')).toBe(2);
  });
});

describe('formatMoney', () => {
  it('converts minor units to the major unit and formats with the currency', () => {
    expect(formatMoney(12345, 'USD', 'en-US')).toBe('$123.45');
  });

  it('formats negative amounts', () => {
    expect(formatMoney(-250, 'USD', 'en-US')).toBe('-$2.50');
  });

  it('does not divide a currency without minor units', () => {
    expect(formatMoney(500, 'JPY', 'en-US')).toBe('¥500');
  });

  it('keeps grouping and decimals in the Polish locale', () => {
    expect(formatMoney(1234567, 'PLN', 'pl-PL').replace(/\s/g, ' ')).toBe('12 345,67 zł');
  });

  it('shows a plain amount when the currency is not valid', () => {
    expect(formatMoney(123, 'not-a-currency', 'en-US')).toBe('1.23 not-a-currency');
  });
});

describe('MoneyPipe', () => {
  function pipe(locale: string): MoneyPipe {
    TestBed.configureTestingModule({
      providers: [{ provide: LOCALE_ID, useValue: locale }, MoneyPipe],
    });
    return TestBed.inject(MoneyPipe);
  }

  it('formats an amount in minor units with the app locale', () => {
    expect(pipe('en-US').transform(100, 'USD')).toBe('$1.00');
  });

  it('follows the locale it is configured with', () => {
    expect(pipe('pl-PL').transform(1234567, 'PLN').replace(/\s/g, ' ')).toBe('12 345,67 zł');
  });
});
