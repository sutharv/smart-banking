import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <nav class="rail" aria-label="Main">
        <a class="brand" routerLink="/">Smart Banking<span>Simulator</span></a>
        <ul>
          <li><a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Overview</a></li>
          @if (!auth.isAnalyst()) {
            <li><a routerLink="/transfer" routerLinkActive="active">Send money</a></li>
          }
          <li><a routerLink="/alerts" routerLinkActive="active">{{ auth.isAnalyst() ? 'Review alerts' : 'Alerts' }}</a></li>
        </ul>
        <div class="who">
          <span>{{ auth.session()?.username }}</span>
          <span class="role">{{ auth.isAnalyst() ? 'Analyst' : 'Customer' }}</span>
          <button type="button" class="link-button" (click)="auth.logout()">Log out</button>
        </div>
      </nav>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
}
