import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Account, Transfer } from '../../core/models';
import { TransferComponent } from './transfer.component';

const ACCOUNTS: Account[] = [
  { reference: 'SB-EUR-0001', displayName: 'Alice Everyday', currency: 'EUR', balance: 5000, status: 'ACTIVE' },
  { reference: 'SB-EUR-0002', displayName: 'Alice Savings', currency: 'EUR', balance: 12000, status: 'ACTIVE' },
  { reference: 'SB-EUR-0009', displayName: 'Frozen', currency: 'EUR', balance: 10, status: 'FROZEN' },
];

const COMPLETED: Transfer = {
  id: 't-1', sourceAccountReference: 'SB-EUR-0002', destinationAccountReference: 'SB-EUR-0001', amount: 2500,
  currency: 'EUR', status: 'COMPLETED', rejectionReason: null, reference: null,
  createdAt: '2026-01-01T10:00:00Z', completedAt: '2026-01-01T10:00:00Z',
  alerts: [{ id: 'a-1', ruleCode: 'HIGH_VALUE_TRANSFER', severity: 'MEDIUM', reviewStatus: 'OPEN',
    explanation: 'Transfer amount 2,500.00 EUR is above the high-value threshold of 2,000.00 EUR.' }],
};

describe('TransferComponent', () => {
  let fixture: ComponentFixture<TransferComponent>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [TransferComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TransferComponent);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/accounts').flush(ACCOUNTS);
    await settle();
  });

  afterEach(() => http.verify());

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function setValue(selector: string, value: string): void {
    const element = page.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
    element.value = value;
    element.dispatchEvent(new Event(element instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  async function fill(amount: string): Promise<void> {
    setValue('#source', 'SB-EUR-0002');
    setValue('#destination', ' sb-eur-0001 ');
    setValue('#amount', amount);
    await settle();
  }

  async function submit(): Promise<void> {
    page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await settle();
  }

  function expectTransferPost(): TestRequest {
    return http.expectOne((req) => req.method === 'POST' && req.url === '/api/transfers');
  }

  it('offers only active accounts as the source and shows the available balance', async () => {
    const options = [...page.querySelectorAll('#source option')].map((o) => o.textContent?.trim());
    expect(options).toEqual(['Choose an account', 'Alice Everyday (SB-EUR-0001)', 'Alice Savings (SB-EUR-0002)']);
    setValue('#source', 'SB-EUR-0001');
    await settle();
    expect(page.textContent).toContain('Available: 5,000.00 EUR');
  });

  it('blocks amounts with more than two decimals before calling the API', async () => {
    await fill('10.123');
    await submit();
    http.expectNone('/api/transfers');
    expect(page.textContent).toContain('Use a number with up to two decimal places');
  });

  it('blocks sending to the same account', async () => {
    setValue('#source', 'SB-EUR-0001');
    setValue('#destination', 'SB-EUR-0001');
    setValue('#amount', '5');
    await settle();
    await submit();
    http.expectNone('/api/transfers');
    expect(page.textContent).toContain('Choose a different account');
  });

  it('sends a normalised request with an idempotency key and shows the alert explanation', async () => {
    await fill('2500');
    await submit();

    const request = expectTransferPost();
    expect(request.request.body).toEqual({
      sourceAccountReference: 'SB-EUR-0002',
      destinationAccountReference: 'SB-EUR-0001',
      amount: 2500,
      currency: 'EUR',
      reference: null,
    });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^web-[0-9a-f-]{36}$/);
    request.flush(COMPLETED, { status: 201, statusText: 'Created' });
    http.expectOne('/api/accounts').flush(ACCOUNTS);
    await settle();

    expect(page.textContent).toContain('Sent 2,500.00 EUR to SB-EUR-0001');
    expect(page.textContent).toContain('Flagged for review');
    expect(page.textContent).toContain('above the high-value threshold of 2,000.00 EUR');
  });

  it('reuses the same idempotency key when retrying after a network failure', async () => {
    await fill('25.50');
    await submit();
    const first = expectTransferPost();
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    await settle();
    expect(page.textContent).toContain('Sending again is safe');

    await submit();
    const retry = expectTransferPost();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ ...COMPLETED, amount: 25.5, alerts: [] }, { status: 201, statusText: 'Created' });
    http.expectOne('/api/accounts').flush(ACCOUNTS);
  });

  it('uses a new idempotency key once the request changes', async () => {
    await fill('25.50');
    await submit();
    const first = expectTransferPost();
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    await settle();

    setValue('#amount', '26.00');
    await settle();
    await submit();
    const changed = expectTransferPost();
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(key);
    changed.flush({ ...COMPLETED, alerts: [] }, { status: 201, statusText: 'Created' });
    http.expectOne('/api/accounts').flush(ACCOUNTS);
  });

  it('shows the backend reason when a transfer is rejected', async () => {
    await fill('99999');
    await submit();
    expectTransferPost().flush(
      { code: 'INSUFFICIENT_FUNDS', detail: 'The source account does not have enough funds for this transfer.', transferId: 't-9' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    http.expectOne('/api/accounts').flush(ACCOUNTS);
    await settle();

    expect(page.textContent).toContain('Transfer rejected');
    expect(page.textContent).toContain('does not have enough funds');
    expect(page.querySelector('a[href="/transfers/t-9"]')).not.toBeNull();
  });
});
