import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() !== null ? true : inject(Router).createUrlTree(['/login']);
};

export const customerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.session()?.role === 'CUSTOMER' ? true : inject(Router).createUrlTree(['/']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() === null ? true : inject(Router).createUrlTree(['/']);
};
