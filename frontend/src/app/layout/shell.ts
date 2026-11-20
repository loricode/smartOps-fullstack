import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { Logo } from '../shared/logo';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  role?: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatIconModule,
    MatButtonModule, MatToolbarModule, Logo],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav #drawer class="sidebar" [mode]="mobile() ? 'over' : 'side'" [opened]="!mobile()">
        <div class="sidebar-inner">
          <app-logo class="brand" [size]="42" [light]="true" tagline="Operaciones y conciliación" />
          <mat-nav-list class="nav">
            @for (item of nav(); track item.path) {
              <a mat-list-item [routerLink]="item.path" routerLinkActive="active" #rla="routerLinkActive"
                 [activated]="rla.isActive" (click)="mobile() && drawer.close()">
                <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
          <div class="sidebar-foot">
            <div class="user">
              <span class="avatar">{{ initial() }}</span>
              <div>
                <div>{{ auth.session()?.username }}</div>
                <div class="muted small">{{ auth.session()?.roles?.join(' · ') }}</div>
              </div>
            </div>
            <button matButton="outlined" class="logout" (click)="auth.logout()">
              <mat-icon>logout</mat-icon> Salir
            </button>
          </div>
        </div>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="topbar">
          <button matIconButton aria-label="Abrir menú" (click)="drawer.toggle()"><mat-icon>menu</mat-icon></button>
          <span>SmartOps</span>
        </mat-toolbar>
        <main class="content">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly mobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map(r => r.matches)),
    { initialValue: false },
  );

  private readonly items: NavItem[] = [
    { path: '/dashboard', label: 'Tablero', icon: 'space_dashboard' },
    { path: '/transactions', label: 'Operaciones', icon: 'swap_horiz' },
    { path: '/batch', label: 'Procesamiento masivo', icon: 'stacks' },
    { path: '/reconciliations', label: 'Conciliación', icon: 'rule' },
    { path: '/audit', label: 'Auditoría', icon: 'policy', role: 'AUDITOR' },
  ];
  protected readonly nav = computed(() => this.items.filter(i => !i.role || this.auth.hasRole(i.role)));

  protected initial(): string {
    return (this.auth.session()?.username ?? '?').charAt(0).toUpperCase();
  }
}
