import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const auth = { token: vi.fn<() => string | null>(), logout: vi.fn() };
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    auth.token.mockReset().mockReturnValue('secret-token');
    auth.logout.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API requests', () => {
    http.get('/api/accounts').subscribe();
    expect(backend.expectOne('/api/accounts').request.headers.get('Authorization')).toBe('Bearer secret-token');
  });

  it('never sends the token with the login request or to other hosts', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.get('https://example.com/api/x').subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('https://example.com/api/x').request.headers.has('Authorization')).toBe(false);
  });

  it('ends the session when the backend answers 401', () => {
    http.get('/api/accounts').subscribe({ error: () => undefined });
    backend.expectOne('/api/accounts').flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalledWith('expired');
  });

  it('leaves the session alone on 403', () => {
    http.get('/api/reconciliation').subscribe({ error: () => undefined });
    backend.expectOne('/api/reconciliation').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
