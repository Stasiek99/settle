import { TokenStore } from './token-store';

describe('TokenStore', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('returns nothing when no token was stored', () => {
    expect(new TokenStore().read()).toBeNull();
  });

  it('round-trips a token and keeps it across instances through sessionStorage', () => {
    new TokenStore().write('abc', 3600, 1_000);

    expect(new TokenStore().read(2_000)).toBe('abc');
  });

  it('drops a token that has expired and removes it from storage', () => {
    const store = new TokenStore();
    store.write('abc', 10, 0);

    expect(store.read(10_000)).toBeNull();
    expect(sessionStorage.getItem('settle.session')).toBeNull();
  });

  it('clear forgets the token', () => {
    const store = new TokenStore();
    store.write('abc', 3600);
    store.clear();

    expect(store.read()).toBeNull();
    expect(new TokenStore().read()).toBeNull();
  });

  it('ignores corrupted storage content', () => {
    sessionStorage.setItem('settle.session', '{not json');
    expect(new TokenStore().read()).toBeNull();

    sessionStorage.setItem('settle.session', JSON.stringify({ token: 5, expiresAt: 'x' }));
    expect(new TokenStore().read()).toBeNull();
  });

  it('keeps working in memory when storage throws', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const store = new TokenStore();

    store.write('abc', 3600);

    expect(store.read()).toBe('abc');
  });
});
