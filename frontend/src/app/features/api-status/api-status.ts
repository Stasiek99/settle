import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';

import { API_BASE_URL } from '../../core/api.config';

type ApiState = 'checking' | 'up' | 'down';

interface HealthResponse {
  status: string;
}

@Component({
  selector: 'app-api-status',
  imports: [ButtonModule, CardModule, TagModule],
  templateUrl: './api-status.html',
  styleUrl: './api-status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApiStatus {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<ApiState>('checking');

  constructor() {
    this.check();
  }

  protected check(): void {
    this.state.set('checking');
    this.http
      .get<HealthResponse>(`${this.baseUrl}/actuator/health`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (health) => this.state.set(health.status === 'UP' ? 'up' : 'down'),
        error: () => this.state.set('down'),
      });
  }
}
