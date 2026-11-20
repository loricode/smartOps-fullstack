import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BatchExecution, Page, RejectedRecord, RejectionCount } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { reasonLabel } from '../shared/labels';
import { CountUp } from '../shared/count-up';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-batch-detail',
  imports: [CountUp, DatePipe, DecimalPipe, RouterLink, StatusBadge, MatCardModule, MatButtonModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatProgressBarModule, MatTooltipModule],
  template: `
    <a routerLink="/batch" class="back"><mat-icon>arrow_back</mat-icon> Procesamiento masivo</a>
    @if (error()) {
      <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
    }
    @if (execution(); as e) {
      <header class="page-head">
        <div>
          <h1>Lote #{{ e.id }} <app-status [value]="e.status" /></h1>
          <p class="muted">{{ e.fileName }} · cargado por {{ e.startedBy }} · {{ e.startedAt | date: 'medium' }}</p>
        </div>
        <a matButton="outlined" routerLink="/transactions"><mat-icon>list</mat-icon> Ver operaciones</a>
      </header>

      @if (e.status === 'STARTED') {
        <mat-progress-bar mode="indeterminate" />
        <div class="alert info"><mat-icon>hourglass_top</mat-icon>Procesando… la página se actualiza automáticamente.</div>
      }
      @if (e.exitMessage) {
        <div class="alert bad"><mat-icon>error</mat-icon>{{ e.exitMessage }}</div>
      }

      <section class="kpis" style="margin-top: 16px">
        <div class="kpi indigo"><mat-icon>description</mat-icon><span class="kpi-label">Registros leídos</span><span class="kpi-value" [appCountUp]="e.readCount"></span></div>
        <div class="kpi teal"><mat-icon>check_circle</mat-icon><span class="kpi-label">Procesados</span><span class="kpi-value" [appCountUp]="e.processedCount"></span></div>
        <div class="kpi cognac"><mat-icon>block</mat-icon><span class="kpi-label">Rechazados</span><span class="kpi-value" [appCountUp]="e.rejectedCount"></span></div>
        <div class="kpi ochre">
          <mat-icon>timer</mat-icon>
          <span class="kpi-label">Duración</span>
          @if (e.durationMs != null) { <span class="kpi-value" [appCountUp]="e.durationMs" format="seconds"></span> } @else { <span class="kpi-value">—</span> }
          @if (throughput()) { <span class="small">{{ throughput() | number: '1.0-0' }} registros/s</span> }
        </div>
      </section>

      <div class="grid-2">
        <mat-card class="panel">
          <h2><span class="h-icon cognac"><mat-icon>report</mat-icon></span>Rechazos por motivo</h2>
          @for (r of summary(); track r.reasonCode) {
            <div class="bar-row">
              <span>{{ reason(r.reasonCode) }}</span>
              <span class="bar"><span class="bar-fill" [style.width.%]="(r.total / maxReason()) * 100"></span></span>
              <span class="num">{{ r.total | number }}</span>
            </div>
          } @empty {
            <p class="muted">Sin rechazos.</p>
          }
        </mat-card>
        <mat-card class="panel">
          <h2><span class="h-icon teal"><mat-icon>account_tree</mat-icon></span>Flujo del lote</h2>
          <ol class="steps">
            <li class="done"><mat-icon>check_circle</mat-icon>Archivo recibido y registrado (checksum SHA-256 único)</li>
            <li [class.done]="e.status !== 'STARTED' || e.readCount > 0"><mat-icon>check_circle</mat-icon>validateAndLoadStep · chunks de 500</li>
            <li [class.done]="e.status === 'COMPLETED'"><mat-icon>check_circle</mat-icon>postingStep · contabilización de saldos</li>
            <li [class.done]="e.status === 'COMPLETED'"><mat-icon>check_circle</mat-icon>Resumen y auditoría</li>
          </ol>
        </mat-card>
      </div>

      <mat-card class="panel">
        <h2><span class="h-icon ochre"><mat-icon>playlist_remove</mat-icon></span>Registros rechazados en validación</h2>
        <div class="table-wrap">
          <table mat-table [dataSource]="rejected()?.content ?? []">
            <ng-container matColumnDef="line">
              <th mat-header-cell *matHeaderCellDef class="num">Línea</th>
              <td mat-cell *matCellDef="let r" class="num">{{ r.lineNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Referencia</th>
              <td mat-cell *matCellDef="let r" class="mono">{{ r.reference ?? '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="reason">
              <th mat-header-cell *matHeaderCellDef>Motivo</th>
              <td mat-cell *matCellDef="let r"><span class="badge tone-bad">{{ reason(r.reasonCode) }}</span></td>
            </ng-container>
            <ng-container matColumnDef="detail">
              <th mat-header-cell *matHeaderCellDef>Detalle</th>
              <td mat-cell *matCellDef="let r" class="small">{{ r.reasonDetail }}</td>
            </ng-container>
            <ng-container matColumnDef="raw">
              <th mat-header-cell *matHeaderCellDef>Contenido</th>
              <td mat-cell *matCellDef="let r" class="mono small truncate" [matTooltip]="r.rawLine ?? ''">{{ r.rawLine }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Sin registros rechazados</td></tr>
          </table>
        </div>
        @if (rejected(); as p) {
          <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                         (page)="onPage($event)" />
        }
        <p class="muted small">Las operaciones rechazadas al contabilizar (p. ej. saldo insuficiente) aparecen en
          Operaciones con estado Rechazada.</p>
      </mat-card>
    }
  `,
})
export class BatchDetail implements OnInit {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ApiService);
  private timer?: ReturnType<typeof setTimeout>;

  protected readonly columns = ['line', 'reference', 'reason', 'detail', 'raw'];
  protected readonly execution = signal<BatchExecution | null>(null);
  protected readonly summary = signal<RejectionCount[]>([]);
  protected readonly rejected = signal<Page<RejectedRecord> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly reason = reasonLabel;
  protected readonly maxReason = computed(() => Math.max(1, ...this.summary().map(r => r.total)));
  protected readonly throughput = computed(() => {
    const e = this.execution();
    return e?.durationMs ? (e.readCount / e.durationMs) * 1000 : 0;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit(): void {
    this.refresh();
  }

  private refresh(): void {
    forkJoin({ execution: this.api.execution(this.id()), summary: this.api.rejectionSummary(this.id()) })
      .subscribe({
        next: r => {
          this.execution.set(r.execution);
          this.summary.set(r.summary);
          if (r.execution.status === 'STARTED') {
            this.timer = setTimeout(() => this.refresh(), 1500);
          } else {
            this.loadRejected(0);
          }
        },
        error: e => this.error.set(problemMessage(e)),
      });
  }

  protected onPage(event: PageEvent): void {
    this.loadRejected(event.pageIndex);
  }

  private loadRejected(page: number): void {
    this.api.rejections(this.id(), page).subscribe(p => this.rejected.set(p));
  }
}
