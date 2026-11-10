import { ComponentFixture } from '@angular/core/testing';

export function query<T extends HTMLElement>(fixture: ComponentFixture<unknown>, selector: string): T | null {
  return (fixture.nativeElement as HTMLElement).querySelector<T>(selector);
}

export function setValue(fixture: ComponentFixture<unknown>, selector: string, value: string): void {
  const input = query<HTMLInputElement>(fixture, selector);
  if (!input) {
    throw new Error(`No element matches ${selector}`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

export function click(fixture: ComponentFixture<unknown>, selector: string): void {
  const element = query<HTMLElement>(fixture, selector);
  if (!element) {
    throw new Error(`No element matches ${selector}`);
  }
  element.click();
}

export function submit(fixture: ComponentFixture<unknown>, selector = 'form'): void {
  const form = query<HTMLFormElement>(fixture, selector);
  if (!form) {
    throw new Error(`No element matches ${selector}`);
  }
  form.dispatchEvent(new Event('submit'));
}

export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  fixture.detectChanges();
}

export function text(fixture: ComponentFixture<unknown>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}
