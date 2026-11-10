import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Group, Member } from '../../core/models';
import { GroupStore } from './group-store';

const GROUP: Group = {
  id: 'g1',
  name: 'Trip',
  currency: 'PLN',
  createdBy: 'u1',
  createdAt: '2026-11-10T10:00:00Z',
  version: 0,
};

const MEMBERS: Member[] = [
  { id: 'm1', userId: 'u1', displayName: 'Ann', role: 'OWNER', joinedAt: '' },
  { id: 'm2', userId: 'u2', displayName: 'Bob', role: 'MEMBER', joinedAt: '' },
];

describe('GroupStore', () => {
  let store: GroupStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), GroupStore],
    });
    store = TestBed.inject(GroupStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function answer(group = GROUP, members = MEMBERS): void {
    http.expectOne('/api/groups/g1').flush(group);
    http.expectOne('/api/groups/g1/members').flush(members);
  }

  it('starts idle and refuses to hand out an id before anything was loaded', () => {
    expect(store.status()).toBe('idle');
    expect(() => store.groupId()).toThrow();
  });

  it('is loading while the group and its members are fetched', () => {
    store.load('g1');

    expect(store.status()).toBe('loading');
    expect(store.groupId()).toBe('g1');
    answer();
  });

  it('becomes ready with the group and the members', () => {
    store.load('g1');
    answer();

    expect(store.status()).toBe('ready');
    expect(store.group()).toEqual(GROUP);
    expect(store.members()).toEqual(MEMBERS);
  });

  it('turns user ids into names and does not invent one for a stranger', () => {
    store.load('g1');
    answer();

    expect(store.nameOf('u2')).toBe('Bob');
    expect(store.nameOf('someone-else')).toBe('Unknown member');
  });

  it('reports why loading failed', () => {
    store.load('g1');
    http
      .expectOne('/api/groups/g1')
      .flush({ detail: 'Group g1 not found' }, { status: 404, statusText: 'Not Found' });
    http.match((r) => r.url === '/api/groups/g1/members');

    expect(store.status()).toBe('error');
    expect(store.error()).toBe('Group g1 not found');
    expect(store.group()).toBeNull();
  });

  it('forgets the previous group while another one loads', () => {
    store.load('g1');
    answer();

    store.load('g1');

    expect(store.status()).toBe('loading');
    expect(store.group()).toBeNull();
    expect(store.members()).toEqual([]);
    answer();
  });

  it('reload fetches the same group again', () => {
    store.load('g1');
    answer();

    store.reload();

    answer();
    expect(store.status()).toBe('ready');
  });
});
