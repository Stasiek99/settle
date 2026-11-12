import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { providePrimeNG } from 'primeng/config';

import { GroupBalances, MemberBalance } from '../../../core/models';
import { click, query, settle, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { BalancesPage } from './balances-page';

function usd(balances: MemberBalance[]): GroupBalances {
  return { defaultCurrency: 'USD', currencies: [{ currency: 'USD', balances }] };
}

describe('BalancesPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        { provide: GroupStore, useValue: { groupId: () => 'g1' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(result: GroupBalances) {
    const fixture = TestBed.createComponent(BalancesPage);
    fixture.detectChanges();
    http.expectOne('/api/groups/g1/balances').flush(result);
    await settle(fixture);
    return fixture;
  }

  it('shows placeholders while loading', () => {
    const fixture = TestBed.createComponent(BalancesPage);
    fixture.detectChanges();

    expect(query(fixture, '[aria-busy="true"]')).not.toBeNull();
    http.expectOne('/api/groups/g1/balances').flush(usd([]));
  });

  it('says who gets money back, who owes and who is even, with positive amounts', async () => {
    const fixture = await render(
      usd([
        { userId: 'u1', displayName: 'Ann', balance: 2500 },
        { userId: 'u2', displayName: 'Bob', balance: -2500 },
        { userId: 'u3', displayName: 'Cid', balance: 0 },
      ]),
    );

    const content = text(fixture);
    expect(content).toContain('Ann');
    expect(content).toContain('gets back');
    expect(content).toContain('Bob');
    expect(content).toContain('owes');
    expect(content).toContain('Cid');
    expect(content).toContain('settled up');
    expect(content).toContain('$25.00');
    expect(content).not.toContain('-$25.00');
  });

  it('shows no amount for a member who is settled up', async () => {
    const fixture = await render(usd([{ userId: 'u3', displayName: 'Cid', balance: 0 }]));

    expect(text(fixture)).not.toContain('$0.00');
  });

  it('shows each currency in its own section when the group uses several', async () => {
    const fixture = await render({
      defaultCurrency: 'USD',
      currencies: [
        { currency: 'USD', balances: [{ userId: 'u1', displayName: 'Ann', balance: 2500 }] },
        { currency: 'EUR', balances: [{ userId: 'u1', displayName: 'Ann', balance: -1000 }] },
      ],
    });

    const content = text(fixture);
    expect(content).toContain('USD');
    expect(content).toContain('EUR');
    expect(content).toContain('$25.00');
    expect(content).toContain('€10.00');
  });

  it('shows an empty state', async () => {
    const fixture = await render(usd([]));

    expect(text(fixture)).toContain('No members yet.');
  });

  it('shows the error and retries', async () => {
    const fixture = TestBed.createComponent(BalancesPage);
    fixture.detectChanges();
    http
      .expectOne('/api/groups/g1/balances')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    await settle(fixture);
    expect(text(fixture)).toContain('Cannot reach the server');

    click(fixture, 'p-button[label="Try again"] button');
    http
      .expectOne('/api/groups/g1/balances')
      .flush(usd([{ userId: 'u1', displayName: 'Ann', balance: 100 }]));
    await settle(fixture);

    expect(text(fixture)).toContain('Ann');
  });
});
