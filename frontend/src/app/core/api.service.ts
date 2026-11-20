import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account, AuditEntry, BatchExecution, CreateTransaction, Dashboard, Page, Problem, Reconciliation,
  ReconciliationDetail, RejectedRecord, RejectionCount, Session, Transaction, TransactionFilter,
} from './api.models';

const BASE = '/api/v1';

/** Cliente tipado del API REST. Un único punto de acceso para todos los módulos. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  session(authHeader?: string): Observable<Session> {
    const headers = authHeader ? new HttpHeaders({ Authorization: authHeader }) : undefined;
    return this.http.get<Session>(`${BASE}/session`, { headers });
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${BASE}/dashboard`);
  }

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${BASE}/accounts`);
  }

  // ---- Operaciones
  transactions(filter: TransactionFilter, page: number, size = 20): Observable<Page<Transaction>> {
    return this.http.get<Page<Transaction>>(`${BASE}/transactions`, { params: params({ ...filter, page, size }) });
  }

  createTransaction(body: CreateTransaction, idempotencyKey: string): Observable<HttpResponse<Transaction>> {
    return this.http.post<Transaction>(`${BASE}/transactions`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
      observe: 'response',
    });
  }

  // ---- Lotes
  uploadOperations(file: File): Observable<{ fileId: number; executionId: number }> {
    return this.http.post<{ fileId: number; executionId: number }>(`${BASE}/batch/imports`, form(file));
  }

  executions(page: number, size = 20): Observable<Page<BatchExecution>> {
    return this.http.get<Page<BatchExecution>>(`${BASE}/batch/executions`, { params: params({ page, size }) });
  }

  execution(id: number): Observable<BatchExecution> {
    return this.http.get<BatchExecution>(`${BASE}/batch/executions/${id}`);
  }

  rejections(id: number, page: number, size = 50): Observable<Page<RejectedRecord>> {
    return this.http.get<Page<RejectedRecord>>(`${BASE}/batch/executions/${id}/rejections`,
      { params: params({ page, size }) });
  }

  rejectionSummary(id: number): Observable<RejectionCount[]> {
    return this.http.get<RejectionCount[]>(`${BASE}/batch/executions/${id}/rejections/summary`);
  }

  // ---- Conciliación
  uploadStatement(file: File): Observable<{ statementFileId: number; reconciliationId: number }> {
    return this.http.post<{ statementFileId: number; reconciliationId: number }>(`${BASE}/reconciliations`, form(file));
  }

  reconciliations(page: number, size = 20): Observable<Page<Reconciliation>> {
    return this.http.get<Page<Reconciliation>>(`${BASE}/reconciliations`, { params: params({ page, size }) });
  }

  reconciliation(id: number): Observable<Reconciliation> {
    return this.http.get<Reconciliation>(`${BASE}/reconciliations/${id}`);
  }

  reconciliationDetails(id: number, result: string, page: number, size = 50): Observable<Page<ReconciliationDetail>> {
    return this.http.get<Page<ReconciliationDetail>>(`${BASE}/reconciliations/${id}/details`,
      { params: params({ result, page, size }) });
  }

  // ---- Auditoría
  audit(filter: { entityType?: string; entityId?: string; actor?: string }, page: number, size = 50):
    Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>(`${BASE}/audit`, { params: params({ ...filter, page, size }) });
  }
}

function params(values: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}

function form(file: File): FormData {
  const data = new FormData();
  data.append('file', file, file.name);
  return data;
}

/** Mensaje legible a partir de un error HTTP (RFC 7807 si está disponible). */
export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const p = error.error as Problem | null;
    if (p && typeof p === 'object' && p.detail) {
      const extra = p.errors?.map(e => (typeof e === 'string' ? e : `${e.field}: ${e.message}`)).join('; ');
      return extra ? `${p.detail} (${extra})` : p.detail;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    return `Error ${error.status}`;
  }
  return 'Error inesperado';
}
