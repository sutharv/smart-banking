import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BankingApiService } from '../../core/banking-api.service';
import { ApiError, toApiError } from '../../core/api-error';
import { MoneyPipe } from '../../core/money.pipe';
import { Account, RULE_NAMES, Transfer } from '../../core/models';

/** Up to 13 digits, optionally followed by one or two decimals. No signs, no exponents. */
export const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

function amountValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = (control.value ?? '').trim();
  if (value === '') return { required: true };
  if (!AMOUNT_PATTERN.test(value)) return { format: true };
  if (Number(value) <= 0) return { min: true };
  if (Number(value) > 1_000_000) return { max: true };
  return null;
}

function newIdempotencyKey(): string {
  return `web-${crypto.randomUUID()}`;
}

type FieldName = 'sourceAccountReference' | 'destinationAccountReference' | 'amount' | 'reference';

const FIELD_LABELS: Record<FieldName, string> = {
  sourceAccountReference: 'Sending account',
  destinationAccountReference: 'Receiving account',
  amount: 'Amount',
  reference: 'Reference',
};

@Component({
  selector: 'app-transfer',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe],
  styles: `
    .layout { display: grid; grid-template-columns: minmax(0, 30rem) minmax(0, 1fr); gap: 2.5rem; align-items: start; }
    .result { display: grid; gap: 1rem; }
    .result ul { margin: 0.4rem 0 0; padding-left: 1.1rem; }
    .actions { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
  `,
  template: `
    <header class="page-head">
      <div>
        <h1>Send money</h1>
        <p>Move funds between demo accounts in the same currency. You can send to any account reference.</p>
      </div>
    </header>

    <div class="layout">
      <form class="sheet" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="source">From</label>
          <select id="source" formControlName="sourceAccountReference" [attr.aria-invalid]="invalid('sourceAccountReference')">
            <option value="" disabled>Choose an account</option>
            @for (account of activeAccounts(); track account.reference) {
              <option [value]="account.reference">{{ account.displayName }} ({{ account.reference }})</option>
            }
          </select>
          @if (source(); as src) {
            <span class="hint num">Available: {{ src.balance | money: src.currency }}</span>
          }
          @if (invalid('sourceAccountReference')) { <span class="error">{{ message('sourceAccountReference') }}</span> }
        </div>

        <div class="field">
          <label for="destination">To account reference</label>
          <input id="destination" formControlName="destinationAccountReference" list="own-accounts"
                 placeholder="SB-EUR-0004" autocomplete="off" spellcheck="false"
                 [attr.aria-invalid]="invalid('destinationAccountReference')" />
          <datalist id="own-accounts">
            @for (account of activeAccounts(); track account.reference) {
              <option [value]="account.reference">{{ account.displayName }}</option>
            }
          </datalist>
          <span class="hint">Pick one of your accounts or type another customer's reference.</span>
          @if (invalid('destinationAccountReference')) { <span class="error">{{ message('destinationAccountReference') }}</span> }
        </div>

        <div class="field">
          <label for="amount">Amount</label>
          <div class="amount-input">
            <input id="amount" formControlName="amount" inputmode="decimal" autocomplete="off" placeholder="0.00"
                   [attr.aria-invalid]="invalid('amount')" aria-describedby="amount-currency" />
            <span id="amount-currency">{{ source()?.currency ?? '—' }}</span>
          </div>
          @if (invalid('amount')) { <span class="error">{{ message('amount') }}</span> }
        </div>

        <div class="field">
          <label for="reference">Payment reference <span class="muted">(optional)</span></label>
          <input id="reference" formControlName="reference" maxlength="140" placeholder="e.g. Rent for March"
                 [attr.aria-invalid]="invalid('reference')" />
          @if (invalid('reference')) { <span class="error">{{ message('reference') }}</span> }
        </div>

        <button class="button" type="submit" [disabled]="busy()">{{ busy() ? 'Sending…' : 'Send money' }}</button>
      </form>

      <div class="result" aria-live="polite">
        @if (failure(); as f) {
          <div class="notice error" role="alert">
            <strong>{{ f.status === 422 ? 'Transfer rejected' : 'Transfer not sent' }}</strong>
            {{ f.message }}
            @if (f.status === 0) {
              <p style="margin-top: 0.4rem">Sending again is safe: it reuses the same request key, so you will not be charged twice.</p>
            }
            @if (f.transferId) {
              <p style="margin-top: 0.4rem"><a [routerLink]="['/transfers', f.transferId]">View the recorded attempt</a></p>
            }
          </div>
        }
        @if (completed(); as t) {
          <div class="notice success">
            <strong>Sent {{ t.amount | money: t.currency }} to {{ t.destinationAccountReference }}</strong>
            Completed and posted to the ledger.
            <div class="actions" style="margin-top: 0.5rem">
              <a [routerLink]="['/transfers', t.id]">View transfer</a>
              <a [routerLink]="['/accounts', t.sourceAccountReference]">View {{ t.sourceAccountReference }}</a>
            </div>
          </div>
          @if (t.alerts.length > 0) {
            <div class="notice review">
              <strong>Flagged for review</strong>
              The transfer went through, and monitoring raised {{ t.alerts.length === 1 ? 'an alert' : t.alerts.length + ' alerts' }}:
              <ul>
                @for (alert of t.alerts; track alert.id) {
                  <li><b>{{ ruleName(alert.ruleCode) }}.</b> {{ alert.explanation }}</li>
                }
              </ul>
            </div>
          }
        }
        @if (!failure() && !completed()) {
          <div class="sheet muted">
            <p>Transfers either complete in full or not at all: both balances and the two ledger lines are written together.</p>
            <p style="margin-top: 0.6rem">Large or rapid transfers still complete, but are flagged for an analyst to review.</p>
          </div>
        }
      </div>
    </div>
  `,
})
export class TransferComponent implements OnInit {
  private readonly api = inject(BankingApiService);
  private readonly destroyRef = inject(DestroyRef);

