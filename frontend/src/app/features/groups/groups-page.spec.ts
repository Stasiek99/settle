import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { providePrimeNG } from 'primeng/config';

import { Group, Page } from '../../core/models';
import { click, query, setValue, settle, submit, text } from '../../testing/dom';
import { GroupsPage } from './groups-page';

function group(name: string, currency = 'PLN'): Group {
  return {
    id: `id-${name}`,
    name,
    currency,
    createdBy: 'u1',
    createdAt: '2026-11-10T10:00:00Z',
    version: 0,
  };
}

function page(content: Group[], overrides: Partial<Page<Group>> = {}): Page<Group> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  };
}

describe('GroupsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), providePrimeNG()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectList(pageNumber = 0): TestRequest {
    return http.expectOne(
      (r) =>
        r.url === '/api/groups' &&
        r.method === 'GET' &&
        r.params.get('page') === String(pageNumber),
    );
  }

  async function render(first: Page<Group> = page([group('Trip'), group('Flat', 'EUR')])) {
    const fixture = TestBed.createComponent(GroupsPage);
    fixture.detectChanges();
    const request = expectList();
    expect(request.request.params.get('size')).toBe('10');
    request.flush(first);
    await settle(fixture);
    return fixture;
  }

  it('shows placeholders while the first page loads', () => {
    const fixture = TestBed.createComponent(GroupsPage);
    fixture.detectChanges();

    expect(query(fixture, '[aria-busy="true"]')).not.toBeNull();
    expectList().flush(page([]));
  });

  it('lists the groups with their currency', async () => {
    const fixture = await render();

    expect(text(fixture)).toContain('Trip');
    expect(text(fixture)).toContain('Flat');
    expect(text(fixture)).toContain('PLN');
    expect(text(fixture)).toContain('EUR');
  });

  it('shows an empty state for a user without groups', async () => {
    const fixture = await render(page([]));

    expect(text(fixture)).toContain('not in any group yet');
  });

  it('shows the error and retries on request', async () => {
    const fixture = TestBed.createComponent(GroupsPage);
    fixture.detectChanges();
    expectList().flush({ detail: 'nope' }, { status: 500, statusText: 'Server Error' });
    await settle(fixture);

    expect(text(fixture)).toContain('server had a problem');
    expect(text(fixture)).not.toContain('nope');

    click(fixture, 'p-button[label="Try again"] button');
    expectList().flush(page([group('Trip')]));
    await settle(fixture);

    expect(text(fixture)).toContain('Trip');
    expect(text(fixture)).not.toContain('server had a problem');
  });

  it('shows the paginator and loads the requested page', async () => {
    const fixture = await render(page([group('Trip')], { totalElements: 25, totalPages: 3 }));

    expect(query(fixture, 'p-paginator')).not.toBeNull();
    const next = query<HTMLButtonElement>(fixture, 'p-paginator .p-paginator-next');
    next?.click();

    const second = expectList(1);
    second.flush(page([group('Older')], { page: 1, totalElements: 25, totalPages: 3 }));
    await settle(fixture);

    expect(text(fixture)).toContain('Older');
    expect(text(fixture)).not.toContain('Trip');
  });

  it('creates a group from the dialog and reloads the list from the first page', async () => {
    const fixture = await render();

    click(fixture, 'p-button[label="New group"] button');
    await settle(fixture);
    setValue(fixture, '#group-name', '  Ski trip  ');
    submit(fixture, 'p-dialog form');

    const create = http.expectOne((r) => r.url === '/api/groups' && r.method === 'POST');
    expect(create.request.body).toEqual({ name: 'Ski trip', currency: 'PLN' });
    create.flush(group('Ski trip'), { status: 201, statusText: 'Created' });

    expectList().flush(page([group('Ski trip'), group('Trip')]));
    await settle(fixture);

    expect(text(fixture)).toContain('Ski trip');
  });

  it('does not create a group without a name', async () => {
    const fixture = await render();

    click(fixture, 'p-button[label="New group"] button');
    await settle(fixture);
    submit(fixture, 'p-dialog form');
    await settle(fixture);

    http.expectNone((r) => r.method === 'POST');
    expect(text(fixture)).toContain('Name is required.');
  });

  it('keeps the dialog open and shows the server message when creating fails', async () => {
    const fixture = await render();

    click(fixture, 'p-button[label="New group"] button');
    await settle(fixture);
    setValue(fixture, '#group-name', 'Ski trip');
    submit(fixture, 'p-dialog form');
    http
      .expectOne((r) => r.method === 'POST')
      .flush(
        { detail: 'Validation failed', errors: [{ field: 'name', message: 'must not be blank' }] },
        {
          status: 400,
          statusText: 'Bad Request',
        },
      );
    await settle(fixture);

    expect(text(fixture)).toContain('name: must not be blank');
    expect(query(fixture, 'p-dialog form')).not.toBeNull();
  });
});
