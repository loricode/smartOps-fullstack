import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { problemMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Logo } from '../shared/logo';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, Logo],
  template: `
    <div class="login">
      <!-- Panel de marca: ilustración vectorial plana (bordes nítidos, sin desenfoque) -->
      <section class="login-art" aria-hidden="true">
        <svg class="login-scene" viewBox="0 0 640 520" preserveAspectRatio="xMinYMin meet">
          <defs>
            <!-- Rayas inspiradas en el tapete de la sala -->
            <pattern id="rug" width="28" height="28" patternUnits="userSpaceOnUse" patternTransform="rotate(-20)">
              <rect width="28" height="28" fill="#b5653a" />
              <rect width="7" height="28" fill="#e9a481" />
              <rect x="14" width="4" height="28" fill="#f9edcc" />
              <rect x="22" width="3" height="28" fill="#8a3f17" />
            </pattern>
            <clipPath id="pill-clip"><rect x="-60" y="70" width="470" height="132" rx="66" /></clipPath>
          </defs>

          <!-- Píldora con rayas y flecha: "la operación entra" -->
          <g class="pill">
            <g clip-path="url(#pill-clip)">
              <rect class="rug-stripes" x="-120" y="40" width="640" height="200" fill="url(#rug)" />
            </g>
            <g class="pill-arrow">
              <circle cx="344" cy="136" r="56" fill="#7cd4e2" />
              <path d="M318 136h50M352 118l18 18-18 18" fill="none" stroke="#1f2a5c" stroke-width="6"
                    stroke-linecap="round" stroke-linejoin="round" />
            </g>
          </g>

          <!-- Pila de comprobantes de colores: "se procesa y concilia" -->
          <g class="stack">
            <g class="card c1"><rect x="440" y="230" width="150" height="92" rx="18" fill="#d4a22c" />
              <rect x="462" y="254" width="70" height="10" rx="5" fill="#fff" opacity=".85" /><rect x="462" y="274" width="46" height="10" rx="5" fill="#fff" opacity=".6" /></g>
            <g class="card c2"><rect x="410" y="290" width="150" height="92" rx="18" fill="#2c8c99" />
              <rect x="432" y="314" width="80" height="10" rx="5" fill="#fff" opacity=".85" /><rect x="432" y="334" width="54" height="10" rx="5" fill="#fff" opacity=".6" /></g>
            <g class="card c3"><rect x="380" y="350" width="150" height="92" rx="18" fill="#fffdf9" />
              <rect x="402" y="374" width="74" height="10" rx="5" fill="#2e3a6e" opacity=".8" /><rect x="402" y="394" width="44" height="10" rx="5" fill="#2e3a6e" opacity=".4" />
              <circle cx="498" cy="396" r="17" fill="#d4f0f2" />
              <path class="check" d="M490 396l6 6 11-12" fill="none" stroke="#0f5f69" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" /></g>
          </g>

          <!-- Formas de acento (sólidas, bordes nítidos) -->
          <circle class="dot d1" cx="560" cy="96" r="14" fill="#f2c14e" />
          <circle class="dot d2" cx="96" cy="300" r="9" fill="#fbe0d2" />
          <rect class="dot d3" x="230" y="262" width="22" height="22" rx="6" fill="#7cd4e2" transform="rotate(18 241 273)" />
        </svg>

        <div class="login-copy">
          <h1>Opera.<br />Concilia.<br /><span>Audita.</span></h1>
          <p>Operaciones bancarias por API y en lotes masivos, conciliadas contra el banco y con un rastro que no se borra.</p>
          <ul class="login-points">
            <li><mat-icon>bolt</mat-icon> Idempotencia: un reintento nunca debita dos veces</li>
            <li><mat-icon>stacks</mat-icon> Spring Batch: miles de registros sin perder ninguno</li>
            <li><mat-icon>verified</mat-icon> Conciliación y auditoría inmutable</li>
          </ul>
        </div>
      </section>

      <!-- Formulario -->
      <section class="login-panel">
        <div class="login-form">
          <app-logo [size]="52" class="login-logo" />
          <h2 class="login-title">Bienvenido de nuevo.</h2>
          <p class="muted login-sub">Ingrese para operar, conciliar y auditar.</p>

          <form (ngSubmit)="submit()">
            <mat-form-field class="full">
              <mat-label>Usuario</mat-label>
              <mat-icon matPrefix>person</mat-icon>
              <input matInput name="username" [(ngModel)]="username" autocomplete="username" required />
            </mat-form-field>
            <mat-form-field class="full" style="margin-top: 14px">
              <mat-label>Contraseña</mat-label>
              <mat-icon matPrefix>lock</mat-icon>
              <input matInput name="password" [type]="showPassword() ? 'text' : 'password'" [(ngModel)]="password"
                     autocomplete="current-password" required />
              <button matIconButton matSuffix type="button" (click)="showPassword.set(!showPassword())"
                      [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>
            @if (error()) {
              <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
            }
            <button matButton="filled" class="full login-btn" [disabled]="busy()">
              {{ busy() ? 'Ingresando…' : 'Ingresar' }}
              @if (!busy()) { <mat-icon iconPositionEnd>arrow_forward</mat-icon> }
            </button>
            @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
          </form>

          <div class="divider"><span>o use un usuario de demostración</span></div>
          <div class="demo-users">
            @for (u of demoUsers; track u.user) {
              <button type="button" class="demo-user" [class.selected]="username === u.user"
                      (click)="username = u.user; password = u.pass">
                <span [class]="'demo-icon ' + u.tone"><mat-icon>{{ u.icon }}</mat-icon></span>
                <span><strong>{{ u.user }}</strong><small>{{ u.role }}</small></span>
              </button>
            }
          </div>
        </div>
      </section>
    </div>
  `,
  styles: `.full { width: 100%; }`,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected username = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly demoUsers = [
    { user: 'operator', pass: 'operator123', role: 'Operador', icon: 'badge', tone: 'teal' },
    { user: 'auditor', pass: 'auditor123', role: 'Auditora', icon: 'policy', tone: 'ochre' },
    { user: 'admin', pass: 'admin123', role: 'Administrador', icon: 'admin_panel_settings', tone: 'cognac' },
  ];

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: e => {
        this.busy.set(false);
        this.error.set(e?.status === 401 ? 'Usuario o contraseña incorrectos' : problemMessage(e));
      },
    });
  }
}
