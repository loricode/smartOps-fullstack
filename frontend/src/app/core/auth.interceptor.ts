import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Añade la credencial a cada llamada al API y cierra sesión si el backend responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const header = auth.authorizationHeader();
  const request = header && !req.headers.has('Authorization')
    ? req.clone({ setHeaders: { Authorization: header } })
    : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && auth.isAuthenticated()) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
