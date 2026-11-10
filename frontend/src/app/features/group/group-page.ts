import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';

import { GroupStore } from './group-store';

@Component({
  selector: 'app-group-page',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    ButtonModule,
    MessageModule,
    SkeletonModule,
    TagModule,
  ],
  templateUrl: './group-page.html',
  styleUrl: './group-page.scss',
  providers: [GroupStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupPage {
  protected readonly store = inject(GroupStore);

  readonly groupId = input.required<string>();

  constructor() {
    effect(() => {
      const id = this.groupId();
      untracked(() => this.store.load(id));
    });
  }
}
