import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../../core/auth/auth.service';
import { Expense, Group, Member, User } from '../../../core/models';
import { click, inputValue, query, setValue, settle, submit, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { ExpenseFormPage } from './expense-form-page';

const GROUP: Group = {
  id: 'g1',
  name: 'Flat',
  currency: 'PLN',
  createdBy: 'u1',
  createdAt: '',
  version: 0,
};
const MEMBERS: Member[] = [
  { id: 'm1', userId: 'u1', displayName: 'Ann', role: 'OWNER', joinedAt: '' },
  { id: 'm2', userId: 'u2', displayName: 'Bob', role: 'MEMBER', joinedAt: '' },
  { id: 'm3', userId: 'u3', displayName: 'Cid', role: 'MEMBER', joinedAt: '' },
];
const ANN: User = { id: 'u1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' };

function expense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 'e1',
    groupId: 'g1',
    paidBy: 'u2',
    description: 'Taxi',
    amount: 4550,
    currency: 'PLN',
    splitType: 'EQUAL',
    expenseDate: '2026-11-08',
    createdAt: '2026-11-08T10:00:00Z',
    splits: [
      { userId: 'u1', amount: 2275 },
      { userId: 'u2', amount: 2275 },
    ],
    version: 7,
    ...overrides,
  };
}

