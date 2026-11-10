import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';

import { BackgroundPaths } from '../../shared/background-paths/background-paths';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, ButtonDirective, BackgroundPaths],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Landing {}
