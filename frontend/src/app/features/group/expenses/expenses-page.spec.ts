import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { providePrimeNG } from 'primeng/config';

import { Expense, Page } from '../../../core/models';
import { click, query, settle, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { ExpensesPage } from './expenses-page';

const NAMES: Record<string, string> = { u1: 'Ann', u2: 'Bob' };

function expense(description: string, overrides: Partial<Expense> = {}): Expense {
  return {
    id: `id-${description}`,
    groupId: 'g1',
    paidBy: 'u1',
    description,
    amount: 10000,
    currency: 'USD',
    splitType: 'EQUAL',
    expenseDate: '2026-11-09',
    createdAt: '2026-11-09T10:00:00Z',
    splits: [
      { userId: 'u1', amount: 5000 },
      { userId: 'u2', amount: 5000 },
    ],
    ...overrides,
  };
}

function page(content: Expense[], overrides: Partial<Page<Expense>> = {}): Page<Expense> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...overrides };
}

describe('ExpensesPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        {
          provide: GroupStore,
          useValue: { groupId: () => 'g1', nameOf: (id: string) => NAMES[id] ?? 'Unknown member' },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectList(pageNumber = 0): TestRequest {
    return http.expectOne(
      (r) => r.url === '/api/groups/g1/expenses' && r.params.get('page') === String(pageNumber),
    );
  }

  async function render(first: Page<Expense> = page([expense('Dinner')])) {
    const fixture = TestBed.createComponent(ExpensesPage);
    fixture.detectChanges();
    expectList().flush(first);
    await settle(fixture);
    return fixture;
  }

  it('shows placeholders while loading', () => {
    const fixture = TestBed.createComponent(ExpensesPage);
    fixture.detectChanges();

    expect(query(fixture, '[aria-busy="true"]')).not.toBeNull();
    expectList().flush(page([]));
  });

  it("shows who paid, the total and everyone's share", async () => {
    const fixture = await render();

    const content = text(fixture);
    expect(content).toContain('Dinner');
    expect(content).toContain('Paid by Ann');
    expect(content).toContain('$100.00');
    expect(content).toContain('Ann: $50.00');
    expect(content).toContain('Bob: $50.00');
    expect(content).toContain('Equal');
  });

  it('labels the way the cost was split', async () => {
    const fixture = await render(
      page([expense('Rent', { splitType: 'PERCENTAGE' }), expense('Taxi', { splitType: 'EXACT' })]),
    );

    expect(text(fixture)).toContain('Percentage');
    expect(text(fixture)).toContain('Exact');
  });

  it('shows an empty state', async () => {
    const fixture = await render(page([]));

    expect(text(fixture)).toContain('No expenses yet.');
  });

  it('shows the error and retries', async () => {
    const fixture = TestBed.createComponent(ExpensesPage);
    fixture.detectChanges();
    expectList().flush({ detail: 'x' }, { status: 500, statusText: 'Server Error' });
    await settle(fixture);
    expect(text(fixture)).toContain('server had a problem');

    click(fixture, 'p-button[label="Try again"] button');
    expectList().flush(page([expense('Dinner')]));
    await settle(fixture);

    expect(text(fixture)).toContain('Dinner');
  });

  it('loads the next page from the paginator', async () => {
    const fixture = await render(page([expense('Dinner')], { totalElements: 25, totalPages: 3 }));

    query<HTMLButtonElement>(fixture, 'p-paginator .p-paginator-next')?.click();
    expectList(1).flush(page([expense('Lunch')], { page: 1, totalElements: 25, totalPages: 3 }));
    await settle(fixture);

    expect(text(fixture)).toContain('Lunch');
    expect(text(fixture)).not.toContain('Dinner');
  });
});
