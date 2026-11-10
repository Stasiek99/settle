import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface PathSpec {
  id: number;
  d: string;
  width: number;
  opacity: number;
}

export interface Layer {
  position: 1 | -1;
  paths: PathSpec[];
}

export interface Word {
  letters: { char: string; delay: number }[];
}

const PATHS_PER_LAYER = 18;
const STEP = 2;

export function buildPaths(position: 1 | -1, count = PATHS_PER_LAYER): PathSpec[] {
  return Array.from({ length: count }, (_, i) => {
    const k = i * STEP;
    const shift = k * 5 * position;
    const drop = k * 6;
    const d =
      `M-${380 - shift} -${189 + drop}` +
      `C-${380 - shift} -${189 + drop} -${312 - shift} ${216 - drop} ${152 - shift} ${343 - drop}` +
      `C${616 - shift} ${470 - drop} ${684 - shift} ${875 - drop} ${684 - shift} ${875 - drop}`;
    return {
      id: i,
      d,
      width: 0.5 + k * 0.03,
      opacity: 0.1 + k * 0.03,
    };
  });
}

export function buildWords(title: string): Word[] {
  return title
    .split(' ')
    .filter((word) => word.length > 0)
    .map((word, wordIndex) => ({
      letters: word.split('').map((char, letterIndex) => ({
        char,
        delay: wordIndex * 0.1 + letterIndex * 0.03,
      })),
    }));
}

@Component({
  selector: 'app-background-paths',
  templateUrl: './background-paths.html',
  styleUrl: './background-paths.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BackgroundPaths {
  readonly title = input('');

  protected readonly layers: Layer[] = [
    { position: 1, paths: buildPaths(1) },
    { position: -1, paths: buildPaths(-1) },
  ];

  protected readonly words = computed(() => buildWords(this.title()));
}
