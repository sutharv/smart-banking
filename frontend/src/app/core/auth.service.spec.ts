import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function setup(): AuthService {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('stores the session after a successful login', () => {
    const auth = setup();
    auth.login('alice', 'Demo!2026').subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'alice', password: 'Demo!2026' });
    request.flush({
      accessToken: 'token-1', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      username: 'alice', role: 'CUSTOMER',
    });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAnalyst()).toBe(false);
    expect(auth.token()).toBe('token-1');
    expect(sessionStorage.getItem('smart-banking.session')).toContain('token-1');
  });

  it('does not restore an expired session', () => {
    sessionStorage.setItem('smart-banking.session', JSON.stringify({
      token: 'old', expiresAt: new Date(Date.now() - 1000).toISOString(), username: 'alice', role: 'CUSTOMER',
    }));
    const auth = setup();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('clears the session and returns to login on logout', () => {
    sessionStorage.setItem('smart-banking.session', JSON.stringify({
      token: 't', expiresAt: new Date(Date.now() + 60_000).toISOString(), username: 'analyst', role: 'ANALYST',
    }));
    const auth = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    expect(auth.isAnalyst()).toBe(true);

    auth.logout('expired');

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('smart-banking.session')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });
});
