import { Routes } from '@angular/router';
import { authGuard, customerGuard, guestGuard } from './core/auth.guard';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Log in · Smart Banking Simulator',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Overview · Smart Banking Simulator',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'accounts/:reference',
        title: 'Account · Smart Banking Simulator',
        loadComponent: () =>
          import('./features/account-detail/account-detail.component').then((m) => m.AccountDetailComponent),
      },
      {
        path: 'transfer',
        canActivate: [customerGuard],
        title: 'Send money · Smart Banking Simulator',
        loadComponent: () => import('./features/transfer/transfer.component').then((m) => m.TransferComponent),
      },
      {
        path: 'transfers/:id',
        title: 'Transfer · Smart Banking Simulator',
        loadComponent: () =>
          import('./features/transfer-detail/transfer-detail.component').then((m) => m.TransferDetailComponent),
      },
      {
        path: 'alerts',
        title: 'Alerts · Smart Banking Simulator',
        loadComponent: () => import('./features/alerts/alerts.component').then((m) => m.AlertsComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
