import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BankingApiService } from '../../core/banking-api.service';
import { toApiError } from '../../core/api-error';
import { MoneyPipe } from '../../core/money.pipe';
import { REJECTION_TEXT, RULE_NAMES, Transfer } from '../../core/models';

@Component({
  selector: 'app-transfer-detail',
  imports: [RouterLink, DatePipe, TitleCasePipe, MoneyPipe],
  template: `
    <p><a routerLink="/">Back to overview</a></p>
    @if (error()) {
      <p class="notice error" role="alert" style="margin-top: 1rem">{{ error() }}</p>
    }
    @if (transfer(); as t) {
      <header class="page-head" style="margin-top: 1rem">
        <div>
          <h1>Transfer of {{ t.amount | money: t.currency }}</h1>
          <p class="num">{{ t.id }}</p>
        </div>
        @if (t.status === 'COMPLETED') { <span class="mark ok">Completed</span> }
        @else if (t.status === 'REJECTED') { <span class="mark bad">Rejected</span> }
      </header>

      <div class="sheet">
        <dl class="facts">
          <dt>From</dt><dd class="num">{{ t.sourceAccountReference }}</dd>
          <dt>To</dt><dd class="num">{{ t.destinationAccountReference }}</dd>
          <dt>Amount</dt><dd class="num">{{ t.amount | money: t.currency }}</dd>
          <dt>Reference</dt><dd>{{ t.reference ?? 'None' }}</dd>
          <dt>Requested</dt><dd class="num">{{ t.createdAt | date: 'd MMM y, HH:mm:ss' }}</dd>
          @if (t.completedAt) { <dt>Completed</dt><dd class="num">{{ t.completedAt | date: 'd MMM y, HH:mm:ss' }}</dd> }
          @if (t.rejectionReason) { <dt>Rejected because</dt><dd class="debit">{{ rejection(t) }}</dd> }
        </dl>
      </div>

      <section class="section">
        <h2>Monitoring</h2>
        @for (alert of t.alerts; track alert.id) {
          <div class="notice review" style="margin-bottom: 0.75rem">
            <strong>{{ ruleName(alert.ruleCode) }}</strong>
            <span class="mark warn">{{ alert.severity | titlecase }} severity</span>
            <span class="mark quiet" style="margin-left: 0.3rem">{{ alert.reviewStatus | titlecase }}</span>
            <p style="margin-top: 0.4rem">{{ alert.explanation }}</p>
          </div>
        } @empty {
          <p class="muted">{{ t.status === 'COMPLETED' ? 'No monitoring rules were triggered.' : 'Rejected transfers are not evaluated by monitoring rules.' }}</p>
        }
      </section>
    } @else if (!error()) {
      <p class="muted" style="margin-top: 1rem">Loading transfer…</p>
    }
  `,
})
export class TransferDetailComponent {
  private readonly api = inject(BankingApiService);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly transfer = signal<Transfer | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      this.transfer.set(null);
      this.error.set(null);
      this.api.transfer(id).subscribe({
        next: (transfer) => this.transfer.set(transfer),
        error: (err: unknown) => this.error.set(toApiError(err).message),
      });
    });
  }

  protected rejection(t: Transfer): string {
    return t.rejectionReason ? REJECTION_TEXT[t.rejectionReason] : '';
  }

  protected ruleName(code: string): string {
    return RULE_NAMES[code] ?? code;
  }
}
