import { HttpErrorResponse } from '@angular/common/http';

export interface ApiError {
  status: number;
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  transferId?: string;
}

interface ProblemBody {
  code?: string;
  detail?: string;
  transferId?: string;
  errors?: { field: string; message: string }[];
}

/** Turns any HTTP failure into a message a person can act on. */
export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, code: 'UNKNOWN', message: 'Something went wrong. Try again.', fieldErrors: {} };
  }
  if (error.status === 0) {
    return {
      status: 0,
      code: 'NETWORK',
      message: 'The banking service could not be reached. Check that the backend is running, then try again.',
      fieldErrors: {},
    };
  }
  const body = (typeof error.error === 'object' && error.error !== null ? error.error : {}) as ProblemBody;
  const fieldErrors: Record<string, string> = {};
  for (const fieldError of body.errors ?? []) {
    fieldErrors[fieldError.field] = fieldError.message;
  }
  return {
    status: error.status,
    code: body.code ?? `HTTP_${error.status}`,
    message: body.detail ?? fallbackMessage(error.status),
    fieldErrors,
    transferId: body.transferId,
  };
}

function fallbackMessage(status: number): string {
  if (status === 401) return 'Your session has ended. Log in again.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 404) return 'That item could not be found.';
  if (status >= 500) return 'The banking service hit an unexpected error. No changes were saved.';
  return 'The request could not be completed.';
}
