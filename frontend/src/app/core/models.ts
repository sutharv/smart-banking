// TypeScript mirrors of the backend DTOs. The backend stays the authority on all values.

export type Role = 'CUSTOMER' | 'ANALYST';
export type Currency = 'EUR' | 'PLN' | 'USD';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type TransferStatus = 'PENDING' | 'COMPLETED' | 'REJECTED';
export type RejectionReason =
  | 'INSUFFICIENT_FUNDS'
  | 'SOURCE_ACCOUNT_INACTIVE'
  | 'DESTINATION_ACCOUNT_INACTIVE'
  | 'CURRENCY_MISMATCH';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';
export type ReviewStatus = 'OPEN' | 'CONFIRMED' | 'DISMISSED';

/** Money arrives as a JSON number with at most two decimals; it is only displayed, never computed with. */
export type Amount = number;

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  username: string;
  role: Role;
}

export interface Account {
  reference: string;
  displayName: string;
  currency: Currency;
  balance: Amount;
  status: AccountStatus;
}

export interface Transaction {
  transferId: string;
  direction: 'INCOMING' | 'OUTGOING';
  counterpartyReference: string;
  amount: Amount;
  currency: Currency;
  status: TransferStatus;
  rejectionReason: RejectionReason | null;
  reference: string | null;
  createdAt: string;
  flagged: boolean;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TransferRequest {
  sourceAccountReference: string;
  destinationAccountReference: string;
  /** Sent as a decimal string-compatible number with at most two decimals. */
  amount: number;
  currency: Currency;
  reference: string | null;
}

export interface FraudAlertSummary {
  id: string;
  ruleCode: string;
  severity: Severity;
  explanation: string;
  reviewStatus: ReviewStatus;
}

export interface Transfer {
  id: string;
  sourceAccountReference: string;
  destinationAccountReference: string;
  amount: Amount;
  currency: Currency;
  status: TransferStatus;
  rejectionReason: RejectionReason | null;
  reference: string | null;
  createdAt: string;
  completedAt: string | null;
  alerts: FraudAlertSummary[];
}

export interface FraudAlert {
  id: string;
  transferId: string;
  sourceAccountReference: string;
  destinationAccountReference: string;
  amount: Amount;
  currency: Currency;
  ruleCode: string;
  severity: Severity;
  explanation: string;
  reviewStatus: ReviewStatus;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export interface AlertFilter {
  status?: ReviewStatus | '';
  severity?: Severity | '';
}

export const RULE_NAMES: Record<string, string> = {
  HIGH_VALUE_TRANSFER: 'High-value transfer',
  RAPID_REPEATED_TRANSFERS: 'Repeated transfers',
  HIGH_VELOCITY_AMOUNT: 'High total in a short time',
};

export const REJECTION_TEXT: Record<RejectionReason, string> = {
  INSUFFICIENT_FUNDS: 'Not enough funds',
  SOURCE_ACCOUNT_INACTIVE: 'Sending account inactive',
  DESTINATION_ACCOUNT_INACTIVE: 'Receiving account inactive',
  CURRENCY_MISMATCH: 'Currencies differ',
};
