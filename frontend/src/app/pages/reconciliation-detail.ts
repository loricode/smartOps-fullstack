import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Page, Reconciliation, ReconciliationDetail as Detail } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { statusLabel } from '../shared/labels';
import { CountUp } from '../shared/count-up';
import { StatusBadge } from '../shared/status-badge';

type ResultFilter = '' | 'MATCHED' | 'AMOUNT_MISMATCH' | 'MISSING_EXTERNAL' | 'MISSING_INTERNAL';

@Component({
  selector: 'app-reconciliation-detail',
  imports: [CountUp, DatePipe, DecimalPipe, CurrencyPipe, RouterLink, StatusBadge, MatCardModule,
    MatButtonModule, MatIconModule, MatTableModule, MatPaginatorModule, MatProgressBarModule],
  template: `
    <a routerLink="/reconciliations" class="back"><mat-icon>arrow_back</mat-icon> Conciliación</a>
    @if (error()) {
      <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
    }
    @if (rec(); as r) {
      <header class="page-head">
        <div>
          <h1>Conciliación #{{ r.id }} <app-status [value]="r.status" /></h1>
          <p class="muted">{{ r.fileName }} · fechas valor {{ r.valueDateFrom ?? '—' }} a {{ r.valueDateTo ?? '—' }} ·
            {{ r.startedBy }} · {{ r.startedAt | date: 'medium' }}</p>
        </div>
      </header>
      @if (r.status === 'STARTED') {
        <mat-progress-bar mode="indeterminate" />
        <div class="alert info"><mat-icon>hourglass_top</mat-icon>Conciliando… la página se actualiza automáticamente.</div>
      }
      @if (r.exitMessage) {
        <div class="alert bad"><mat-icon>error</mat-icon>{{ r.exitMessage }}</div>
      }

      <section class="kpis" style="margin-top: 16px">
        <div class="kpi indigo">
          <mat-icon>donut_large</mat-icon>
          <span class="kpi-label">Tasa de conciliación</span>
          <span class="kpi-value" [appCountUp]="rate()" format="percent"></span>
          <span class="small">{{ r.totalInternal | number }} internas · {{ r.totalExternal | number }} externas</span>
        </div>
        @for (c of cards(); track c.key) {
          <button type="button" [class]="'kpi selectable ' + c.color" [class.selected]="filter() === c.key"
                  (click)="select(c.key)" [attr.aria-pressed]="filter() === c.key">
            <mat-icon>{{ c.icon }}</mat-icon>
            <span class="kpi-label">{{ c.label }}</span>
            <span class="kpi-value" [appCountUp]="c.value"></span>
          </button>
        }
      </section>

      <mat-card class="panel">
        <div class="card-head">
          <h2><span class="h-icon indigo"><mat-icon>filter_alt</mat-icon></span>{{ filter() ? label(filter()) : 'Todas las referencias' }}</h2>
          @if (filter()) { <button matButton (click)="select('')"><mat-icon>clear_all</mat-icon> Ver todas</button> }
        </div>
        <div class="table-wrap">
          <table mat-table [dataSource]="details()?.content ?? []">
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Referencia</th>
              <td mat-cell *matCellDef="let d" class="mono">{{ d.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="result">
              <th mat-header-cell *matHeaderCellDef>Resultado</th>
              <td mat-cell *matCellDef="let d"><app-status [value]="d.result" /></td>
            </ng-container>
            <ng-container matColumnDef="internal">
              <th mat-header-cell *matHeaderCellDef class="num">Interno</th>
              <td mat-cell *matCellDef="let d" class="num">{{ d.internalAmount != null ? (d.internalAmount | currency: 'COP' : 'symbol-narrow') : '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="external">
              <th mat-header-cell *matHeaderCellDef class="num">Externo</th>
              <td mat-cell *matCellDef="let d" class="num">{{ d.externalAmount != null ? (d.externalAmount | currency: 'COP' : 'symbol-narrow') : '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="difference">
              <th mat-header-cell *matHeaderCellDef class="num">Diferencia</th>
              <td mat-cell *matCellDef="let d" class="num" [class.tone-text-bad]="d.difference">{{ d.difference != null ? (d.difference | currency: 'COP' : 'symbol-narrow') : '—' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Sin registros</td></tr>
          </table>
        </div>
        @if (details(); as p) {
          <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                         [showFirstLastButtons]="true" (page)="onPage($event)" />
        }
      </mat-card>
    }
  `,
})
export class ReconciliationDetail implements OnInit {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ApiService);
  private timer?: ReturnType<typeof setTimeout>;

  protected readonly columns = ['reference', 'result', 'internal', 'external', 'difference'];
  protected readonly rec = signal<Reconciliation | null>(null);
  protected readonly details = signal<Page<Detail> | null>(null);
  protected readonly filter = signal<ResultFilter>('AMOUNT_MISMATCH');
  protected readonly error = signal<string | null>(null);
  protected readonly label = statusLabel;

  protected readonly rate = computed(() => {
    const r = this.rec();
    const universe = r ? r.totalInternal + r.missingInternalCount : 0;
    return r && universe ? r.matchedCount / universe : 0;
  });

  protected readonly cards = computed(() => {
    const r = this.rec();
    if (!r) return [];
    return [
      { key: 'MATCHED' as const, label: 'Conciliadas', value: r.matchedCount, color: 'teal', icon: 'check_circle' },
      { key: 'AMOUNT_MISMATCH' as const, label: 'Diferencia de monto', value: r.amountMismatchCount, color: 'ochre', icon: 'difference' },
      { key: 'MISSING_EXTERNAL' as const, label: 'Faltan en extracto', value: r.missingExternalCount, color: 'sand', icon: 'cloud_off' },
      { key: 'MISSING_INTERNAL' as const, label: 'Faltan en SmartOps', value: r.missingInternalCount, color: 'cognac', icon: 'report' },
    ];
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit(): void {
    this.refresh();
  }

  private refresh(): void {
    this.api.reconciliation(this.id()).subscribe({
      next: r => {
        this.rec.set(r);
        if (r.status === 'STARTED') {
          this.timer = setTimeout(() => this.refresh(), 1500);
        } else {
          // Si no hay diferencias de monto, mostrar la primera categoría con registros.
          if (this.filter() === 'AMOUNT_MISMATCH' && r.amountMismatchCount === 0) {
            this.filter.set(this.cards().find(c => c.key !== 'MATCHED' && c.value > 0)?.key ?? '');
          }
          this.loadDetails(0);
        }
      },
      error: e => this.error.set(problemMessage(e)),
    });
  }

  protected select(result: ResultFilter): void {
    this.filter.set(result);
    this.loadDetails(0);
  }

  protected onPage(event: PageEvent): void {
    this.loadDetails(event.pageIndex);
  }

  private loadDetails(page: number): void {
    this.api.reconciliationDetails(this.id(), this.filter(), page).subscribe(p => this.details.set(p));
  }
}
