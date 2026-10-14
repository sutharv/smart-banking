import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Attaches the bearer token to API calls and ends the session when the backend rejects it. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const isApi = request.url.startsWith('/api/');
  const isLogin = request.url === '/api/auth/login';
  const token = isApi && !isLogin ? auth.token() : null;
  const authorised = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(authorised).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApi && !isLogin) {
        auth.logout('expired');
      }
      return throwError(() => error);
    }),
  );
};
