import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { BackgroundPaths, buildPaths, buildWords } from './background-paths';

describe('buildPaths', () => {
  it('creates 18 paths per layer by default', () => {
    expect(buildPaths(1)).toHaveLength(18);
    expect(buildPaths(-1)).toHaveLength(18);
  });

  it('reproduces the original curve for the first path', () => {
    expect(buildPaths(1)[0].d).toBe(
      'M-380 -189C-380 -189 -312 216 152 343C616 470 684 875 684 875',
    );
  });

  it('mirrors the second layer horizontally', () => {
    const [, second] = [buildPaths(1)[1], buildPaths(-1)[1]];

    expect(second.d).toBe('M-390 -201C-390 -201 -322 204 162 331C626 458 694 863 694 863');
  });

  it('makes later paths thicker and more opaque', () => {
    const paths = buildPaths(1);

    expect(paths[0].width).toBeCloseTo(0.5);
    expect(paths[17].width).toBeCloseTo(1.52);
    expect(paths[0].opacity).toBeCloseTo(0.1);
    expect(paths[17].opacity).toBeCloseTo(1.12);
  });

  it('is deterministic', () => {
    expect(buildPaths(1)).toEqual(buildPaths(1));
  });
});

describe('buildWords', () => {
  it('splits the title into words and letters with growing delays', () => {
    const words = buildWords('Hello big world');

    expect(words.map((w) => w.letters.map((l) => l.char).join(''))).toEqual([
      'Hello',
      'big',
      'world',
    ]);
    expect(words[0].letters[0].delay).toBe(0);
    expect(words[0].letters[2].delay).toBeCloseTo(0.06);
    expect(words[1].letters[0].delay).toBeCloseTo(0.1);
  });

  it('ignores repeated spaces and an empty title', () => {
    expect(buildWords('a  b').map((w) => w.letters[0].char)).toEqual(['a', 'b']);
    expect(buildWords('')).toEqual([]);
  });
});

@Component({
  imports: [BackgroundPaths],
  template: '<app-background-paths [title]="title"><p class="extra">Hi</p></app-background-paths>',
})
class Host {
  title = 'Settle';
}

describe('BackgroundPaths', () => {
  function render(title = 'Settle') {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.title = title;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('draws two layers of 18 animated paths and hides them from assistive technology', () => {
    const root = render();

    expect(root.querySelectorAll('svg')).toHaveLength(2);
    expect(root.querySelectorAll('path')).toHaveLength(36);
    expect(root.querySelector('.hero__paths')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('exposes the title once to screen readers while animating its letters', () => {
    const root = render('Settle');

    const heading = root.querySelector('h1');
    expect(heading?.getAttribute('aria-label')).toBe('Settle');
    expect(root.querySelectorAll('.hero__letter')).toHaveLength(6);
    expect(root.querySelector('.hero__word')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('renders projected content', () => {
    expect(render().querySelector('.extra')?.textContent).toBe('Hi');
  });

  it('renders no heading without a title', () => {
    expect(render('').querySelector('h1')).toBeNull();
  });
});
