import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BankingApiService } from '../../core/banking-api.service';
import { toApiError } from '../../core/api-error';
import { MoneyPipe } from '../../core/money.pipe';
import { AlertFilter, FraudAlert, Page, RULE_NAMES, ReviewStatus, Severity } from '../../core/models';

@Component({
  selector: 'app-alerts',
  imports: [FormsModule, RouterLink, DatePipe, TitleCasePipe, MoneyPipe],
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 1rem; align-items: end; margin-bottom: 1rem; }
    .filters .field { margin: 0; min-width: 11rem; }
    .why { max-width: 40ch; }
    .review-box { display: grid; gap: 0.5rem; margin-top: 0.6rem; max-width: 26rem; }
    .review-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
  `,
  template: `
    <header class="page-head">
      <div>
        <h1>{{ auth.isAnalyst() ? 'Review alerts' : 'Alerts on your transfers' }}</h1>
        <p>
          @if (auth.isAnalyst()) {
            Each alert names the rule that fired and why. Confirm it if the activity looks suspicious, or dismiss it.
          } @else {
            Flagged transfers still complete. An analyst reviews each alert.
          }
        </p>
      </div>
    </header>

    <form class="filters" (ngSubmit)="load(0)">
      <div class="field">
        <label for="status">Review status</label>
        <select id="status" name="status" [(ngModel)]="filter.status" (ngModelChange)="load(0)">
          <option value="">Any</option>
          <option value="OPEN">Open</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="DISMISSED">Dismissed</option>
        </select>
      </div>
      <div class="field">
        <label for="severity">Severity</label>
        <select id="severity" name="severity" [(ngModel)]="filter.severity" (ngModelChange)="load(0)">
          <option value="">Any</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
      </div>
    </form>

    @if (error()) {
      <p class="notice error" role="alert" style="margin-bottom: 1rem">{{ error() }}</p>
    }

    <div class="table-wrap">
      <table class="ledger">
        <thead>
          <tr><th>Raised</th><th>Rule</th><th>Transfer</th><th class="amount">Amount</th><th>Status</th></tr>
        </thead>
        <tbody>
          @for (alert of page()?.content ?? []; track alert.id) {
            <tr>
              <td class="when">{{ alert.createdAt | date: 'd MMM, HH:mm' }}</td>
              <td class="why">
                <b>{{ ruleName(alert.ruleCode) }}</b>
                <span class="mark" [class.bad]="alert.severity === 'HIGH'" [class.warn]="alert.severity !== 'HIGH'" style="margin-left: 0.3rem">
                  {{ alert.severity | titlecase }}
                </span>
                <span class="sub">{{ alert.explanation }}</span>
              </td>
              <td>
                <a [routerLink]="['/transfers', alert.transferId]"><span class="num">{{ alert.sourceAccountReference }}</span> to <span class="num">{{ alert.destinationAccountReference }}</span></a>
              </td>
              <td class="amount">{{ alert.amount | money: alert.currency }}</td>
              <td>
                @if (alert.reviewStatus === 'OPEN') {
                  <span class="mark warn">Open</span>
                  @if (auth.isAnalyst()) {
                    @if (reviewing() === alert.id) {
                      <div class="review-box">
                        <label class="sr-only" [for]="'note-' + alert.id">Review note</label>
                        <textarea [id]="'note-' + alert.id" rows="2" maxlength="500" placeholder="Note (optional)"
                                  [ngModel]="note()" (ngModelChange)="note.set($event)" [ngModelOptions]="{ standalone: true }"></textarea>
                        <div class="review-actions">
                          <button class="button small" type="button" [disabled]="busy()" (click)="review(alert, 'CONFIRMED')">Confirm suspicious</button>
                          <button class="button small secondary" type="button" [disabled]="busy()" (click)="review(alert, 'DISMISSED')">Dismiss</button>
                          <button class="link-button" type="button" (click)="reviewing.set(null)">Cancel</button>
                        </div>
                      </div>
                    } @else {
                      <div style="margin-top: 0.4rem">
                        <button class="button small secondary" type="button" (click)="startReview(alert)">Review</button>
                      </div>
                    }
                  }
                } @else {
                  <span class="mark" [class.bad]="alert.reviewStatus === 'CONFIRMED'" [class.quiet]="alert.reviewStatus === 'DISMISSED'">
                    {{ alert.reviewStatus | titlecase }}
                  </span>
                  @if (alert.reviewNote) { <span class="sub">{{ alert.reviewNote }}</span> }
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="empty">
              @if (loading()) { Loading alerts… } @else { No alerts match these filters. }
            </td></tr>
          }
        </tbody>
      </table>
    </div>

    @if ((page()?.totalPages ?? 0) > 1) {
      <nav class="pager" aria-label="Alert pages">
        <button class="button secondary small" type="button" [disabled]="pageIndex() === 0" (click)="load(pageIndex() - 1)">Newer</button>
        <span class="muted">Page {{ pageIndex() + 1 }} of {{ page()?.totalPages }}</span>
        <button class="button secondary small" type="button" [disabled]="pageIndex() + 1 >= (page()?.totalPages ?? 0)" (click)="load(pageIndex() + 1)">Older</button>
      </nav>
    }
  `,
})
export class AlertsComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(BankingApiService);

  protected filter: { status: ReviewStatus | ''; severity: Severity | '' } = { status: '', severity: '' };
  protected readonly page = signal<Page<FraudAlert> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly reviewing = signal<string | null>(null);
  protected readonly note = signal('');
  protected readonly busy = signal(false);

  ngOnInit(): void {
    if (this.auth.isAnalyst()) {
      this.filter.status = 'OPEN';
    }
    this.load(0);
  }

  protected load(index: number): void {
    this.loading.set(true);
    const filter: AlertFilter = { ...this.filter };
    this.api.alerts(filter, index, 20).subscribe({
      next: (page) => {
        this.page.set(page);
        this.pageIndex.set(index);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toApiError(err).message);
      },
    });
  }

  protected startReview(alert: FraudAlert): void {
    this.note.set('');
    this.reviewing.set(alert.id);
  }

  protected review(alert: FraudAlert, decision: 'CONFIRMED' | 'DISMISSED'): void {
    this.busy.set(true);
    const note = this.note().trim() || null;
    this.api.reviewAlert(alert.id, decision, note).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.reviewing.set(null);
        this.page.update((page) => page && {
          ...page,
          content: page.content.map((a) => (a.id === updated.id ? updated : a)),
        });
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(toApiError(err).message);
      },
    });
  }

  protected ruleName(code: string): string {
    return RULE_NAMES[code] ?? code;
  }
}
