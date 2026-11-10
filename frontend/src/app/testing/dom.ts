import { ComponentFixture } from '@angular/core/testing';

export function query<T extends HTMLElement>(
  fixture: ComponentFixture<unknown>,
  selector: string,
): T | null {
  return (fixture.nativeElement as HTMLElement).querySelector<T>(selector);
}

export function setValue(
  fixture: ComponentFixture<unknown>,
  selector: string,
  value: string,
): void {
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

export function clickText(
  fixture: ComponentFixture<unknown>,
  containerSelector: string,
  label: string,
): void {
  const container = query<HTMLElement>(fixture, containerSelector);
  const target = Array.from(container?.querySelectorAll<HTMLElement>('*') ?? []).find(
    (element) => element.children.length === 0 && element.textContent?.trim() === label,
  );
  if (!target) {
    throw new Error(`No "${label}" inside ${containerSelector}`);
  }
  target.click();
}

export function setNumber(
  fixture: ComponentFixture<unknown>,
  selector: string,
  value: string,
): void {
  const input = query<HTMLInputElement>(fixture, selector);
  if (!input) {
    throw new Error(`No element matches ${selector}`);
  }
  input.focus();
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

export function inputValue(fixture: ComponentFixture<unknown>, selector: string): string {
  return query<HTMLInputElement>(fixture, selector)?.value ?? '';
}
