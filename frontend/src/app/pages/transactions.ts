import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Account, CreateTransaction, Page, Transaction, TransactionFilter, TransactionType } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TYPE_LABELS, statusLabel } from '../shared/labels';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-transactions',
  imports: [FormsModule, CurrencyPipe, DatePipe, StatusBadge, MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule, MatPaginatorModule, MatTooltipModule,
    MatProgressBarModule],
  template: `
    <header class="page-head">
      <div>
        <h1>Operaciones</h1>
        <p class="muted">Registro idempotente por API y consulta de todas las operaciones (API y lotes).</p>
      </div>
      @if (canCreate) {
        <button [matButton]="showForm() ? 'outlined' : 'filled'" (click)="toggleForm()">
          <mat-icon>{{ showForm() ? 'close' : 'add' }}</mat-icon> {{ showForm() ? 'Cerrar' : 'Nueva operación' }}
        </button>
      }
    </header>

    @if (showForm()) {
      <mat-card class="panel">
        <h2><span class="h-icon teal"><mat-icon>add_card</mat-icon></span>Registrar operación</h2>
        <form class="form-grid" (ngSubmit)="create()">
          <mat-form-field>
            <mat-label>Tipo</mat-label>
            <mat-select name="type" [(ngModel)]="draft.type" (selectionChange)="onTypeChange()">
              @for (t of types; track t) { <mat-option [value]="t">{{ typeLabel(t) }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Cuenta origen</mat-label>
            <mat-select name="source" [(ngModel)]="draft.sourceAccount" [disabled]="draft.type === 'COLLECTION'">
              <mat-option [value]="null">—</mat-option>
              @for (a of accounts(); track a.accountNumber) {
                <mat-option [value]="a.accountNumber">
                  {{ a.accountNumber }} · {{ a.balance | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}{{ a.status === 'BLOCKED' ? ' (bloqueada)' : '' }}
                </mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Cuenta destino</mat-label>
            <mat-select name="target" [(ngModel)]="draft.targetAccount" [disabled]="draft.type === 'PAYMENT'">
              <mat-option [value]="null">—</mat-option>
              @for (a of accounts(); track a.accountNumber) {
                <mat-option [value]="a.accountNumber">{{ a.accountNumber }}{{ a.status === 'BLOCKED' ? ' (bloqueada)' : '' }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Monto</mat-label>
            <span matTextPrefix>$&nbsp;</span>
            <input matInput name="amount" type="number" min="0.01" step="0.01" [(ngModel)]="draft.amount" required />
            <span matTextSuffix>COP</span>
          </mat-form-field>
          <mat-form-field class="wide">
            <mat-label>Descripción</mat-label>
            <input matInput name="description" maxlength="255" [(ngModel)]="draft.description" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Clave de idempotencia</mat-label>
            <input matInput name="key" [(ngModel)]="idempotencyKey" pattern="[A-Za-z0-9_\\-]{4,64}" required />
            <button matIconButton matSuffix type="button" matTooltip="Generar nueva clave" (click)="newKey()">
              <mat-icon>autorenew</mat-icon>
            </button>
          </mat-form-field>
          <div class="form-actions">
            <span class="muted small"><mat-icon inline>info</mat-icon> Reenviar con la misma clave no duplica la operación.</span>
            <button matButton="filled" [disabled]="busy()"><mat-icon>send</mat-icon> {{ busy() ? 'Enviando…' : 'Registrar' }}</button>
          </div>
        </form>
        @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
        @if (result(); as r) {
          <div [class]="'alert ' + (r.tx.status === 'REJECTED' ? 'bad' : r.replayed ? 'info' : 'ok')">
            <mat-icon>{{ r.tx.status === 'REJECTED' ? 'block' : r.replayed ? 'replay' : 'check_circle' }}</mat-icon>
            <span>
              @if (r.replayed) {
                Reintento detectado: se devolvió la operación original <strong>{{ r.tx.reference }}</strong> sin reprocesarla.
              } @else if (r.tx.status === 'REJECTED') {
                Operación <strong>{{ r.tx.reference }}</strong> registrada como rechazada: {{ r.tx.rejectionReason }}
              } @else {
                Operación <strong>{{ r.tx.reference }}</strong> procesada.
              }
            </span>
          </div>
        }
        @if (formError()) {
          <div class="alert bad"><mat-icon>error</mat-icon>{{ formError() }}</div>
        }
      </mat-card>
    }

    <mat-card class="panel">
      <form class="filters" (ngSubmit)="search(0)">
        <mat-form-field>
          <mat-label>Referencia</mat-label>
          <mat-icon matPrefix>search</mat-icon>
          <input matInput name="reference" [(ngModel)]="filter.reference" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Estado</mat-label>
          <mat-select name="status" [(ngModel)]="filter.status">
            <mat-option value="">Todos</mat-option>
            @for (s of statuses; track s) { <mat-option [value]="s">{{ statusLabel(s) }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Tipo</mat-label>
          <mat-select name="type" [(ngModel)]="filter.type">
            <mat-option value="">Todos</mat-option>
            @for (t of types; track t) { <mat-option [value]="t">{{ typeLabel(t) }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Canal</mat-label>
          <mat-select name="channel" [(ngModel)]="filter.channel">
            <mat-option value="">API y lotes</mat-option>
            <mat-option value="API">API</mat-option>
            <mat-option value="BATCH">Lote</mat-option>
          </mat-select>
        </mat-form-field>
        <button matButton="tonal"><mat-icon>filter_list</mat-icon> Buscar</button>
      </form>

      @if (error()) {
        <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
      }

      <div class="table-wrap">
        <table mat-table [dataSource]="page()?.content ?? []">
          <ng-container matColumnDef="reference">
            <th mat-header-cell *matHeaderCellDef>Referencia</th>
            <td mat-cell *matCellDef="let t" class="mono">{{ t.reference }}</td>
          </ng-container>
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>Tipo</th>
            <td mat-cell *matCellDef="let t">{{ typeLabel(t.type) }}</td>
          </ng-container>
          <ng-container matColumnDef="accounts">
            <th mat-header-cell *matHeaderCellDef>Origen → destino</th>
            <td mat-cell *matCellDef="let t" class="mono nowrap">{{ t.sourceAccount ?? '—' }} → {{ t.targetAccount ?? '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="num">Monto</th>
            <td mat-cell *matCellDef="let t" class="num">{{ t.amount | currency: t.currency : 'symbol-narrow' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let t">
              <app-status [value]="t.status" [matTooltip]="t.rejectionReason ?? ''" [matTooltipDisabled]="!t.rejectionReason" />
            </td>
          </ng-container>
          <ng-container matColumnDef="channel">
            <th mat-header-cell *matHeaderCellDef>Canal</th>
            <td mat-cell *matCellDef="let t">
              <span class="tag">{{ t.channel === 'API' ? 'API' : 'LOTE #' + t.batchFileId }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="created">
            <th mat-header-cell *matHeaderCellDef>Registrada</th>
            <td mat-cell *matCellDef="let t" class="muted small nowrap">{{ t.createdAt | date: 'short' }}<br />{{ t.createdBy }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Sin resultados</td></tr>
        </table>
      </div>
      @if (page(); as p) {
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                       [showFirstLastButtons]="true" (page)="onPage($event)" />
      }
    </mat-card>
  `,
})
export class Transactions {
  private readonly api = inject(ApiService);
  protected readonly canCreate = inject(AuthService).hasRole('OPERATOR');

