import { Component, input } from '@angular/core';

/**
 * Marca de SmartOps en SVG (nítida a cualquier tamaño): una "S" hecha de dos flujos que se encuentran
 * y un punto ocre de "conciliado". Colores de la sala: turquesa del sofá, índigo de la pared y ocre de las hojas.
 */
@Component({
  selector: 'app-logo',
  host: { class: 'logo', '[class.logo--light]': 'light()' },
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 48 48" role="img" aria-label="SmartOps">
      <defs>
        <linearGradient id="so-logo-bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#3aa0ad" />
          <stop offset="1" stop-color="#1f6f7b" />
        </linearGradient>
      </defs>
      <rect width="48" height="48" rx="14" fill="url(#so-logo-bg)" />
      <path class="logo-s" d="M31 15.5H21.5a5.25 5.25 0 0 0 0 10.5h5a5.25 5.25 0 0 1 0 10.5H16"
            fill="none" stroke="#fff" stroke-width="4.6" stroke-linecap="round" stroke-linejoin="round" />
      <circle class="logo-dot" cx="34.5" cy="15.5" r="3.4" fill="#f2c14e" />
      <circle cx="13" cy="36.5" r="2.2" fill="#fbe0d2" />
    </svg>
    @if (wordmark()) {
      <span class="logo-text">
        <span class="logo-name">Smart<b>Ops</b></span>
        @if (tagline()) { <span class="logo-tagline">{{ tagline() }}</span> }
      </span>
    }
  `,
})
export class Logo {
  readonly size = input(40);
  readonly wordmark = input(true);
  readonly tagline = input('');
  readonly light = input(false);
}
