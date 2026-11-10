import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';

import { GroupsApi } from '../../../core/groups/groups.api';
import { SplitType } from '../../../core/models';
import { MoneyPipe } from '../../../core/money';
import { createPager } from '../../../core/pager';
import { GroupStore } from '../group-store';

const SPLIT_LABELS: Record<SplitType, string> = {
  EQUAL: 'Equal',
  PERCENTAGE: 'Percentage',
  EXACT: 'Exact',
};

@Component({
  selector: 'app-expenses-page',
  imports: [
    DatePipe,
    RouterLink,
    ButtonModule,
    CardModule,
    MessageModule,
    PaginatorModule,
    SkeletonModule,
    TagModule,
    MoneyPipe,
  ],
  templateUrl: './expenses-page.html',
  styleUrl: '../group-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpensesPage {
  private readonly api = inject(GroupsApi);
  protected readonly store = inject(GroupStore);

  protected readonly pager = createPager((page, size) =>
    this.api.expenses(this.store.groupId(), page, size),
  );

  constructor() {
    this.pager.load(0);
  }

  protected splitLabel(type: SplitType): string {
    return SPLIT_LABELS[type];
  }

  protected onPageChange(event: PaginatorState): void {
    this.pager.load(event.page ?? 0);
  }
}
