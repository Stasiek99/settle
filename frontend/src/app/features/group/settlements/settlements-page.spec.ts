import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { Page, Settlement, SettlementSuggestions, SuggestedTransfer } from '../../../core/models';
import { click, query, settle, text } from '../../../testing/dom';
import { GroupStore } from '../group-store';
import { SettlementsPage } from './settlements-page';

const NAMES: Record<string, string> = { u1: 'Ann', u2: 'Bob' };

function settlement(id: string, overrides: Partial<Settlement> = {}): Settlement {
  return {
    id,
    groupId: 'g1',
    paidBy: 'u2',
    paidTo: 'u1',
    amount: 4000,
    currency: 'USD',
    settledOn: '2026-11-09',
    createdAt: '2026-11-09T10:00:00Z',
    ...overrides,
  };
}

function page(content: Settlement[], overrides: Partial<Page<Settlement>> = {}): Page<Settlement> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...overrides };
}

function usdTransfers(transfers: SuggestedTransfer[]): SettlementSuggestions {
  return { defaultCurrency: 'USD', currencies: [{ currency: 'USD', transfers }] };
}

const NO_TRANSFERS = usdTransfers([]);

describe('SettlementsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
        provideRouter([]),
        {
          provide: GroupStore,
          useValue: { groupId: () => 'g1', nameOf: (id: string) => NAMES[id] ?? 'Unknown member' },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectHistory(pageNumber = 0): TestRequest {
    return http.expectOne(
      (r) => r.url === '/api/groups/g1/settlements' && r.params.get('page') === String(pageNumber),
    );
  }

  function expectSuggestions(): TestRequest {
    return http.expectOne('/api/groups/g1/settlements/suggestions');
  }

  async function render(
    options: { suggestions?: SettlementSuggestions; history?: Page<Settlement> } = {},
  ) {
    const fixture = TestBed.createComponent(SettlementsPage);
    fixture.detectChanges();
    expectSuggestions().flush(options.suggestions ?? NO_TRANSFERS);
    expectHistory().flush(options.history ?? page([]));
    await settle(fixture);
    return fixture;
  }

  it('shows placeholders while both sections load', () => {
    const fixture = TestBed.createComponent(SettlementsPage);
    fixture.detectChanges();

    expect(query(fixture, 'p-skeleton')).not.toBeNull();
    expectSuggestions().flush(NO_TRANSFERS);
    expectHistory().flush(page([]));
  });

  it('proposes who should pay whom', async () => {
    const fixture = await render({
      suggestions: usdTransfers([{ paidBy: 'u2', paidTo: 'u1', amount: 2500 }]),
    });

    const content = text(fixture);
    expect(content).toContain('Suggested transfers');
    expect(content).toContain('Bob');
    expect(content).toContain('pays');
    expect(content).toContain('Ann');
    expect(content).toContain('$25.00');
  });

  it('offers to record a payment', async () => {
    const fixture = await render();

    expect(query<HTMLAnchorElement>(fixture, 'a[href="/new"]')?.textContent).toContain(
      'Record payment',
    );
  });

  it('lets a suggested transfer be recorded with one click', async () => {
    const fixture = await render({
      suggestions: usdTransfers([{ paidBy: 'u2', paidTo: 'u1', amount: 2500 }]),
    });

    const link = query<HTMLAnchorElement>(fixture, 'section a[href^="/new?"]');
    expect(link?.textContent).toContain('Record');
    expect(link?.getAttribute('href')).toBe('/new?from=u2&to=u1&amount=2500&currency=USD');
  });

  it('lists transfers per currency and records each in its own currency', async () => {
    const fixture = await render({
      suggestions: {
        defaultCurrency: 'USD',
        currencies: [
          { currency: 'USD', transfers: [{ paidBy: 'u2', paidTo: 'u1', amount: 2500 }] },
          { currency: 'EUR', transfers: [{ paidBy: 'u1', paidTo: 'u2', amount: 1000 }] },
        ],
      },
    });

    expect(text(fixture)).toContain('€10.00');
    const links = fixture.nativeElement.querySelectorAll('section a[href^="/new?"]');
    expect(links[1].getAttribute('href')).toBe('/new?from=u1&to=u2&amount=1000&currency=EUR');
  });

  it('says so when nobody owes anything', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('Everyone is settled up.');
  });

  it('lists the recorded payments, newest first as served', async () => {
    const fixture = await render({
      history: page([
        settlement('s1'),
        settlement('s2', { paidBy: 'u1', paidTo: 'u2', amount: 1000 }),
      ]),
    });

    const content = text(fixture);
    expect(content).toContain('History');
    expect(content).toContain('Bob paid Ann');
    expect(content).toContain('$40.00');
    expect(content).toContain('Ann paid Bob');
    expect(content).toContain('$10.00');
  });

  it('shows an empty history', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('No payments recorded yet.');
  });

  it('keeps the history usable when the suggestions fail, and the other way round', async () => {
    const fixture = TestBed.createComponent(SettlementsPage);
    fixture.detectChanges();
    expectSuggestions().flush(null, { status: 500, statusText: 'Server Error' });
    expectHistory().flush(page([settlement('s1')]));
    await settle(fixture);

    expect(text(fixture)).toContain('server had a problem');
    expect(text(fixture)).toContain('Bob paid Ann');
  });

  it('retries only the suggestions section', async () => {
    const fixture = TestBed.createComponent(SettlementsPage);
    fixture.detectChanges();
    expectSuggestions().flush(null, { status: 500, statusText: 'Server Error' });
    expectHistory().flush(page([]));
    await settle(fixture);

    click(fixture, 'section[aria-labelledby="suggested-heading"] p-button button');
    expectSuggestions().flush(NO_TRANSFERS);
    await settle(fixture);

    expect(text(fixture)).toContain('Everyone is settled up.');
  });

  it('pages through the history', async () => {
    const fixture = await render({
      history: page([settlement('s1')], { totalElements: 25, totalPages: 3 }),
    });

    query<HTMLButtonElement>(fixture, 'p-paginator .p-paginator-next')?.click();
    expectHistory(1).flush(
      page([settlement('s2', { paidBy: 'u1', paidTo: 'u2' })], {
        page: 1,
        totalElements: 25,
        totalPages: 3,
      }),
    );
    await settle(fixture);

    expect(text(fixture)).toContain('Ann paid Bob');
  });
});
