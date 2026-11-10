import {
  allocated,
  buildRequest,
  evenAmounts,
  evenPercents,
  ExpenseInput,
  formatDate,
  ParticipantInput,
  parseDate,
  percentsFromShares,
  splitProblem,
  toBasisPoints,
  toMajor,
  toMinor,
} from './expense-form.logic';

function person(userId: string, overrides: Partial<ParticipantInput> = {}): ParticipantInput {
  return { userId, included: true, percent: null, amount: null, ...overrides };
}

describe('money conversion', () => {
  it.each([
    [12.34, 2, 1234],
    [0.1 + 0.2, 2, 30],
    [19.99, 2, 1999],
    [1.005, 2, 101],
    [500, 0, 500],
    [1.5, 3, 1500],
  ])('%s with %i digits is %i minor units', (major, digits, minor) => {
    expect(toMinor(major, digits)).toBe(minor);
  });

  it('converts back without float noise for typical amounts', () => {
    expect(toMajor(1234, 2)).toBe(12.34);
    expect(toMajor(500, 0)).toBe(500);
  });

  it('turns a percentage into basis points', () => {
    expect(toBasisPoints(33.33)).toBe(3333);
    expect(toBasisPoints(100)).toBe(10_000);
    expect(toBasisPoints(0.07)).toBe(7);
  });
});

describe('formatDate', () => {
  it('uses the local calendar day, not UTC', () => {
    expect(formatDate(new Date(2026, 10, 9, 23, 59))).toBe('2026-11-09');
    expect(formatDate(new Date(2026, 0, 5, 0, 1))).toBe('2026-01-05');
  });
});

describe('even splits', () => {
  it('shares percentages so that they add up to 100', () => {
    expect(evenPercents(2)).toEqual([50, 50]);
    expect(evenPercents(3)).toEqual([33.34, 33.33, 33.33]);
    expect(evenPercents(3).reduce((a, b) => a + b, 0)).toBeCloseTo(100);
    expect(evenPercents(1)).toEqual([100]);
    expect(evenPercents(0)).toEqual([]);
  });

  it('hands the leftover minor units to the first people', () => {
    expect(evenAmounts(1000, 3, 2)).toEqual([3.34, 3.33, 3.33]);
    expect(evenAmounts(1001, 2, 2)).toEqual([5.01, 5]);
    expect(evenAmounts(10, 3, 0)).toEqual([4, 3, 3]);
  });

  it('always adds up to the total', () => {
    for (const total of [1, 99, 1000, 12345]) {
      for (const count of [1, 2, 3, 7]) {
        const sum = evenAmounts(total, count, 2).reduce((a, b) => a + toMinor(b, 2), 0);
        expect(sum).toBe(total);
      }
    }
  });
});

describe('allocated', () => {
  const people = [
    person('a', { percent: 60, amount: 6 }),
    person('b', { percent: 40, amount: 4.5 }),
    person('c', { included: false, percent: 99, amount: 99 }),
  ];

  it('sums basis points of the included people only', () => {
    expect(allocated('PERCENTAGE', people, 2)).toBe(10_000);
  });

  it('sums minor units of the included people only', () => {
    expect(allocated('EXACT', people, 2)).toBe(1050);
  });

  it('is zero for an equal split', () => {
    expect(allocated('EQUAL', people, 2)).toBe(0);
  });
});

describe('splitProblem', () => {
  it('needs at least one person', () => {
    expect(splitProblem('EQUAL', [person('a', { included: false })], 1000, 2)).toContain(
      'at least one',
    );
    expect(splitProblem('EQUAL', [], 1000, 2)).toContain('at least one');
  });

  it('accepts any equal split with someone included', () => {
    expect(splitProblem('EQUAL', [person('a'), person('b')], 1000, 2)).toBeNull();
  });

  it('wants percentages that add up to 100', () => {
    const ok = [person('a', { percent: 70 }), person('b', { percent: 30 })];
    const short = [person('a', { percent: 70 }), person('b', { percent: 20 })];

    expect(splitProblem('PERCENTAGE', ok, 1000, 2)).toBeNull();
    expect(splitProblem('PERCENTAGE', short, 1000, 2)).toBe('Percentages must add up to 100%.');
  });

  it('tolerates the rounding of thirds', () => {
    const thirds = [
      person('a', { percent: 33.34 }),
      person('b', { percent: 33.33 }),
      person('c', { percent: 33.33 }),
    ];

    expect(splitProblem('PERCENTAGE', thirds, 1000, 2)).toBeNull();
  });

  it('wants a percentage for everyone who is included', () => {
    const missing = [person('a', { percent: 100 }), person('b')];
    const out = [person('a', { percent: 150 })];

    expect(splitProblem('PERCENTAGE', missing, 1000, 2)).toContain('Enter a percentage');
    expect(splitProblem('PERCENTAGE', out, 1000, 2)).toContain('between 0 and 100');
  });

  it('wants exact amounts that add up to the total', () => {
    const ok = [person('a', { amount: 6 }), person('b', { amount: 4 })];
    const off = [person('a', { amount: 6 }), person('b', { amount: 3.99 })];

    expect(splitProblem('EXACT', ok, 1000, 2)).toBeNull();
    expect(splitProblem('EXACT', off, 1000, 2)).toBe('Amounts must add up to the total.');
  });

  it('wants an amount for everyone who is included', () => {
    expect(splitProblem('EXACT', [person('a', { amount: 5 }), person('b')], 500, 2)).toContain(
      'Enter an amount',
    );
  });

  it('cannot compare amounts before the total is known', () => {
    expect(splitProblem('EXACT', [person('a', { amount: 5 })], null, 2)).toBeNull();
  });

  it('ignores people who are left out', () => {
    const people = [person('a', { percent: 100 }), person('b', { included: false })];

    expect(splitProblem('PERCENTAGE', people, 1000, 2)).toBeNull();
  });
});

