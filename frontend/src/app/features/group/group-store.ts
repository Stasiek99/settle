import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';

import { GroupsApi } from '../../core/groups/groups.api';
import { Group, Member } from '../../core/models';
import { problemMessage } from '../../core/problem';

export type GroupStatus = 'idle' | 'loading' | 'ready' | 'error';

const UNKNOWN_MEMBER = 'Unknown member';

@Injectable()
export class GroupStore {
  private readonly api = inject(GroupsApi);
  private inFlight: Subscription | undefined;
  private currentId: string | null = null;

  readonly group = signal<Group | null>(null);
  readonly members = signal<Member[]>([]);
  readonly status = signal<GroupStatus>('idle');
  readonly error = signal<string | null>(null);

  private readonly names = computed(
    () => new Map(this.members().map((member) => [member.userId, member.displayName])),
  );

  groupId(): string {
    if (this.currentId === null) {
      throw new Error('No group has been loaded yet');
    }
    return this.currentId;
  }

  nameOf(userId: string): string {
    return this.names().get(userId) ?? UNKNOWN_MEMBER;
  }

  load(groupId: string): void {
    this.inFlight?.unsubscribe();
    this.currentId = groupId;
    this.group.set(null);
    this.members.set([]);
    this.error.set(null);
    this.status.set('loading');
    this.inFlight = forkJoin([this.api.get(groupId), this.api.members(groupId)]).subscribe({
      next: ([group, members]) => {
        this.group.set(group);
        this.members.set(members);
        this.status.set('ready');
      },
      error: (err: unknown) => {
        this.error.set(problemMessage(err));
        this.status.set('error');
      },
    });
  }

  reload(): void {
    this.load(this.groupId());
  }
}
