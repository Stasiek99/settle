import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';

import { GroupsApi } from '../../../core/groups/groups.api';
import { createLoader } from '../../../core/loader';
import { MemberBalance } from '../../../core/models';
import { MoneyPipe } from '../../../core/money';
import { GroupStore } from '../group-store';

type Severity = 'success' | 'danger' | 'secondary';

@Component({
  selector: 'app-balances-page',
  imports: [ButtonModule, CardModule, MessageModule, SkeletonModule, TagModule, MoneyPipe],
  templateUrl: './balances-page.html',
  styleUrl: '../group-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BalancesPage {
  private readonly api = inject(GroupsApi);
  private readonly store = inject(GroupStore);

  protected readonly balances = createLoader(() => this.api.balances(this.store.groupId()));

  constructor() {
    this.balances.load();
  }

  protected severity(entry: MemberBalance): Severity {
    if (entry.balance > 0) {
      return 'success';
    }
    return entry.balance < 0 ? 'danger' : 'secondary';
  }

  protected absolute(entry: MemberBalance): number {
    return Math.abs(entry.balance);
  }

  protected label(entry: MemberBalance): string {
    if (entry.balance > 0) {
      return 'gets back';
    }
    return entry.balance < 0 ? 'owes' : 'settled up';
  }
}
