import { Component, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  constructor() {
    // Íconos Material Symbols (variante redondeada) para todos los <mat-icon>.
    inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-rounded');
  }
}
