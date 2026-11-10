import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../../core/auth/auth.service';
import { Expense, Member, Page, User } from '../../../core/models';
import { click, query, settle, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { ExpensesPage } from './expenses-page';

const NAMES: Record<string, string> = { u1: 'Ann', u2: 'Bob', u3: 'Cid' };
const MEMBERS: Member[] = [
  { id: 'm1', userId: 'u1', displayName: 'Ann', role: 'OWNER', joinedAt: '' },
  { id: 'm2', userId: 'u2', displayName: 'Bob', role: 'MEMBER', joinedAt: '' },
  { id: 'm3', userId: 'u3', displayName: 'Cid', role: 'MEMBER', joinedAt: '' },
];

function user(id: string): User {
  return { id, email: `${id}@example.com`, displayName: NAMES[id], createdAt: '' };
}

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
    version: 3,
    ...overrides,
  };
}

function page(content: Expense[], overrides: Partial<Page<Expense>> = {}): Page<Expense> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  };
}

describe('ExpensesPage', () => {
  let http: HttpTestingController;
  const me = signal<User | null>(user('u1'));

  beforeEach(() => {
    me.set(user('u1'));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        provideRouter([]),
        {
          provide: GroupStore,
          useValue: {
            groupId: () => 'g1',
            members: () => MEMBERS,
            nameOf: (id: string) => NAMES[id] ?? 'Unknown member',
          },
        },
        { provide: AuthService, useValue: { user: me.asReadonly() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectList(pageNumber = 0): TestRequest {
    return http.expectOne(
      (r) =>
        r.url === '/api/groups/g1/expenses' &&
        r.method === 'GET' &&
        r.params.get('page') === String(pageNumber),
    );
  }

  async function render(first: Page<Expense> = page([expense('Dinner')])) {
    const fixture = TestBed.createComponent(ExpensesPage);
    fixture.detectChanges();
    expectList().flush(first);
    await settle(fixture);
    return fixture;
  }

  it('offers a link to add an expense', async () => {
    const fixture = await render();

    const link = query<HTMLAnchorElement>(fixture, 'a[href="/new"]');
    expect(link?.textContent).toContain('Add expense');
  });

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

  describe('changing an expense', () => {
    it('lets the payer edit and delete their own expense', async () => {
      me.set(user('u2'));
      const fixture = await render(page([expense('Taxi', { paidBy: 'u2' })]));

      expect(query(fixture, 'a[href="/id-Taxi/edit"]')).not.toBeNull();
      expect(query(fixture, 'p-button[label="Delete"]')).not.toBeNull();
    });

    it('lets the group owner change an expense somebody else paid', async () => {
      const fixture = await render(page([expense('Taxi', { paidBy: 'u2' })]));

      expect(query(fixture, 'a[href="/id-Taxi/edit"]')).not.toBeNull();
      expect(query(fixture, 'p-button[label="Delete"]')).not.toBeNull();
    });

    it('hides the actions from a member who neither paid nor owns the group', async () => {
      me.set(user('u3'));
      const fixture = await render(page([expense('Taxi', { paidBy: 'u2' })]));

      expect(query(fixture, 'a[href="/id-Taxi/edit"]')).toBeNull();
      expect(query(fixture, 'p-button[label="Delete"]')).toBeNull();
    });

    it('hides the actions until the current user is known', async () => {
      me.set(null);
      const fixture = await render();

      expect(query(fixture, 'p-button[label="Delete"]')).toBeNull();
    });

    function dialogButton(label: 'Delete' | 'Cancel'): HTMLButtonElement {
      const selector =
        label === 'Delete' ? '.p-confirmdialog-accept-button' : '.p-confirmdialog-reject-button';
      const button = document.querySelector<HTMLButtonElement>(selector);
      if (!button) {
        throw new Error(`No "${label}" button in the confirmation dialog`);
      }
      return button;
    }

    async function openConfirmation(fixture: Awaited<ReturnType<typeof render>>): Promise<void> {
      click(fixture, 'p-button[label="Delete"] button');
      await settle(fixture);
    }

    it('asks for confirmation and does nothing when cancelled', async () => {
      const fixture = await render();

      await openConfirmation(fixture);
      expect(document.body.textContent).toContain('Delete "Dinner"?');
      dialogButton('Cancel').click();
      await settle(fixture);

      http.expectNone((r) => r.method === 'DELETE');
    });

    it('deletes with the version it showed and reloads the list', async () => {
      const fixture = await render(page([expense('Dinner'), expense('Lunch')]));

      await openConfirmation(fixture);
      dialogButton('Delete').click();

      const request = http.expectOne((r) => r.method === 'DELETE');
      expect(request.request.url).toBe('/api/groups/g1/expenses/id-Dinner');
      expect(request.request.headers.get('If-Match')).toBe('"3"');
      request.flush(null, { status: 204, statusText: 'No Content' });
      expectList().flush(page([expense('Lunch')]));
      await settle(fixture);

      expect(text(fixture)).not.toContain('Dinner');
      expect(text(fixture)).toContain('Lunch');
    });

    it('goes back a page when the last expense of a later page was deleted', async () => {
      const fixture = await render(page([expense('Dinner')], { totalElements: 11, totalPages: 2 }));
      query<HTMLButtonElement>(fixture, 'p-paginator .p-paginator-next')?.click();
      expectList(1).flush(page([expense('Lonely')], { page: 1, totalElements: 11, totalPages: 2 }));
      await settle(fixture);

      await openConfirmation(fixture);
      dialogButton('Delete').click();
      http
        .expectOne((r) => r.method === 'DELETE')
        .flush(null, { status: 204, statusText: 'No Content' });
      expectList(0).flush(page([expense('Dinner')], { totalElements: 10 }));
      await settle(fixture);

      expect(text(fixture)).toContain('Dinner');
    });

    it('explains why it was refused and keeps the expense', async () => {
      const fixture = await render();

      await openConfirmation(fixture);
      dialogButton('Delete').click();
      http
        .expectOne((r) => r.method === 'DELETE')
        .flush(
          { detail: 'Only the payer or the group owner can change this expense' },
          { status: 403, statusText: 'Forbidden' },
        );
      await settle(fixture);

      expect(text(fixture)).toContain('Only the payer or the group owner');
      expect(text(fixture)).toContain('Dinner');
    });

    it('reloads the list when somebody changed the expense in the meantime', async () => {
      const fixture = await render();

      await openConfirmation(fixture);
      dialogButton('Delete').click();
      http
        .expectOne((r) => r.method === 'DELETE')
        .flush(
          { detail: 'The resource was modified by someone else; reload it and retry' },
          { status: 412, statusText: 'Precondition Failed' },
        );
      expectList().flush(page([expense('Dinner', { version: 4 })]));
      await settle(fixture);

      expect(text(fixture)).toContain('modified by someone else');
    });
  });
});
