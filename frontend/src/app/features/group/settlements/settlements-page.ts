import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';

import { GroupsApi } from '../../../core/groups/groups.api';
import { createLoader } from '../../../core/loader';
import { MoneyPipe } from '../../../core/money';
import { createPager } from '../../../core/pager';
import { GroupStore } from '../group-store';

@Component({
  selector: 'app-settlements-page',
  imports: [
    DatePipe,
    ButtonModule,
    CardModule,
    MessageModule,
    PaginatorModule,
    SkeletonModule,
    MoneyPipe,
  ],
  templateUrl: './settlements-page.html',
  styleUrl: '../group-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettlementsPage {
  private readonly api = inject(GroupsApi);
  protected readonly store = inject(GroupStore);

  protected readonly suggestions = createLoader(() => this.api.suggestions(this.store.groupId()));
  protected readonly history = createPager((page, size) =>
    this.api.settlements(this.store.groupId(), page, size),
  );

  constructor() {
    this.suggestions.load();
    this.history.load(0);
  }

  protected onPageChange(event: PaginatorState): void {
    this.history.load(event.page ?? 0);
  }
}
