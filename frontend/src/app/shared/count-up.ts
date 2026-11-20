import { Directive, ElementRef, OnDestroy, effect, inject, input } from '@angular/core';

export type CountFormat = 'number' | 'currency' | 'percent' | 'seconds';

const FORMATTERS: Record<CountFormat, (v: number) => string> = {
  number: v => new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 }).format(v),
  // Montos grandes en millones ("$1.461,1 M") para que quepan en la tarjeta; el valor completo va en el title.
  currency: v => Math.abs(v) >= 1e7
    ? '$' + new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(v / 1e6) + ' M'
    : new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', currencyDisplay: 'narrowSymbol', maximumFractionDigits: 0 }).format(v),
  percent: v => new Intl.NumberFormat('es-CO', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(v),
  seconds: v => new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(v / 1000) + ' s',
};

/**
 * Anima un indicador desde su valor anterior hasta el nuevo (easing suave, ~0,9 s).
 * Respeta `prefers-reduced-motion`: en ese caso muestra el valor final directamente.
 *
 *   <span [appCountUp]="total" format="currency"></span>
 */
@Directive({ selector: '[appCountUp]' })
export class CountUp implements OnDestroy {
  readonly value = input.required<number | null | undefined>({ alias: 'appCountUp' });
  readonly format = input<CountFormat>('number');

  private readonly el = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private current = 0;
  private frame = 0;

  constructor() {
    effect(() => {
      const value = this.value() ?? 0;
      if (this.format() === 'currency') {
        this.el.title = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', currencyDisplay: 'narrowSymbol' }).format(value);
      }
      this.animateTo(value, FORMATTERS[this.format()]);
    });
  }

  private animateTo(target: number, fmt: (v: number) => string): void {
    cancelAnimationFrame(this.frame);
    const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    const from = this.current;
    if (reduce || from === target) {
      this.current = target;
      this.el.textContent = fmt(target);
      return;
    }
    const start = performance.now();
    const duration = 900;
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      this.current = from + (target - from) * eased;
      this.el.textContent = fmt(this.current);
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      } else {
        this.current = target;
      }
    };
    this.frame = requestAnimationFrame(step);
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frame);
  }
}
