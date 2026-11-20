import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BatchExecution, Dashboard as DashboardData, Reconciliation } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { statusLabel, statusTone } from '../shared/labels';
import { CountUp } from '../shared/count-up';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-dashboard',
  imports: [CountUp, DecimalPipe, CurrencyPipe, DatePipe, RouterLink, StatusBadge, MatCardModule,
    MatButtonModule, MatIconModule, MatTableModule, MatProgressBarModule],
  template: `
    <header class="page-head">
      <div>
        <h1>Tablero operativo</h1>
        <p class="muted">Estado consolidado de operaciones, lotes y conciliaciones.</p>
      </div>
      <button matButton="outlined" (click)="load()"><mat-icon>refresh</mat-icon> Actualizar</button>
    </header>

    @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
    @if (error()) {
      <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
    }

    @if (data(); as d) {
      <section class="kpis">
        <div class="kpi indigo">
          <mat-icon>receipt_long</mat-icon>
          <span class="kpi-label">Operaciones registradas</span>
          <span class="kpi-value" [appCountUp]="d.totalTransactions"></span>
        </div>
        <div class="kpi teal">
          <mat-icon>payments</mat-icon>
          <span class="kpi-label">Monto procesado</span>
          <span class="kpi-value" [appCountUp]="d.processedAmount" format="currency"></span>
        </div>
        <div class="kpi cognac">
          <mat-icon>block</mat-icon>
          <span class="kpi-label">Registros rechazados en lotes</span>
          <span class="kpi-value" [appCountUp]="d.rejectedRecords"></span>
        </div>
        <div class="kpi ochre">
          <mat-icon>task_alt</mat-icon>
          <span class="kpi-label">Tasa de conciliación</span>
          <span class="kpi-value" [appCountUp]="d.reconciliationMatchRate" format="percent"></span>
        </div>
      </section>

      <mat-card class="panel">
        <h2><span class="h-icon indigo"><mat-icon>donut_small</mat-icon></span>Operaciones por estado</h2>
        @if (d.totalTransactions === 0) {
          <p class="muted">Aún no hay operaciones. Registre una o cargue un archivo en Procesamiento masivo.</p>
        } @else {
          <div class="stacked-bar" role="img" [attr.aria-label]="barLabel()">
            @for (s of d.byStatus; track s.status) {
              <span [class]="'seg tone-' + tone(s.status)" [style.flex-grow]="s.total"
                    [title]="label(s.status) + ': ' + s.total"></span>
            }
          </div>
          <div class="table-wrap">
            <table mat-table [dataSource]="d.byStatus" style="min-width: 0">
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Estado</th>
                <td mat-cell *matCellDef="let s"><app-status [value]="s.status" /></td>
              </ng-container>
              <ng-container matColumnDef="total">
                <th mat-header-cell *matHeaderCellDef class="num">Operaciones</th>
                <td mat-cell *matCellDef="let s" class="num">{{ s.total | number }}</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef class="num">Monto</th>
                <td mat-cell *matCellDef="let s" class="num">{{ s.amount | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="statusColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: statusColumns"></tr>
            </table>
          </div>
        }
      </mat-card>

      <div class="grid-2">
        <mat-card class="panel">
          <div class="card-head">
            <h2><span class="h-icon teal"><mat-icon>stacks</mat-icon></span>Últimos lotes</h2>
            <a matButton routerLink="/batch">Ver todos</a>
          </div>
          @for (e of executions(); track e.id) {
            <a class="row-link" [routerLink]="['/batch', e.id]">
              <span><strong>#{{ e.id }}</strong> {{ e.fileName }}</span>
              <span class="muted small">{{ e.processedCount | number }} proc. · {{ e.rejectedCount | number }} rech.</span>
              <app-status [value]="e.status" />
            </a>
          } @empty {
            <p class="muted">Sin ejecuciones.</p>
          }
        </mat-card>
        <mat-card class="panel">
          <div class="card-head">
            <h2><span class="h-icon ochre"><mat-icon>rule</mat-icon></span>Últimas conciliaciones</h2>
            <a matButton routerLink="/reconciliations">Ver todas</a>
          </div>
          @for (r of reconciliations(); track r.id) {
            <a class="row-link" [routerLink]="['/reconciliations', r.id]">
              <span><strong>#{{ r.id }}</strong> {{ r.fileName }}</span>
              <span class="muted small">{{ r.startedAt | date: 'short' }}</span>
              <app-status [value]="r.status" />
            </a>
          } @empty {
            <p class="muted">Sin conciliaciones.</p>
          }
        </mat-card>
      </div>
    }
  `,
})
export class Dashboard {
  private readonly api = inject(ApiService);

  protected readonly data = signal<DashboardData | null>(null);
  protected readonly executions = signal<BatchExecution[]>([]);
  protected readonly reconciliations = signal<Reconciliation[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly statusColumns = ['status', 'total', 'amount'];
  protected readonly barLabel = computed(() =>
    (this.data()?.byStatus ?? []).map(s => `${statusLabel(s.status)} ${s.total}`).join(', '));

  protected readonly label = statusLabel;
  protected readonly tone = statusTone;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    forkJoin({
      dashboard: this.api.dashboard(),
      executions: this.api.executions(0, 5),
      reconciliations: this.api.reconciliations(0, 5),
    }).subscribe({
      next: r => {
        this.data.set(r.dashboard);
        this.executions.set(r.executions.content);
        this.reconciliations.set(r.reconciliations.content);
        this.error.set(null);
        this.loading.set(false);
      },
      error: e => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }
}
