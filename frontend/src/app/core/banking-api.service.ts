import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Account, AlertFilter, FraudAlert, Page, ReviewStatus, Transaction, Transfer, TransferRequest } from './models';

/** Typed access to the REST API. */
@Injectable({ providedIn: 'root' })
export class BankingApiService {
  private readonly http = inject(HttpClient);

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  account(reference: string): Observable<Account> {
    return this.http.get<Account>(`/api/accounts/${encodeURIComponent(reference)}`);
  }

  transactions(reference: string, page = 0, size = 20): Observable<Page<Transaction>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Transaction>>(`/api/accounts/${encodeURIComponent(reference)}/transactions`, { params });
  }

  /** The idempotency key makes a retried submission safe: the backend returns the original result. */
  createTransfer(request: TransferRequest, idempotencyKey: string): Observable<HttpResponse<Transfer>> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<Transfer>('/api/transfers', request, { headers, observe: 'response' });
  }

  transfer(id: string): Observable<Transfer> {
    return this.http.get<Transfer>(`/api/transfers/${encodeURIComponent(id)}`);
  }

  alerts(filter: AlertFilter, page = 0, size = 20): Observable<Page<FraudAlert>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.severity) params = params.set('severity', filter.severity);
    return this.http.get<Page<FraudAlert>>('/api/fraud-alerts', { params });
  }

  reviewAlert(id: string, decision: Exclude<ReviewStatus, 'OPEN'>, note: string | null): Observable<FraudAlert> {
    return this.http.patch<FraudAlert>(`/api/fraud-alerts/${encodeURIComponent(id)}/review`, { decision, note });
  }
}
