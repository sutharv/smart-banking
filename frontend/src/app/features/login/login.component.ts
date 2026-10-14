import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { toApiError } from '../../core/api-error';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  styles: `
    .login {
      display: grid;
      grid-template-columns: minmax(0, 26rem) minmax(0, 22rem);
      gap: 3rem;
      align-items: start;
      max-width: 56rem;
      margin: 0 auto;
      padding: 4rem 1.25rem;
    }
    h1 { font-size: 2.25rem; margin-bottom: 0.75rem; }
    .intro p { color: var(--ink-soft); margin-bottom: 1rem; }
    .demo td, .demo th { padding: 0.4rem 0.75rem; }
    @media (max-width: 760px) { .login { grid-template-columns: 1fr; gap: 2rem; padding-top: 2rem; } }
  `,
  template: `
    <div class="login">
      <section class="intro">
        <h1>Smart Banking Simulator</h1>
        <p>Move fictional money between demo accounts, see how every transfer is posted to a ledger,
          and review the alerts raised by simple, explainable monitoring rules.</p>
        <div class="table-wrap demo">
          <table class="ledger">
            <caption class="sr-only">Demo users</caption>
            <thead><tr><th>Demo user</th><th>Can</th></tr></thead>
            <tbody>
              <tr><td>alice</td><td>Send money from three accounts</td></tr>
              <tr><td>bob</td><td>Send money; one account is frozen</td></tr>
              <tr><td>analyst</td><td>Review alerts across all accounts</td></tr>
            </tbody>
          </table>
        </div>
        <p class="muted" style="margin-top: 0.75rem">Every demo password is <strong>Demo!2026</strong>.</p>
      </section>

      <form class="sheet" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h2 style="margin-bottom: 1.25rem">Log in</h2>
        @if (reason() === 'expired') {
          <p class="notice review" style="margin-bottom: 1rem">Your session ended. Log in again to continue.</p>
        }
        @if (error()) {
          <p class="notice error" role="alert" style="margin-bottom: 1rem">{{ error() }}</p>
        }
        <div class="field">
          <label for="username">Username</label>
          <input id="username" formControlName="username" autocomplete="username"
                 [attr.aria-invalid]="showError('username')" />
          @if (showError('username')) { <span class="error">Enter your username.</span> }
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password"
                 [attr.aria-invalid]="showError('password')" />
          @if (showError('password')) { <span class="error">Enter your password.</span> }
        </div>
        <button class="button" type="submit" [disabled]="busy()">{{ busy() ? 'Logging in…' : 'Log in' }}</button>
      </form>
    </div>
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Bound from the ?reason= query parameter. */
  readonly reason = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly submitted = signal(false);

  protected showError(name: 'username' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (err: unknown) => {
        this.error.set(toApiError(err).message);
        this.busy.set(false);
      },
    });
  }
}