describe('buildRequest', () => {
  const base: ExpenseInput = {
    description: '  Dinner  ',
    amount: 45.5,
    currency: 'PLN',
    paidBy: 'u1',
    splitType: 'EQUAL',
    date: new Date(2026, 10, 9),
    participants: [person('u1'), person('u2'), person('u3', { included: false })],
  };

  it('sends an equal split with only user ids', () => {
    expect(buildRequest(base, 2)).toEqual({
      description: 'Dinner',
      amount: 4550,
      currency: 'PLN',
      paidBy: 'u1',
      splitType: 'EQUAL',
      expenseDate: '2026-11-09',
      participants: [{ userId: 'u1' }, { userId: 'u2' }],
    });
  });

  it('sends percentages as basis points and nothing else', () => {
    const request = buildRequest(
      {
        ...base,
        splitType: 'PERCENTAGE',
        participants: [
          person('u1', { percent: 60, amount: 1 }),
          person('u2', { percent: 40, amount: 2 }),
        ],
      },
      2,
    );

    expect(request.participants).toEqual([
      { userId: 'u1', basisPoints: 6000 },
      { userId: 'u2', basisPoints: 4000 },
    ]);
  });

  it('sends exact shares in minor units and nothing else', () => {
    const request = buildRequest(
      {
        ...base,
        splitType: 'EXACT',
        participants: [
          person('u1', { amount: 20, percent: 5 }),
          person('u2', { amount: 25.5, percent: 5 }),
        ],
      },
      2,
    );

    expect(request.participants).toEqual([
      { userId: 'u1', amount: 2000 },
      { userId: 'u2', amount: 2550 },
    ]);
  });

  it('leaves the date out when none was chosen', () => {
    expect(buildRequest({ ...base, date: null }, 2)).not.toHaveProperty('expenseDate');
  });

  it('respects a currency without minor units', () => {
    expect(buildRequest({ ...base, currency: 'JPY', amount: 1500 }, 0).amount).toBe(1500);
  });
});

describe('percentsFromShares', () => {
  it('turns shares back into percentages that add up to 100', () => {
    expect(percentsFromShares([5000, 5000], 10000)).toEqual([50, 50]);
    expect(percentsFromShares([2562, 1098], 3660)).toEqual([70, 30]);
  });

  it('hands the leftover basis points to the biggest fractions', () => {
    const percents = percentsFromShares([3334, 3333, 3333], 10000);

    expect(percents).toEqual([33.34, 33.33, 33.33]);
    expect(percents.reduce((a, b) => a + b, 0)).toBeCloseTo(100);
  });

  it('always adds up to exactly 100 for awkward amounts', () => {
    for (const total of [1001, 4550, 12345, 99999]) {
      const shares = [
        Math.floor(total / 3) + 1,
        Math.floor(total / 3),
        total - 2 * Math.floor(total / 3) - 1,
      ];
      const bp = percentsFromShares(shares, total).map((p) => Math.round(p * 100));

      expect(bp.reduce((a, b) => a + b, 0)).toBe(10_000);
    }
  });

  it('copes with nothing to split', () => {
    expect(percentsFromShares([], 100)).toEqual([]);
    expect(percentsFromShares([0, 0], 0)).toEqual([0, 0]);
  });

  it('falls back to plain rounding when the shares do not add up to the total', () => {
    expect(percentsFromShares([100, 100], 1000)).toEqual([10, 10]);
  });
});

describe('parseDate', () => {
  it('reads a calendar day as a local date, not UTC', () => {
    const date = parseDate('2026-11-09');

    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 10, 9]);
    expect(formatDate(date)).toBe('2026-11-09');
  });
});
