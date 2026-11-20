import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { statusIcon, statusLabel, statusTone } from './labels';

@Component({
  selector: 'app-status',
  imports: [MatIconModule],
  template: `<span [class]="'badge tone-' + tone()" [class.spinning]="value() === 'STARTED'">
    <mat-icon>{{ icon() }}</mat-icon>{{ label() }}</span>`,
})
export class StatusBadge {
  readonly value = input.required<string>();
  protected readonly label = computed(() => statusLabel(this.value()));
  protected readonly tone = computed(() => statusTone(this.value()));
  protected readonly icon = computed(() => statusIcon(this.value()));
}
