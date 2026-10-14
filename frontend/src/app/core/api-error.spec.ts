import { HttpErrorResponse } from '@angular/common/http';
import { toApiError } from './api-error';

describe('toApiError', () => {
  it('reads the code, detail, field errors and transfer id from problem details', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        code: 'VALIDATION_FAILED',
        detail: 'The request contains invalid fields.',
        errors: [{ field: 'amount', message: 'must have at most 2 decimal places' }],
        transferId: 'abc',
      },
    });

    expect(toApiError(error)).toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'The request contains invalid fields.',
      fieldErrors: { amount: 'must have at most 2 decimal places' },
      transferId: 'abc',
    });
  });

  it('explains network failures in actionable terms', () => {
    const result = toApiError(new HttpErrorResponse({ status: 0 }));
    expect(result.code).toBe('NETWORK');
    expect(result.message).toContain('could not be reached');
  });

  it('falls back to a status-specific message when the body is not problem details', () => {
    expect(toApiError(new HttpErrorResponse({ status: 503, error: 'oops' })).message)
      .toBe('The banking service hit an unexpected error. No changes were saved.');
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN');
  });
});
