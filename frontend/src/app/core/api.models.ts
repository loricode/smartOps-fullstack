// Contratos del API SmartOps (espejo de los DTO / read models del backend).

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export type TransactionStatus =
  | 'RECEIVED' | 'VALIDATED' | 'PROCESSING' | 'PROCESSED' | 'REJECTED' | 'RECONCILED';
export type TransactionType = 'TRANSFER' | 'PAYMENT' | 'COLLECTION';
export type RunStatus = 'STARTED' | 'COMPLETED' | 'FAILED';
export type ReconciliationResult = 'MATCHED' | 'AMOUNT_MISMATCH' | 'MISSING_EXTERNAL' | 'MISSING_INTERNAL';

export interface Transaction {
  id: string;
  reference: string;
  type: TransactionType;
  sourceAccount?: string;
  targetAccount?: string;
  amount: number;
  currency: string;
  valueDate: string;
  description?: string;
  status: TransactionStatus;
  channel: 'API' | 'BATCH';
  batchFileId?: number;
  rejectionReason?: string;
  createdBy: string;
  createdAt: string;
  processedAt?: string;
}

export interface CreateTransaction {
  type: TransactionType;
  sourceAccount?: string | null;
  targetAccount?: string | null;
  amount: number;
  currency: string;
  valueDate?: string | null;
  description?: string | null;
}

export interface TransactionFilter {
  status?: string;
  type?: string;
  channel?: string;
  reference?: string;
  batchFileId?: number;
}

export interface BatchExecution {
  id: number;
  batchFileId: number;
  fileName: string;
  jobName: string;
  status: RunStatus;
  readCount: number;
  acceptedCount: number;
  rejectedCount: number;
  processedCount: number;
  startedAt: string;
  finishedAt?: string;
  durationMs?: number;
  exitMessage?: string;
  startedBy: string;
}

export interface RejectedRecord {
  id: number;
  lineNumber: number;
  reference?: string;
  reasonCode: string;
  reasonDetail: string;
  rawLine?: string;
}

export interface RejectionCount {
  reasonCode: string;
  total: number;
}

export interface Reconciliation {
  id: number;
  statementFileId: number;
  fileName: string;
  valueDateFrom?: string;
  valueDateTo?: string;
  status: RunStatus;
  totalInternal: number;
  totalExternal: number;
  matchedCount: number;
  amountMismatchCount: number;
  missingExternalCount: number;
  missingInternalCount: number;
  startedAt: string;
  finishedAt?: string;
  exitMessage?: string;
  startedBy: string;
}

export interface ReconciliationDetail {
  id: number;
  reference: string;
  result: ReconciliationResult;
  internalAmount?: number;
  externalAmount?: number;
  difference?: number;
  transactionId?: string;
}

export interface AuditEntry {
  id: number;
  entityType: string;
  entityId: string;
  action: string;
  actor: string;
  correlationId?: string;
  details?: string;
  occurredAt: string;
}

export interface Dashboard {
  totalTransactions: number;
  processedAmount: number;
  byStatus: { status: TransactionStatus; total: number; amount: number }[];
  batchExecutions: number;
  rejectedRecords: number;
  reconciliations: number;
  reconciliationMatchRate: number;
}

export interface Account {
  accountNumber: string;
  holderName: string;
  currency: string;
  balance: number;
  status: 'ACTIVE' | 'BLOCKED';
}

export interface Session {
  username: string;
  roles: string[];
}

/** Error RFC 7807 devuelto por el backend. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: (string | { field: string; message: string })[];
  existingFileId?: number;
}