describe('ExpenseFormPage in edit mode', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        providePrimeNG(),
        {
          provide: GroupStore,
          useValue: {
            group: () => GROUP,
            members: () => MEMBERS,
            groupId: () => 'g1',
            nameOf: (id: string) => MEMBERS.find((m) => m.userId === id)?.displayName ?? '?',
          },
        },
        { provide: AuthService, useValue: { user: signal<User | null>(ANN).asReadonly() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function render(loaded: Expense | null = expense()) {
    const fixture = TestBed.createComponent(ExpenseFormPage);
    fixture.componentRef.setInput('expenseId', 'e1');
    fixture.detectChanges();
    if (loaded) {
      http.expectOne('/api/groups/g1/expenses/e1').flush(loaded);
    }
    await settle(fixture);
    return fixture;
  }

  function expectPut() {
    return http.expectOne((r) => r.url === '/api/groups/g1/expenses/e1' && r.method === 'PUT');
  }

  it('asks for the expense and shows placeholders until it arrives', () => {
    const fixture = TestBed.createComponent(ExpenseFormPage);
    fixture.componentRef.setInput('expenseId', 'e1');
    fixture.detectChanges();

    expect(text(fixture)).toContain('Edit expense');
    expect(query(fixture, '[aria-busy="true"]')).not.toBeNull();
    http.expectOne('/api/groups/g1/expenses/e1').flush(expense());
  });

  it('fills the form with what was saved', async () => {
    const fixture = await render();

    expect(inputValue(fixture, '#description')).toBe('Taxi');
    expect(inputValue(fixture, '#amount')).toContain('45.50');
    expect(inputValue(fixture, '#expenseDate')).toContain('8 Nov 2026');
    expect(text(fixture)).toContain('Bob');
    expect(text(fixture)).toContain('Split equally between 2 people.');
    expect(query(fixture, 'button[type=submit]')?.textContent).toContain('Save changes');
  });

  it('keeps out the people who were not part of the split', async () => {
    const fixture = await render();

    const boxes = Array.from(
      fixture.nativeElement.querySelectorAll('p-checkbox input'),
    ) as HTMLInputElement[];
    expect(boxes.map((box) => box.checked)).toEqual([true, true, false]);
  });

  it('saves with the version it loaded and goes back to the list', async () => {
    const fixture = await render();
    setValue(fixture, '#description', 'Taxi to the airport');

    submit(fixture);

    const request = expectPut();
    expect(request.request.headers.get('If-Match')).toBe('"7"');
    expect(request.request.body).toMatchObject({
      description: 'Taxi to the airport',
      amount: 4550,
      currency: 'PLN',
      paidBy: 'u2',
      splitType: 'EQUAL',
      expenseDate: '2026-11-08',
      participants: [{ userId: 'u1' }, { userId: 'u2' }],
    });
    request.flush(expense({ version: 8 }));
    expect(navigate).toHaveBeenCalledWith(['/groups', 'g1', 'expenses']);
    http.expectNone((r) => r.method === 'POST');
  });

  it('shows a percentage split as the percentages it was made from', async () => {
    const fixture = await render(
      expense({
        splitType: 'PERCENTAGE',
        amount: 10000,
        splits: [
          { userId: 'u1', amount: 3334 },
          { userId: 'u2', amount: 3333 },
          { userId: 'u3', amount: 3333 },
        ],
      }),
    );

    expect(inputValue(fixture, 'input[aria-label="Percentage for Ann"]')).toContain('33.34');
    expect(inputValue(fixture, 'input[aria-label="Percentage for Bob"]')).toContain('33.33');
    expect(text(fixture)).toContain('Total: 100% of 100%');

    submit(fixture);
    expect(expectPut().request.body.participants).toEqual([
      { userId: 'u1', basisPoints: 3334 },
      { userId: 'u2', basisPoints: 3333 },
      { userId: 'u3', basisPoints: 3333 },
    ]);
  });

  it('shows an exact split as the amounts it was made from', async () => {
    const fixture = await render(
      expense({
        splitType: 'EXACT',
        amount: 4550,
        splits: [
          { userId: 'u1', amount: 2000 },
          { userId: 'u2', amount: 2550 },
        ],
      }),
    );

    expect(inputValue(fixture, 'input[aria-label="Amount for Ann"]')).toContain('20.00');
    expect(inputValue(fixture, 'input[aria-label="Amount for Bob"]')).toContain('25.50');
    expect(text(fixture)).toMatch(/Remaining: PLN\s0\.00/);
  });

  it('says the expense was changed by someone else and offers the latest version', async () => {
    const fixture = await render();
    setValue(fixture, '#description', 'Mine');

    submit(fixture);
    expectPut().flush(
      { detail: 'The resource was modified by someone else; reload it and retry' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await settle(fixture);

    expect(text(fixture)).toContain('modified by someone else');
    expect(navigate).not.toHaveBeenCalled();

    click(fixture, 'p-button[label="Load the latest version"] button');
    http
      .expectOne('/api/groups/g1/expenses/e1')
      .flush(expense({ description: 'Theirs', version: 8 }));
    await settle(fixture);

    expect(inputValue(fixture, '#description')).toBe('Theirs');
    expect(text(fixture)).not.toContain('modified by someone else');

    submit(fixture);
    expect(expectPut().request.headers.get('If-Match')).toBe('"8"');
  });

  it('explains a refusal and stays on the form', async () => {
    const fixture = await render();

    submit(fixture);
    expectPut().flush(
      { detail: 'Only the payer or the group owner can change this expense' },
      { status: 403, statusText: 'Forbidden' },
    );
    await settle(fixture);

    expect(text(fixture)).toContain('Only the payer or the group owner');
    expect(text(fixture)).not.toContain('Load the latest version');
  });

  it('shows why the expense could not be loaded and can try again', async () => {
    const fixture = await render(null);
    http
      .expectOne('/api/groups/g1/expenses/e1')
      .flush({ detail: 'Expense e1 not found' }, { status: 404, statusText: 'Not Found' });
    await settle(fixture);

    expect(text(fixture)).toContain('Expense e1 not found');
    expect(query(fixture, 'p-fluid')).toBeNull();
    expect(query(fixture, 'a[href="/groups/g1/expenses"]')?.textContent).toContain(
      'Back to expenses',
    );

    click(fixture, 'p-button[label="Try again"] button');
    http.expectOne('/api/groups/g1/expenses/e1').flush(expense());
    await settle(fixture);

    expect(inputValue(fixture, '#description')).toBe('Taxi');
  });
});
