import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, Role } from './models';

export interface Session {
  token: string;
  expiresAt: string;
  username: string;
  role: Role;
}

const STORAGE_KEY = 'smart-banking.session';

/**
 * Holds the access token for the current browser tab. sessionStorage (not localStorage) limits the
 * token's lifetime to the tab. The UI uses the role only for display; the backend enforces access.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessionState = signal<Session | null>(this.restore());

  readonly session = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => this.sessionState() !== null);
  readonly isAnalyst = computed(() => this.sessionState()?.role === 'ANALYST');

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap((response) => {
        const session: Session = {
          token: response.accessToken,
          expiresAt: response.expiresAt,
          username: response.username,
          role: response.role,
        };
        this.sessionState.set(session);
        this.write(session);
      }),
    );
  }

  /** Returns the token only while it is unexpired. */
  token(): string | null {
    const session = this.sessionState();
    if (!session) return null;
    if (isExpired(session)) {
      this.clear();
      return null;
    }
    return session.token;
  }

  logout(reason?: 'expired'): void {
    this.clear();
    void this.router.navigate(['/login'], reason ? { queryParams: { reason } } : {});
  }

  private clear(): void {
    this.sessionState.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage may be unavailable (private mode); the in-memory session is already cleared.
    }
  }

  private write(session: Session): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Keep the session in memory only.
    }
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      return isExpired(session) ? null : session;
    } catch {
      return null;
    }
  }
}

function isExpired(session: Session): boolean {
  return Date.parse(session.expiresAt) <= Date.now();
}
