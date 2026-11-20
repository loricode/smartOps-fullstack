import { DecimalPipe } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Zona de arrastre para archivos CSV. */
@Component({
  selector: 'app-file-upload',
  imports: [DecimalPipe, MatButtonModule, MatIconModule],
  template: `
    <label class="dropzone" [class.over]="over()" [class.disabled]="busy()"
           (dragover)="$event.preventDefault(); over.set(true)" (dragleave)="over.set(false)"
           (drop)="onDrop($event)">
      <input type="file" accept=".csv,text/csv" (change)="onPick($event)" [disabled]="busy()" />
      <span class="dropzone-icon"><mat-icon>{{ selected() ? 'description' : 'upload_file' }}</mat-icon></span>
      <span>
        @if (busy()) {
          Enviando…
        } @else if (selected(); as f) {
          <strong>{{ f.name }}</strong> · {{ f.size / 1024 | number: '1.0-1' }} KB<br />
          <span class="muted small">Suelte otro archivo para reemplazarlo</span>
        } @else {
          <strong>{{ title() }}</strong><br />
          <span class="muted">Arrastre un archivo .csv o haga clic para seleccionarlo</span>
        }
      </span>
    </label>
    <div class="upload-actions">
      <span class="muted">{{ hint() }}</span>
      <button matButton="filled" [disabled]="!selected() || busy()" (click)="send()">
        <mat-icon>play_arrow</mat-icon> {{ actionLabel() }}
      </button>
    </div>
  `,
})
export class FileUpload {
  readonly title = input('Seleccione un archivo');
  readonly hint = input('');
  readonly actionLabel = input('Procesar');
  readonly busy = input(false);
  readonly submitted = output<File>();

  protected readonly over = signal(false);
  protected readonly selected = signal<File | null>(null);

  protected onPick(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.selected.set(file);
    }
    (event.target as HTMLInputElement).value = '';
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.over.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.selected.set(file);
    }
  }

  protected send(): void {
    const file = this.selected();
    if (file) {
      this.submitted.emit(file);
    }
  }

  reset(): void {
    this.selected.set(null);
  }
}
