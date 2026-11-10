import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { GroupsApi } from './groups.api';

describe('GroupsApi', () => {
  let api: GroupsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GroupsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gets a group and its members', () => {
    api.get('g1').subscribe();
    api.members('g1').subscribe();

    expect(http.expectOne('/api/groups/g1').request.method).toBe('GET');
    expect(http.expectOne('/api/groups/g1/members').request.method).toBe('GET');
  });

  it('pages through expenses', () => {
    api.expenses('g1', 2, 10).subscribe();

    const request = http.expectOne((r) => r.url === '/api/groups/g1/expenses');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('pages through settlements', () => {
    api.settlements('g1', 0, 5).subscribe();

    const request = http.expectOne((r) => r.url === '/api/groups/g1/settlements');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('5');
  });

  it('reads balances and suggested transfers', () => {
    api.balances('g1').subscribe();
    api.suggestions('g1').subscribe();

    expect(http.expectOne('/api/groups/g1/balances').request.method).toBe('GET');
    expect(http.expectOne('/api/groups/g1/settlements/suggestions').request.method).toBe('GET');
  });
});
