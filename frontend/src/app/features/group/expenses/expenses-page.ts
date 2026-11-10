import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { MessageModule } from 'primeng/message';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../../core/auth/auth.service';
import { GroupsApi } from '../../../core/groups/groups.api';
import { Expense, SplitType } from '../../../core/models';
import { MoneyPipe } from '../../../core/money';
import { createPager } from '../../../core/pager';
import { problemMessage } from '../../../core/problem';
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
    ConfirmDialogModule,
    MessageModule,
    PaginatorModule,
    SkeletonModule,
    TagModule,
    MoneyPipe,
  ],
  templateUrl: './expenses-page.html',
  styleUrl: '../group-section.scss',
  providers: [ConfirmationService],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpensesPage {
  private readonly api = inject(GroupsApi);
  private readonly auth = inject(AuthService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(GroupStore);

  protected readonly deleteError = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);

  protected readonly pager = createPager((page, size) =>
    this.api.expenses(this.store.groupId(), page, size),
  );

  constructor() {
    this.pager.load(0);
  }

  protected canModify(expense: Expense): boolean {
    const me = this.auth.user();
    if (!me) {
      return false;
    }
    if (expense.paidBy === me.id) {
      return true;
    }
    return this.store.members().find((member) => member.userId === me.id)?.role === 'OWNER';
  }

  protected confirmDelete(expense: Expense): void {
    this.confirmation.confirm({
      header: 'Delete expense',
      message: `Delete "${expense.description}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.delete(expense),
    });
  }

  private delete(expense: Expense): void {
    this.deleteError.set(null);
    this.deletingId.set(expense.id);
    const page = Math.floor(this.pager.first() / this.pager.size);
    const lastOnPage = this.pager.items().length === 1 && page > 0;
    this.api
      .deleteExpense(this.store.groupId(), expense.id, expense.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deletingId.set(null);
          this.pager.load(lastOnPage ? page - 1 : page);
        },
        error: (err: unknown) => {
          this.deletingId.set(null);
          this.deleteError.set(problemMessage(err));
          if (err instanceof HttpErrorResponse && (err.status === 412 || err.status === 404)) {
            this.pager.load(page);
          }
        },
      });
  }

  protected splitLabel(type: SplitType): string {
    return SPLIT_LABELS[type];
  }

  protected onPageChange(event: PaginatorState): void {
    this.pager.load(event.page ?? 0);
  }
}
