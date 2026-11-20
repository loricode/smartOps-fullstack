import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', title: 'SmartOps · Ingreso', loadComponent: () => import('./pages/login').then(m => m.Login) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'SmartOps · Tablero', loadComponent: () => import('./pages/dashboard').then(m => m.Dashboard) },
      { path: 'transactions', title: 'SmartOps · Operaciones', loadComponent: () => import('./pages/transactions').then(m => m.Transactions) },
      { path: 'batch', title: 'SmartOps · Lotes', loadComponent: () => import('./pages/batch').then(m => m.Batch) },
      { path: 'batch/:id', title: 'SmartOps · Lote', loadComponent: () => import('./pages/batch-detail').then(m => m.BatchDetail) },
      { path: 'reconciliations', title: 'SmartOps · Conciliación', loadComponent: () => import('./pages/reconciliations').then(m => m.Reconciliations) },
      { path: 'reconciliations/:id', title: 'SmartOps · Conciliación', loadComponent: () => import('./pages/reconciliation-detail').then(m => m.ReconciliationDetail) },
      { path: 'audit', title: 'SmartOps · Auditoría', canActivate: [roleGuard('AUDITOR')], loadComponent: () => import('./pages/audit').then(m => m.Audit) },
    ],
  },
  { path: '**', redirectTo: '' },
];
