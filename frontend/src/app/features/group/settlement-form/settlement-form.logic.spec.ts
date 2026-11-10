import { buildSettlementRequest, prefillFromParams } from './settlement-form.logic';

describe('buildSettlementRequest', () => {
  it('sends the amount in minor units with the local date', () => {
    expect(
      buildSettlementRequest(
        {
          paidBy: 'u2',
          paidTo: 'u1',
          amount: 603.49,
          currency: 'PLN',
          date: new Date(2026, 10, 9),
        },
        2,
      ),
    ).toEqual({
      paidBy: 'u2',
      paidTo: 'u1',
      amount: 60349,
      currency: 'PLN',
      settledOn: '2026-11-09',
    });
  });

  it('leaves the date out when none was chosen', () => {
    const request = buildSettlementRequest(
      { paidBy: 'u2', paidTo: 'u1', amount: 5, currency: 'PLN', date: null },
      2,
    );

    expect(request).not.toHaveProperty('settledOn');
  });
});

describe('prefillFromParams', () => {
  const members = ['u1', 'u2', 'u3'];

  it('accepts a suggested transfer', () => {
    expect(prefillFromParams({ from: 'u2', to: 'u1', amount: '60349' }, members, 2)).toEqual({
      paidBy: 'u2',
      paidTo: 'u1',
      amount: 603.49,
    });
  });

  it('ignores people who are not members', () => {
    expect(prefillFromParams({ from: 'intruder', to: 'u1' }, members, 2)).toEqual({ paidTo: 'u1' });
  });

  it('ignores a transfer from a person to themselves', () => {
    expect(prefillFromParams({ from: 'u1', to: 'u1' }, members, 2)).toEqual({});
  });

  it.each(['', '0', '-5', '1.5', 'abc', '1e9', '99999999999999', '١٢٣'])(
    'ignores the amount "%s"',
    (amount) => {
      expect(prefillFromParams({ amount }, members, 2)).toEqual({});
    },
  );

  it('respects currencies without minor units', () => {
    expect(prefillFromParams({ amount: '500' }, members, 0)).toEqual({ amount: 500 });
  });

  it('does nothing without parameters', () => {
    expect(prefillFromParams({}, members, 2)).toEqual({});
  });
});
