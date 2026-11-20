// Etiquetas de negocio en español y tono visual de cada estado.

export type Tone = 'ok' | 'warn' | 'bad' | 'info' | 'neutral';

const STATUS: Record<string, { label: string; tone: Tone; icon: string }> = {
  RECEIVED: { label: 'Recibida', tone: 'neutral', icon: 'inbox' },
  VALIDATED: { label: 'Validada', tone: 'info', icon: 'task_alt' },
  PROCESSING: { label: 'Procesando', tone: 'info', icon: 'sync' },
  PROCESSED: { label: 'Procesada', tone: 'ok', icon: 'check_circle' },
  REJECTED: { label: 'Rechazada', tone: 'bad', icon: 'block' },
  RECONCILED: { label: 'Conciliada', tone: 'ok', icon: 'verified' },
  STARTED: { label: 'En curso', tone: 'info', icon: 'progress_activity' },
  COMPLETED: { label: 'Completado', tone: 'ok', icon: 'check_circle' },
  FAILED: { label: 'Fallido', tone: 'bad', icon: 'error' },
  MATCHED: { label: 'Conciliada', tone: 'ok', icon: 'verified' },
  AMOUNT_MISMATCH: { label: 'Diferencia de monto', tone: 'warn', icon: 'difference' },
  MISSING_EXTERNAL: { label: 'Falta en extracto', tone: 'warn', icon: 'cloud_off' },
  MISSING_INTERNAL: { label: 'Falta en SmartOps', tone: 'bad', icon: 'report' },
  ACTIVE: { label: 'Activa', tone: 'ok', icon: 'check' },
  BLOCKED: { label: 'Bloqueada', tone: 'bad', icon: 'lock' },
};

export const TYPE_LABELS: Record<string, string> = {
  TRANSFER: 'Transferencia',
  PAYMENT: 'Pago',
  COLLECTION: 'Recaudo',
};

export const REASON_LABELS: Record<string, string> = {
  MALFORMED_RECORD: 'Registro mal formado',
  MISSING_FIELD: 'Campo obligatorio vacío',
  INVALID_TYPE: 'Tipo de operación inválido',
  INVALID_AMOUNT: 'Monto inválido',
  AMOUNT_LIMIT_EXCEEDED: 'Supera el límite por operación',
  INVALID_CURRENCY: 'Moneda no soportada',
  INVALID_DATE: 'Fecha inválida',
  VALUE_DATE_OUT_OF_RANGE: 'Fecha valor fuera de rango',
  INVALID_ACCOUNT_COMBINATION: 'Cuentas no válidas para el tipo',
  SAME_ACCOUNT: 'Origen y destino iguales',
  ACCOUNT_NOT_FOUND: 'Cuenta inexistente',
  ACCOUNT_BLOCKED: 'Cuenta bloqueada',
  CURRENCY_MISMATCH: 'Moneda distinta a la cuenta',
  DUPLICATE_REFERENCE: 'Referencia duplicada',
  INSUFFICIENT_FUNDS: 'Saldo insuficiente',
};

export function statusLabel(code: string): string {
  return STATUS[code]?.label ?? code;
}

export function statusTone(code: string): Tone {
  return STATUS[code]?.tone ?? 'neutral';
}

export function statusIcon(code: string): string {
  return STATUS[code]?.icon ?? 'circle';
}

export function reasonLabel(code: string): string {
  return REASON_LABELS[code] ?? code;
}
