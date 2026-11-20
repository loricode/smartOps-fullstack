import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Session } from './api.models';
import { ApiService } from './api.service';

const STORAGE_KEY = 'smartops.auth';

interface StoredAuth {
  header: string;
  session: Session;
}

/**
 * Autenticación HTTP Basic contra el backend. La credencial vive solo en sessionStorage
 * (se borra al cerrar la pestaña). En producción se reemplazaría por OIDC + tokens.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly state = signal<StoredAuth | null>(read());

  readonly session = computed(() => this.state()?.session ?? null);
  readonly isAuthenticated = computed(() => this.state() !== null);

  authorizationHeader(): string | null {
    return this.state()?.header ?? null;
  }

  hasRole(role: string): boolean {
    return this.session()?.roles.includes(role) ?? false;
  }

  login(username: string, password: string): Observable<Session> {
    const header = 'Basic ' + btoa(`${username}:${password}`);
    return this.api.session(header).pipe(
      tap(session => {
        const auth = { header, session };
        try {
          sessionStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
        } catch {
          /* almacenamiento no disponible: la sesión dura lo que la pestaña */
        }
        this.state.set(auth);
      }),
    );
  }

  logout(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignorar */
    }
    this.state.set(null);
    this.router.navigate(['/login']);
  }
}

function read(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredAuth) : null;
  } catch {
    return null;
  }
}
