import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FraudAlert, Page } from '../../core/models';
import { AlertsComponent } from './alerts.component';

const ALERT: FraudAlert = {
  id: 'a-1', transferId: 't-1', sourceAccountReference: 'SB-EUR-0002', destinationAccountReference: 'SB-EUR-0001',
  amount: 2500, currency: 'EUR', ruleCode: 'HIGH_VALUE_TRANSFER', severity: 'MEDIUM',
  explanation: 'Transfer amount 2,500.00 EUR is above the high-value threshold of 2,000.00 EUR.',
  reviewStatus: 'OPEN', reviewNote: null, reviewedAt: null, createdAt: '2026-01-01T10:00:00Z',
};

const page = (alerts: FraudAlert[]): Page<FraudAlert> =>
  ({ content: alerts, page: 0, size: 20, totalElements: alerts.length, totalPages: 1 });

async function render(analyst: boolean) {
  TestBed.configureTestingModule({
    imports: [AlertsComponent],
    providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: AuthService, useValue: { isAnalyst: signal(analyst) } },
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AlertsComponent);
  fixture.detectChanges();
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  return { http, fixture, settle, el: fixture.nativeElement as HTMLElement };
}

describe('AlertsComponent', () => {
  it('shows analysts the open alerts with a review action, and records a decision', async () => {
    const { http, el, settle } = await render(true);
    const list = http.expectOne((req) => req.url === '/api/fraud-alerts');
    expect(list.request.params.get('status')).toBe('OPEN');
    list.flush(page([ALERT]));
    await settle();

    expect(el.textContent).toContain('High-value transfer');
    expect(el.textContent).toContain('above the high-value threshold');
    (el.querySelector('td button') as HTMLButtonElement).click();
    await settle();
    const confirm = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Confirm suspicious'))!;
    confirm.click();

    const review = http.expectOne('/api/fraud-alerts/a-1/review');
    expect(review.request.method).toBe('PATCH');
    expect(review.request.body).toEqual({ decision: 'CONFIRMED', note: null });
    review.flush({ ...ALERT, reviewStatus: 'CONFIRMED', reviewedAt: '2026-01-01T11:00:00Z' });
    await settle();

    expect(el.textContent).toContain('Confirmed');
    expect(el.textContent).not.toContain('Confirm suspicious');
    http.verify();
  });

  it('shows customers their alerts read-only', async () => {
    const { http, el, settle } = await render(false);
    const list = http.expectOne((req) => req.url === '/api/fraud-alerts');
    expect(list.request.params.has('status')).toBe(false);
    list.flush(page([ALERT]));
    await settle();

    expect(el.textContent).toContain('Alerts on your transfers');
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Review')).toBe(false);
    http.verify();
  });
});
