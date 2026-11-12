import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FormGroup } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../../core/auth/auth.service';
import { Group, Member, User } from '../../../core/models';
import { inputValue, query, setNumber, settle, submit, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { SettlementFormPage } from './settlement-form-page';

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

describe('SettlementFormPage', () => {
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

  async function render(inputs: Record<string, string> = {}) {
    const fixture = TestBed.createComponent(SettlementFormPage);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    await settle(fixture);
    return fixture;
  }

  function expectPost() {
    return http.expectOne((r) => r.url === '/api/groups/g1/settlements' && r.method === 'POST');
  }

  it('starts with the current user as the one who paid', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('Record payment');
    expect(query(fixture, '#paidBy')?.textContent).toContain('Ann');
  });

  it('is filled in from a suggested transfer', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '60349' });

    expect(query(fixture, '#paidBy')?.textContent).toContain('Bob');
    expect(query(fixture, '#paidTo')?.textContent).toContain('Ann');
    expect(inputValue(fixture, '#amount')).toContain('603.49');
    expect(text(fixture)).toMatch(/Bob pays Ann PLN\s603\.49/);
  });

  it('records a suggested transfer in the currency it was suggested in', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '1050', currency: 'EUR' });

    expect(text(fixture)).toMatch(/Bob pays Ann €10\.50/);
    submit(fixture);

    expect(expectPost().request.body).toMatchObject({ amount: 1050, currency: 'EUR' });
  });

  it('falls back to the group currency for an unknown one', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '1050', currency: 'XXX' });

    submit(fixture);

    expect(expectPost().request.body).toMatchObject({ currency: 'PLN' });
  });

  it('ignores a transfer between strangers', async () => {
    const fixture = await render({ from: 'intruder', to: 'someone', amount: '500' });

    expect(query(fixture, '#paidBy')?.textContent).toContain('Ann');
    expect(query(fixture, '#paidTo')?.textContent).not.toContain('someone');
    expect(text(fixture)).not.toContain('intruder');
  });

  it('does not call the API and explains what is missing', async () => {
    const fixture = await render();

    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Choose who received the money.');
    expect(text(fixture)).toContain('Enter an amount greater than zero.');
  });

  it('refuses a payment from a person to themselves', async () => {
    const fixture = await render({ from: 'u1', to: 'u2', amount: '1000' });
    (fixture.componentInstance as unknown as { form: FormGroup }).form.controls['paidTo'].setValue(
      'u1',
    );
    await settle(fixture);

    submit(fixture);
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('nobody can pay themselves');
  });

  it('records the payment in minor units and goes back to the settlements', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '60349' });

    submit(fixture);

    const request = expectPost();
    expect(request.request.body).toMatchObject({
      paidBy: 'u2',
      paidTo: 'u1',
      amount: 60349,
      currency: 'PLN',
    });
    expect(request.request.body.settledOn).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    request.flush({}, { status: 201, statusText: 'Created' });
    expect(navigate).toHaveBeenCalledWith(['/groups', 'g1', 'settlements']);
  });

  it('accepts an amount typed by hand', async () => {
    const fixture = await render({ from: 'u2', to: 'u1' });
    setNumber(fixture, '#amount', '12.30');
    await settle(fixture);

    submit(fixture);

    expect(expectPost().request.body.amount).toBe(1230);
  });

  it('shows the server message and stays on the form when saving fails', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '500' });

    submit(fixture);
    expectPost().flush(
      { detail: 'Payer must be a member of this group' },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle(fixture);

    expect(text(fixture)).toContain('Payer must be a member of this group');
    expect(navigate).not.toHaveBeenCalled();
    expect(query<HTMLButtonElement>(fixture, 'button[type=submit]')?.disabled).toBe(false);
  });

  it('does not send a second request while saving', async () => {
    const fixture = await render({ from: 'u2', to: 'u1', amount: '500' });

    submit(fixture);
    submit(fixture);

    expectPost().flush({}, { status: 201, statusText: 'Created' });
  });

  it('links back to the settlements of the group', async () => {
    const fixture = await render();

    expect(
      query<HTMLAnchorElement>(fixture, 'a[href="/groups/g1/settlements"]')?.textContent,
    ).toContain('Cancel');
  });
});
