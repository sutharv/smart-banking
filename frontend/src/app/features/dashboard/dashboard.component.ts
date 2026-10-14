import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AuthService } from '../../core/auth.service';
import { BankingApiService } from '../../core/banking-api.service';
import { toApiError } from '../../core/api-error';
import { MoneyPipe } from '../../core/money.pipe';
import { Account, REJECTION_TEXT, Transaction } from '../../core/models';

interface Activity extends Transaction {
  accountReference: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, MoneyPipe],
  template: `
    <header class="page-head">
      <div>
        <h1>{{ auth.isAnalyst() ? 'All accounts' : 'Your accounts' }}</h1>
        <p>
          @if (auth.isAnalyst()) {
            Read-only view of every simulated account, for investigating alerts.
          } @else {
            Balances are kept by the backend and always match the ledger.
          }
        </p>
      </div>
      @if (auth.isAnalyst()) {
        <a class="button" routerLink="/alerts">Review open alerts</a>
      } @else {
        <a class="button" routerLink="/transfer">Send money</a>
      }
    </header>

    @if (error()) {
      <p class="notice error" role="alert">{{ error() }}</p>
    } @else if (loading()) {
      <p class="muted">Loading accounts…</p>
    } @else {
      <div class="table-wrap">
        <table class="ledger">
          <thead>
            <tr><th>Account</th><th>Status</th><th class="amount">Balance</th></tr>
          </thead>
          <tbody>
            @for (account of accounts(); track account.reference) {
              <tr>
                <td>
                  <a [routerLink]="['/accounts', account.reference]">{{ account.displayName }}</a>
                  <span class="sub num">{{ account.reference }}</span>
                </td>
                <td>
                  <span class="mark" [class.ok]="account.status === 'ACTIVE'" [class.bad]="account.status !== 'ACTIVE'">
                    {{ account.status === 'ACTIVE' ? 'Active' : account.status === 'FROZEN' ? 'Frozen' : 'Closed' }}
                  </span>
                </td>
                <td class="amount">{{ account.balance | money: account.currency }}</td>
              </tr>
            } @empty {
              <tr><td colspan="3" class="empty">No accounts to show.</td></tr>
            }
          </tbody>
          @if (totals().length > 0) {
            <tfoot>
              @for (total of totals(); track total.currency) {
                <tr><td colspan="2">Total in {{ total.currency }}</td><td class="amount">{{ total.amount | money: total.currency }}</td></tr>
              }
            </tfoot>
          }
        </table>
      </div>

      @if (!auth.isAnalyst()) {
        <section class="section">
          <h2>Recent activity</h2>
          <div class="table-wrap">
            <table class="ledger">
              <thead>
                <tr><th>When</th><th class="hide-sm">Account</th><th>Details</th><th class="amount">Amount</th></tr>
              </thead>
              <tbody>
                @for (item of activity(); track item.transferId + item.accountReference) {
                  <tr>
                    <td class="when">{{ item.createdAt | date: 'd MMM, HH:mm' }}</td>
                    <td class="num hide-sm">{{ item.accountReference }}</td>
                    <td>
                      <a [routerLink]="['/transfers', item.transferId]">
                        {{ item.direction === 'OUTGOING' ? 'To' : 'From' }} <span class="num">{{ item.counterpartyReference }}</span>
                      </a>
                      @if (item.status === 'REJECTED') {
                        <span class="sub debit">Rejected: {{ rejectionText(item) }}</span>
                      }
                      @if (item.flagged) { <span class="sub" style="color: var(--review)">Flagged for review</span> }
                    </td>
                    <td class="amount" [class.credit]="item.direction === 'INCOMING'"
                        [class.debit]="item.direction === 'OUTGOING' && item.status === 'COMPLETED'"
                        [class.struck]="item.status === 'REJECTED'">
                      {{ (item.direction === 'OUTGOING' ? -item.amount : item.amount) | money: item.currency : true }}
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="empty">No transfers yet. <a routerLink="/transfer">Send your first transfer</a>.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    }
  `,
})
export class DashboardComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(BankingApiService);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly activity = signal<Activity[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly totals = computed(() => {
    const sums = new Map<string, number>();
    for (const account of this.accounts()) {
      // Display-only aggregation, rounded to cents to avoid floating-point noise.
      sums.set(account.currency, Math.round(((sums.get(account.currency) ?? 0) + account.balance) * 100) / 100);
    }
    return [...sums].map(([currency, amount]) => ({ currency, amount }));
  });

  ngOnInit(): void {
    const isAnalyst = this.auth.isAnalyst();
    this.api.accounts().pipe(
      switchMap((accounts) => {
        this.accounts.set(accounts);
        if (isAnalyst || accounts.length === 0) return of([] as Activity[]);
        return forkJoin(accounts.map((account) =>
          this.api.transactions(account.reference, 0, 5).pipe(
            map((page) => page.content.map((t) => ({ ...t, accountReference: account.reference }))),
          ),
        )).pipe(
          map((lists) => lists.flat()
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
            .slice(0, 8)),
        );
      }),
      catchError((err: unknown) => {
        this.error.set(toApiError(err).message);
        return of([] as Activity[]);
      }),
    ).subscribe((activity) => {
      this.activity.set(activity);
      this.loading.set(false);
    });
  }

  protected rejectionText(item: Transaction): string {
    return item.rejectionReason ? REJECTION_TEXT[item.rejectionReason] : '';
  }
}
