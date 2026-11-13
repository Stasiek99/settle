import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../../core/auth/auth.service';
import { Group, Member, User } from '../../../core/models';
import {
  click,
  clickText,
  inputValue,
  query,
  setNumber,
  setValue,
  settle,
  submit,
  text,
} from '../../../testing/dom';
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
];
const ANN: User = { id: 'u1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' };

describe('ExpenseFormPage', () => {
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

  async function render() {
    const fixture = TestBed.createComponent(ExpenseFormPage);
    fixture.detectChanges();
    await settle(fixture);
    return fixture;
  }

  async function fillBasics(fixture: Awaited<ReturnType<typeof render>>, amount = '100') {
    setValue(fixture, '#description', '  Dinner  ');
    setNumber(fixture, '#amount', amount);
    await settle(fixture);
  }

  function expectPost() {
    return http.expectOne((r) => r.url === '/api/groups/g1/expenses' && r.method === 'POST');
  }

  it('starts with everyone included, an equal split and the current user as payer', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('Split equally between 2 people.');
    expect(text(fixture)).toContain('Ann');
    expect(text(fixture)).toContain('Bob');
    expect(fixture.nativeElement.querySelectorAll('.expense__person')).toHaveLength(2);
    expect(text(fixture)).not.toContain('Who paid?');
  });

  it('does not call the API and explains what is missing', async () => {
    const fixture = await render();

    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Describe what the money was spent on.');
    expect(text(fixture)).toContain('Enter an amount greater than zero.');
  });

  it('sends an equal split with the amount in minor units', async () => {
    const fixture = await render();
    await fillBasics(fixture, '45.50');

    submit(fixture);

    const request = expectPost();
    expect(request.request.body).toMatchObject({
      description: 'Dinner',
      amount: 4550,
      currency: 'PLN',
      paidBy: 'u1',
      splitType: 'EQUAL',
      participants: [{ userId: 'u1' }, { userId: 'u2' }],
    });
    expect(request.request.body.expenseDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    request.flush({}, { status: 201, statusText: 'Created' });
    expect(navigate).toHaveBeenCalledWith(['/groups', 'g1', 'expenses']);
  });

  it('leaves out people who are unticked', async () => {
    const fixture = await render();
    await fillBasics(fixture);

    const boxes = fixture.nativeElement.querySelectorAll('p-checkbox input');
    (boxes[1] as HTMLInputElement).click();
    await settle(fixture);
    submit(fixture);

    expect(expectPost().request.body.participants).toEqual([{ userId: 'u1' }]);
  });

  it('refuses a split with nobody in it', async () => {
    const fixture = await render();
    await fillBasics(fixture);

    fixture.nativeElement
      .querySelectorAll('p-checkbox input')
      .forEach((box: HTMLInputElement) => box.click());
    await settle(fixture);
    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Choose at least one person');
  });

  it('prefills a percentage split evenly and sends basis points', async () => {
    const fixture = await render();
    await fillBasics(fixture);

    clickText(fixture, 'p-selectbutton', 'Percentage');
    await settle(fixture);

    expect(text(fixture)).toContain('Total: 100% of 100%');
    expect(inputValue(fixture, 'input[aria-label="Percentage for Ann"]')).toContain('50');

    submit(fixture);
    const body = expectPost().request.body;
    expect(body.splitType).toBe('PERCENTAGE');
    expect(body.participants).toEqual([
      { userId: 'u1', basisPoints: 5000 },
      { userId: 'u2', basisPoints: 5000 },
    ]);
  });

  it('blocks percentages that do not add up to 100', async () => {
    const fixture = await render();
    await fillBasics(fixture);
    clickText(fixture, 'p-selectbutton', 'Percentage');
    await settle(fixture);

    setNumber(fixture, 'input[aria-label="Percentage for Ann"]', '70');
    await settle(fixture);
    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Total: 120% of 100%');
    expect(text(fixture)).toContain('Percentages must add up to 100%.');
  });

  it('prefills exact amounts from the total and sends minor units', async () => {
    const fixture = await render();
    await fillBasics(fixture, '100.01');

    clickText(fixture, 'p-selectbutton', 'Exact');
    await settle(fixture);

    expect(text(fixture)).toContain('Remaining:');
    expect(inputValue(fixture, 'input[aria-label="Amount for Ann"]')).toContain('50.01');
    expect(inputValue(fixture, 'input[aria-label="Amount for Bob"]')).toContain('50.00');

    submit(fixture);
    expect(expectPost().request.body.participants).toEqual([
      { userId: 'u1', amount: 5001 },
      { userId: 'u2', amount: 5000 },
    ]);
  });

  it('blocks exact amounts that do not match the total and lets the user split evenly again', async () => {
    const fixture = await render();
    await fillBasics(fixture, '100');
    clickText(fixture, 'p-selectbutton', 'Exact');
    await settle(fixture);

    setNumber(fixture, 'input[aria-label="Amount for Ann"]', '10');
    await settle(fixture);
    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Amounts must add up to the total.');

    click(fixture, 'p-button[label="Split evenly"] button');
    await settle(fixture);

    expect(inputValue(fixture, 'input[aria-label="Amount for Ann"]')).toContain('50.00');
  });

  it('rounds what was typed to the precision of a currency with fewer decimals', async () => {
    const fixture = await render();
    await fillBasics(fixture, '10.40');

    fixture.componentInstance['form'].controls.currency.setValue('JPY');
    await settle(fixture);

    expect(fixture.componentInstance['form'].controls.amount.value).toBe(10);
    expect(inputValue(fixture, '#amount')).toContain('10');
    expect(inputValue(fixture, '#amount')).not.toContain('.4');

    submit(fixture);
    expect(expectPost().request.body).toMatchObject({ amount: 10, currency: 'JPY' });
  });

  it('rounds exact shares too, so what is shown is what is sent', async () => {
    const fixture = await render();
    await fillBasics(fixture, '100.01');
    clickText(fixture, 'p-selectbutton', 'Exact');
    await settle(fixture);

    fixture.componentInstance['form'].controls.currency.setValue('JPY');
    await settle(fixture);

    const shares = fixture.componentInstance['form'].controls.participants.controls.map(
      (row) => row.controls.amount.value,
    );
    expect(shares).toEqual([50, 50]);
  });

  it('keeps what was typed when switching to a currency with more decimals', async () => {
    const fixture = await render();
    await fillBasics(fixture, '10');

    fixture.componentInstance['form'].controls.currency.setValue('JPY');
    fixture.componentInstance['form'].controls.currency.setValue('PLN');
    await settle(fixture);

    expect(fixture.componentInstance['form'].controls.amount.value).toBe(10);
  });

  it('shows the server message and stays on the form when saving fails', async () => {
    const fixture = await render();
    await fillBasics(fixture);

    submit(fixture);
    expectPost().flush(
      {
        detail: 'Validation failed',
        errors: [{ field: 'description', message: 'must not be blank' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle(fixture);

    expect(text(fixture)).toContain('description: must not be blank');
    expect(navigate).not.toHaveBeenCalled();
    expect(query<HTMLButtonElement>(fixture, 'button[type=submit]')?.disabled).toBe(false);
  });

  it('does not send a second request while saving', async () => {
    const fixture = await render();
    await fillBasics(fixture);

    submit(fixture);
    submit(fixture);

    expectPost().flush({}, { status: 201, statusText: 'Created' });
  });

  it('links back to the expenses of the group', async () => {
    const fixture = await render();

    expect(
      query<HTMLAnchorElement>(fixture, 'a[href="/groups/g1/expenses"]')?.textContent,
    ).toContain('Cancel');
  });
});
