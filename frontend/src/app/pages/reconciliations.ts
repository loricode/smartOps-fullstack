import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { Page, Reconciliation } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { FileUpload } from '../shared/file-upload';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-reconciliations',
  imports: [DatePipe, DecimalPipe, StatusBadge, FileUpload, MatCardModule, MatButtonModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatProgressBarModule],
  template: `
    <header class="page-head">
      <div>
        <h1>Conciliación bancaria</h1>
        <p class="muted">Compara las operaciones procesadas contra un extracto externo e identifica coincidencias,
          diferencias y faltantes.</p>
      </div>
    </header>

    @if (canUpload) {
      <mat-card class="panel">
        <h2><span class="h-icon ochre"><mat-icon>compare_arrows</mat-icon></span>Conciliar extracto</h2>
        <app-file-upload title="Extracto externo (.csv)" actionLabel="Conciliar" [busy]="busy()"
                         hint="reference,amount,currency,value_date" (submitted)="upload($event)" />
        @if (error()) {
          <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
        }
      </mat-card>
    }

    <mat-card class="panel">
      <div class="card-head">
        <h2><span class="h-icon indigo"><mat-icon>history</mat-icon></span>Conciliaciones</h2>
        <button matButton (click)="load(currentPage)"><mat-icon>refresh</mat-icon> Actualizar</button>
      </div>
      @if (running()) { <mat-progress-bar mode="indeterminate" /> }
      <div class="table-wrap">
        <table mat-table [dataSource]="page()?.content ?? []">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let r"><strong>#{{ r.id }}</strong></td>
          </ng-container>
          <ng-container matColumnDef="file">
            <th mat-header-cell *matHeaderCellDef>Extracto</th>
            <td mat-cell *matCellDef="let r">{{ r.fileName }}<div class="muted small">{{ r.startedBy }} · {{ r.startedAt | date: 'short' }}</div></td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let r"><app-status [value]="r.status" /></td>
          </ng-container>
          <ng-container matColumnDef="dates">
            <th mat-header-cell *matHeaderCellDef>Fechas valor</th>
            <td mat-cell *matCellDef="let r" class="small nowrap">{{ r.valueDateFrom ?? '—' }}{{ r.valueDateTo && r.valueDateTo !== r.valueDateFrom ? ' → ' + r.valueDateTo : '' }}</td>
          </ng-container>
          <ng-container matColumnDef="matched">
            <th mat-header-cell *matHeaderCellDef class="num">Conciliadas</th>
            <td mat-cell *matCellDef="let r" class="num tone-text-ok">{{ r.matchedCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="mismatch">
            <th mat-header-cell *matHeaderCellDef class="num">Diferencias</th>
            <td mat-cell *matCellDef="let r" class="num tone-text-warn">{{ r.amountMismatchCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="missingExt">
            <th mat-header-cell *matHeaderCellDef class="num">Faltan ext.</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.missingExternalCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="missingInt">
            <th mat-header-cell *matHeaderCellDef class="num">Faltan int.</th>
            <td mat-cell *matCellDef="let r" class="num tone-text-bad">{{ r.missingInternalCount | number }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="router.navigate(['/reconciliations', row.id])"></tr>
          <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Aún no hay conciliaciones</td></tr>
        </table>
      </div>
      @if (page(); as p) {
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                       (page)="onPage($event)" />
      }
    </mat-card>
  `,
})
export class Reconciliations {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly router = inject(Router);
  protected readonly canUpload = inject(AuthService).hasRole('OPERATOR');
  private readonly uploader = viewChild(FileUpload);

  protected readonly columns = ['id', 'file', 'status', 'dates', 'matched', 'mismatch', 'missingExt', 'missingInt'];
  protected readonly page = signal<Page<Reconciliation> | null>(null);
  protected readonly running = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected currentPage = 0;
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
    this.load(0);
  }

  protected onPage(event: PageEvent): void {
    this.load(event.pageIndex);
  }

  protected load(pageNumber: number): void {
    this.currentPage = pageNumber;
    clearTimeout(this.timer);
    this.api.reconciliations(pageNumber).subscribe(p => {
      this.page.set(p);
      this.running.set(p.content.some(r => r.status === 'STARTED'));
      if (this.running()) {
        this.timer = setTimeout(() => this.load(this.currentPage), 2000);
      }
    });
  }

  protected upload(file: File): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.uploadStatement(file).subscribe({
      next: r => {
        this.busy.set(false);
        this.uploader()?.reset();
        this.snack.open(`Extracto recibido. Conciliación #${r.reconciliationId} en curso.`, 'Ver', { duration: 6000 })
          .onAction().subscribe(() => this.router.navigate(['/reconciliations', r.reconciliationId]));
        this.load(0);
      },
      error: e => {
        this.busy.set(false);
        this.error.set(problemMessage(e));
      },
    });
  }
}
