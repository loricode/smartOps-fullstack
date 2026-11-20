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
import { BatchExecution, Page } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { FileUpload } from '../shared/file-upload';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-batch',
  imports: [DatePipe, DecimalPipe, StatusBadge, FileUpload, MatCardModule, MatButtonModule, MatIconModule,
    MatTableModule, MatPaginatorModule, MatProgressBarModule],
  template: `
    <header class="page-head">
      <div>
        <h1>Procesamiento masivo</h1>
        <p class="muted">Carga de archivos de operaciones con Spring Batch: validación por chunks, control de
          duplicados y contabilización.</p>
      </div>
    </header>

    @if (canUpload) {
      <mat-card class="panel">
        <h2><span class="h-icon teal"><mat-icon>upload_file</mat-icon></span>Cargar archivo de operaciones</h2>
        <app-file-upload title="Archivo de operaciones (.csv)" actionLabel="Procesar lote" [busy]="busy()"
                         hint="reference,type,source_account,target_account,amount,currency,value_date,description"
                         (submitted)="upload($event)" />
        @if (error()) {
          <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
        }
      </mat-card>
    }

    <mat-card class="panel">
      <div class="card-head">
        <h2><span class="h-icon indigo"><mat-icon>history</mat-icon></span>Ejecuciones</h2>
        <button matButton (click)="load(currentPage)"><mat-icon>refresh</mat-icon> Actualizar</button>
      </div>
      @if (running()) { <mat-progress-bar mode="indeterminate" /> }
      <div class="table-wrap">
        <table mat-table [dataSource]="page()?.content ?? []">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let e"><strong>#{{ e.id }}</strong></td>
          </ng-container>
          <ng-container matColumnDef="file">
            <th mat-header-cell *matHeaderCellDef>Archivo</th>
            <td mat-cell *matCellDef="let e">{{ e.fileName }}<div class="muted small">{{ e.startedBy }}</div></td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let e"><app-status [value]="e.status" /></td>
          </ng-container>
          <ng-container matColumnDef="read">
            <th mat-header-cell *matHeaderCellDef class="num">Leídas</th>
            <td mat-cell *matCellDef="let e" class="num">{{ e.readCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="processed">
            <th mat-header-cell *matHeaderCellDef class="num">Procesadas</th>
            <td mat-cell *matCellDef="let e" class="num tone-text-ok">{{ e.processedCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="rejected">
            <th mat-header-cell *matHeaderCellDef class="num">Rechazadas</th>
            <td mat-cell *matCellDef="let e" class="num tone-text-bad">{{ e.rejectedCount | number }}</td>
          </ng-container>
          <ng-container matColumnDef="duration">
            <th mat-header-cell *matHeaderCellDef class="num">Duración</th>
            <td mat-cell *matCellDef="let e" class="num">{{ e.durationMs != null ? (e.durationMs / 1000 | number: '1.1-1') + ' s' : '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="started">
            <th mat-header-cell *matHeaderCellDef>Inicio</th>
            <td mat-cell *matCellDef="let e" class="muted small nowrap">{{ e.startedAt | date: 'short' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
          <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Aún no se han cargado archivos</td></tr>
        </table>
      </div>
      @if (page(); as p) {
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                       (page)="onPage($event)" />
      }
    </mat-card>
  `,
})
export class Batch {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly canUpload = inject(AuthService).hasRole('OPERATOR');
  private readonly uploader = viewChild(FileUpload);

  protected readonly columns = ['id', 'file', 'status', 'read', 'processed', 'rejected', 'duration', 'started'];
  protected readonly page = signal<Page<BatchExecution> | null>(null);
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
    this.api.executions(pageNumber).subscribe(p => {
      this.page.set(p);
      // Mientras haya lotes en curso, refresca automáticamente.
      this.running.set(p.content.some(e => e.status === 'STARTED'));
      if (this.running()) {
        this.timer = setTimeout(() => this.load(this.currentPage), 2000);
      }
    });
  }

  protected upload(file: File): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.uploadOperations(file).subscribe({
      next: r => {
        this.busy.set(false);
        this.uploader()?.reset();
        this.snack.open(`Archivo recibido. Ejecución #${r.executionId} en curso.`, 'Ver', { duration: 6000 })
          .onAction().subscribe(() => this.router.navigate(['/batch', r.executionId]));
        this.load(0);
      },
      error: e => {
        this.busy.set(false);
        this.error.set(problemMessage(e));
      },
    });
  }

  protected open(e: BatchExecution): void {
    this.router.navigate(['/batch', e.id]);
  }
}
