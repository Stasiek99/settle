interface StoredToken {
  token: string;
  expiresAt: number;
}

const KEY = 'settle.session';

export class TokenStore {
  private memory: StoredToken | null = null;

  read(now = Date.now()): string | null {
    const stored = this.memory ?? this.readStorage();
    if (!stored || stored.expiresAt <= now) {
      this.clear();
      return null;
    }
    return stored.token;
  }

  write(token: string, expiresInSeconds: number, now = Date.now()): void {
    const stored: StoredToken = { token, expiresAt: now + expiresInSeconds * 1000 };
    this.memory = stored;
    try {
      sessionStorage.setItem(KEY, JSON.stringify(stored));
    } catch {
      // Storage can be unavailable (private mode, blocked site data); the in-memory copy still works.
    }
  }

  clear(): void {
    this.memory = null;
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      // Nothing to clean up when storage is unavailable.
    }
  }

  private readStorage(): StoredToken | null {
    try {
      const raw = sessionStorage.getItem(KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as Partial<StoredToken>;
      return typeof parsed.token === 'string' && typeof parsed.expiresAt === 'number'
        ? { token: parsed.token, expiresAt: parsed.expiresAt }
        : null;
    } catch {
      return null;
    }
  }
}
