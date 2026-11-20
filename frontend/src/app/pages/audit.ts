import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuditEntry, Page } from '../core/api.models';
import { ApiService, problemMessage } from '../core/api.service';

@Component({
  selector: 'app-audit',
  imports: [DatePipe, FormsModule, MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTableModule, MatPaginatorModule, MatTooltipModule],
  template: `
    <header class="page-head">
      <div>
        <h1>Auditoría</h1>
        <p class="muted">Rastro inmutable: quién hizo qué, cuándo y con qué identificador de correlación.</p>
      </div>
    </header>
    <mat-card class="panel">
      <form class="filters" (ngSubmit)="search(0)">
        <mat-form-field>
          <mat-label>Entidad</mat-label>
          <mat-select name="entityType" [(ngModel)]="filter.entityType">
            <mat-option value="">Todas</mat-option>
            @for (t of entityTypes; track t) { <mat-option [value]="t">{{ t }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Id de entidad</mat-label>
          <input matInput name="entityId" [(ngModel)]="filter.entityId" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Usuario</mat-label>
          <mat-icon matPrefix>person</mat-icon>
          <input matInput name="actor" [(ngModel)]="filter.actor" />
        </mat-form-field>
        <button matButton="tonal"><mat-icon>filter_list</mat-icon> Buscar</button>
      </form>
      @if (error()) {
        <div class="alert bad"><mat-icon>error</mat-icon>{{ error() }}</div>
      }
      <div class="table-wrap">
        <table mat-table [dataSource]="page()?.content ?? []">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Fecha</th>
            <td mat-cell *matCellDef="let a" class="small nowrap">{{ a.occurredAt | date: 'medium' }}</td>
          </ng-container>
          <ng-container matColumnDef="entity">
            <th mat-header-cell *matHeaderCellDef>Entidad</th>
            <td mat-cell *matCellDef="let a"><span class="muted small">{{ a.entityType }}</span><br /><span class="mono small">{{ a.entityId }}</span></td>
          </ng-container>
          <ng-container matColumnDef="action">
            <th mat-header-cell *matHeaderCellDef>Acción</th>
            <td mat-cell *matCellDef="let a"><span class="tag">{{ a.action }}</span></td>
          </ng-container>
          <ng-container matColumnDef="actor">
            <th mat-header-cell *matHeaderCellDef>Usuario</th>
            <td mat-cell *matCellDef="let a">{{ a.actor }}</td>
          </ng-container>
          <ng-container matColumnDef="details">
            <th mat-header-cell *matHeaderCellDef>Detalle</th>
            <td mat-cell *matCellDef="let a" class="mono small truncate" [matTooltip]="a.details ?? ''">{{ a.details }}</td>
          </ng-container>
          <ng-container matColumnDef="correlation">
            <th mat-header-cell *matHeaderCellDef>Correlación</th>
            <td mat-cell *matCellDef="let a" class="mono small muted" [matTooltip]="a.correlationId ?? ''">{{ a.correlationId?.slice(0, 8) }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-mdc-row" *matNoDataRow><td class="empty-row" [attr.colspan]="columns.length">Sin registros</td></tr>
        </table>
      </div>
      @if (page(); as p) {
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                       [showFirstLastButtons]="true" (page)="onPage($event)" />
      }
    </mat-card>
  `,
})
export class Audit {
  private readonly api = inject(ApiService);
  protected readonly entityTypes = ['TRANSACTION', 'BATCH_FILE', 'BATCH_EXECUTION', 'RECONCILIATION'];
  protected readonly columns = ['date', 'entity', 'action', 'actor', 'details', 'correlation'];
  protected filter = { entityType: '', entityId: '', actor: '' };
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.search(0);
  }

  protected onPage(event: PageEvent): void {
    this.search(event.pageIndex);
  }

  protected search(pageNumber: number): void {
    this.api.audit(this.filter, pageNumber).subscribe({
      next: p => {
        this.page.set(p);
        this.error.set(null);
      },
      error: e => this.error.set(problemMessage(e)),
    });
  }
}
