import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { Landing } from './landing';

describe('Landing', () => {
  function render(): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([]), providePrimeNG()] });
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the product name as the page heading', () => {
    expect(render().querySelector('h1')?.getAttribute('aria-label')).toBe('Settle');
  });

  it('describes the product', () => {
    expect(render().textContent).toContain('Split expenses with friends');
  });

  it('offers real links to registration and sign in', () => {
    const root = render();

    const register = root.querySelector<HTMLAnchorElement>('a[href="/register"]');
    const login = root.querySelector<HTMLAnchorElement>('a[href="/login"]');
    expect(register?.textContent).toContain('Get started');
    expect(login?.textContent).toContain('Sign in');
    expect(root.querySelector('a button')).toBeNull();
  });
});
