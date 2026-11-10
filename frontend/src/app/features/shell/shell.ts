import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ToolbarModule } from 'primeng/toolbar';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterOutlet, ButtonModule, ToolbarModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly auth = inject(AuthService);

  constructor() {
    if (!this.auth.user()) {
      this.auth.loadUser().subscribe({ error: () => undefined });
    }
  }

  protected logout(): void {
    this.auth.logout();
  }
}
