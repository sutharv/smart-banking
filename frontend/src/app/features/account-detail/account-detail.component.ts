import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BankingApiService } from '../../core/banking-api.service';
import { toApiError } from '../../core/api-error';
import { MoneyPipe } from '../../core/money.pipe';
import { Account, Page, REJECTION_TEXT, Transaction } from '../../core/models';

const PAGE_SIZE = 15;

@Component({
  selector: 'app-account-detail',
  imports: [RouterLink, DatePipe, MoneyPipe],
  template: `
    <p><a routerLink="/">Back to overview</a></p>

    @if (error()) {
      <p class="notice error" role="alert" style="margin-top: 1rem">{{ error() }}</p>
    }

    @if (account(); as acc) {
      <header class="page-head" style="margin-top: 1rem">
        <div>
          <h1>{{ acc.displayName }}</h1>
          <p class="num">{{ acc.reference }}
            <span class="mark" [class.ok]="acc.status === 'ACTIVE'" [class.bad]="acc.status !== 'ACTIVE'">{{ acc.status }}</span>
          </p>
        </div>
        <div style="text-align: right">
          <div class="big-balance">{{ acc.balance | money: acc.currency }}</div>
          @if (!auth.isAnalyst() && acc.status === 'ACTIVE') {
            <a class="button secondary small" routerLink="/transfer" [queryParams]="{ from: acc.reference }">Send from this account</a>
          }
        </div>
      </header>

      <section class="section">
        <h2>History</h2>
        <div class="table-wrap">
          <table class="ledger">
            <thead>
              <tr><th>When</th><th>Counterparty</th><th class="hide-sm">Reference</th><th>Status</th><th class="amount">Amount</th></tr>
            </thead>
            <tbody>
              @for (t of page()?.content ?? []; track t.transferId) {
                <tr>
                  <td class="when">{{ t.createdAt | date: 'd MMM y, HH:mm:ss' }}</td>
                  <td>
                    <a [routerLink]="['/transfers', t.transferId]">{{ t.direction === 'OUTGOING' ? 'To' : 'From' }} <span class="num">{{ t.counterpartyReference }}</span></a>
                  </td>
                  <td class="hide-sm">{{ t.reference ?? '—' }}</td>
                  <td>
                    @if (t.status === 'COMPLETED') { <span class="mark ok">Completed</span> }
                    @else if (t.status === 'REJECTED') { <span class="mark bad">Rejected</span><span class="sub">{{ rejection(t) }}</span> }
                    @else { <span class="mark quiet">{{ t.status }}</span> }
                    @if (t.flagged) { <span class="mark warn" style="margin-left: 0.3rem">Flagged</span> }
                  </td>
                  <td class="amount" [class.credit]="t.direction === 'INCOMING'"
                      [class.debit]="t.direction === 'OUTGOING' && t.status === 'COMPLETED'"
                      [class.struck]="t.status === 'REJECTED'">
                    {{ (t.direction === 'OUTGOING' ? -t.amount : t.amount) | money: t.currency : true }}
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="empty">No transfers on this account yet.</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if ((page()?.totalPages ?? 0) > 1) {
          <nav class="pager" aria-label="History pages">
            <button class="button secondary small" type="button" [disabled]="pageIndex() === 0" (click)="go(pageIndex() - 1)">Newer</button>
            <span class="muted">Page {{ pageIndex() + 1 }} of {{ page()?.totalPages }}</span>
            <button class="button secondary small" type="button" [disabled]="pageIndex() + 1 >= (page()?.totalPages ?? 0)" (click)="go(pageIndex() + 1)">Older</button>
          </nav>
        }
      </section>
    } @else if (!error()) {
      <p class="muted" style="margin-top: 1rem">Loading account…</p>
    }
  `,
})
export class AccountDetailComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(BankingApiService);

  /** Bound from the :reference route parameter. */
  readonly reference = input.required<string>();

  protected readonly account = signal<Account | null>(null);
  protected readonly page = signal<Page<Transaction> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const reference = this.reference();
      this.account.set(null);
      this.error.set(null);
      this.api.account(reference).subscribe({
        next: (account) => {
          this.account.set(account);
          this.go(0);
        },
        error: (err: unknown) => this.error.set(toApiError(err).message),
      });
    });
  }

  protected go(index: number): void {
    this.api.transactions(this.reference(), index, PAGE_SIZE).subscribe({
      next: (page) => {
        this.page.set(page);
        this.pageIndex.set(index);
      },
      error: (err: unknown) => this.error.set(toApiError(err).message),
    });
  }

  protected rejection(t: Transaction): string {
    return t.rejectionReason ? REJECTION_TEXT[t.rejectionReason] : '';
  }
}