  /** Optional ?from= query parameter preselecting the source account. */
  readonly from = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    sourceAccountReference: ['', Validators.required],
    destinationAccountReference: ['', [Validators.required, Validators.maxLength(34)]],
    amount: ['', amountValidator],
    reference: ['', Validators.maxLength(140)],
  });

  protected readonly accounts = signal<Account[]>([]);
  protected readonly activeAccounts = computed(() => this.accounts().filter((a) => a.status === 'ACTIVE'));
  private readonly sourceRef = toSignal(this.form.controls.sourceAccountReference.valueChanges, { initialValue: '' });
  protected readonly source = computed(() => this.accounts().find((a) => a.reference === this.sourceRef()) ?? null);

  protected readonly busy = signal(false);
  protected readonly completed = signal<Transfer | null>(null);
  protected readonly failure = signal<ApiError | null>(null);
  private readonly serverErrors = signal<Partial<Record<FieldName, string>>>({});
  private readonly submitted = signal(false);

  /** Kept across retries of the same form contents; replaced whenever the request changes. */
  private idempotencyKey = newIdempotencyKey();

  ngOnInit(): void {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.idempotencyKey = newIdempotencyKey();
      this.serverErrors.set({});
    });
    this.loadAccounts();
  }

  private loadAccounts(): void {
    this.api.accounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        const preselect = this.from();
        if (preselect && accounts.some((a) => a.reference === preselect && a.status === 'ACTIVE')) {
          this.form.controls.sourceAccountReference.setValue(preselect);
        }
      },
      error: (err: unknown) => this.failure.set(toApiError(err)),
    });
  }

  protected invalid(name: FieldName): boolean {
    const control = this.form.controls[name];
    return (control.invalid && (control.touched || this.submitted())) || !!this.serverErrors()[name]
      || (name === 'destinationAccountReference' && this.sameAccount() && this.submitted());
  }

  protected message(name: FieldName): string {
    const server = this.serverErrors()[name];
    if (server) return `${FIELD_LABELS[name]} ${server}.`;
    const errors = this.form.controls[name].errors ?? {};
    switch (name) {
      case 'sourceAccountReference':
        return 'Choose the account to send from.';
      case 'destinationAccountReference':
        if (errors['required']) return 'Enter the account reference to send to.';
        if (errors['maxlength']) return 'Account references are at most 34 characters.';
        return 'Choose a different account from the one you are sending from.';
      case 'amount':
        if (errors['required']) return 'Enter an amount.';
        if (errors['format']) return 'Use a number with up to two decimal places, like 25 or 25.50.';
        if (errors['min']) return 'The amount must be at least 0.01.';
        return 'The simulator accepts at most 1,000,000.00 per transfer.';
      case 'reference':
        return 'Keep the reference to 140 characters or fewer.';
    }
  }

  private sameAccount(): boolean {
    const { sourceAccountReference, destinationAccountReference } = this.form.getRawValue();
    return !!sourceAccountReference && sourceAccountReference === destinationAccountReference.trim().toUpperCase();
  }

  protected submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const source = this.source();
    if (this.form.invalid || this.sameAccount() || !source || this.busy()) return;

    const value = this.form.getRawValue();
    this.busy.set(true);
    this.failure.set(null);
    this.completed.set(null);

    this.api.createTransfer({
      sourceAccountReference: value.sourceAccountReference,
      destinationAccountReference: value.destinationAccountReference.trim().toUpperCase(),
      amount: Number(value.amount.trim()),
      currency: source.currency,
      reference: value.reference.trim() || null,
    }, this.idempotencyKey).subscribe({
      next: (response) => {
        this.busy.set(false);
        this.completed.set(response.body);
        this.form.patchValue({ amount: '', reference: '' });
        this.form.markAsUntouched();
        this.submitted.set(false);
        this.idempotencyKey = newIdempotencyKey();
        this.loadAccounts();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const apiError = toApiError(err);
        this.failure.set(apiError);
        this.serverErrors.set(apiError.fieldErrors as Partial<Record<FieldName, string>>);
        if (apiError.status === 422) this.loadAccounts();
      },
    });
  }

  protected ruleName(code: string): string {
    return RULE_NAMES[code] ?? code;
  }
}