  protected readonly types: TransactionType[] = ['TRANSFER', 'PAYMENT', 'COLLECTION'];
  protected readonly statuses = ['RECEIVED', 'VALIDATED', 'PROCESSING', 'PROCESSED', 'REJECTED', 'RECONCILED'];
  protected readonly columns = ['reference', 'type', 'accounts', 'amount', 'status', 'channel', 'created'];
  protected readonly typeLabel = (t: string) => TYPE_LABELS[t] ?? t;
  protected readonly statusLabel = statusLabel;

  protected filter: TransactionFilter = { reference: '', status: '', type: '', channel: '' };
  protected readonly page = signal<Page<Transaction> | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly showForm = signal(false);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly busy = signal(false);
  protected readonly result = signal<{ tx: Transaction; replayed: boolean } | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected draft: CreateTransaction = {
    type: 'TRANSFER', sourceAccount: null, targetAccount: null, amount: 100000, currency: 'COP', description: '',
  };
  protected idempotencyKey = generateKey();

  constructor() {
    this.search(0);
  }

  protected onPage(event: PageEvent): void {
    this.search(event.pageIndex);
  }

  protected search(pageNumber: number): void {
    this.api.transactions(this.filter, pageNumber).subscribe({
      next: p => {
        this.page.set(p);
        this.error.set(null);
      },
      error: e => this.error.set(problemMessage(e)),
    });
  }

  protected toggleForm(): void {
    this.showForm.update(v => !v);
    if (this.showForm() && this.accounts().length === 0) {
      this.api.accounts().subscribe(a => this.accounts.set(a));
    }
  }

  protected onTypeChange(): void {
    if (this.draft.type === 'PAYMENT') {
      this.draft.targetAccount = null;
    }
    if (this.draft.type === 'COLLECTION') {
      this.draft.sourceAccount = null;
    }
  }

  protected newKey(): void {
    this.idempotencyKey = generateKey();
    this.result.set(null);
  }

  protected create(): void {
    this.busy.set(true);
    this.formError.set(null);
    this.result.set(null);
    this.api.createTransaction(this.draft, this.idempotencyKey).subscribe({
      next: res => {
        this.busy.set(false);
        this.result.set({ tx: res.body!, replayed: res.headers.get('Idempotent-Replayed') === 'true' });
        this.search(0);
        this.api.accounts().subscribe(a => this.accounts.set(a));
      },
      error: e => {
        this.busy.set(false);
        this.formError.set(problemMessage(e));
      },
    });
  }
}

function generateKey(): string {
  return 'WEB-' + crypto.randomUUID().replaceAll('-', '').slice(0, 16).toUpperCase();
}
