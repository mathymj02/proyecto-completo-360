import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface StartPaymentResult {
  paymentId: string;
  status: string;
  url: string | null;
  token: string | null;
}

export interface PaymentStatus {
  paymentId: string;
  status: string;
  amount: number;
}

@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly url = environment.apiUrls.auth.replace(/\/auth$/, '/payments');

  constructor(private http: HttpClient) {}

  start(amount: number, cardAlias: string): Observable<StartPaymentResult> {
    return this.http.post<StartPaymentResult>(this.url, { amount, cardAlias });
  }

  commit(paymentId: string, token: string): Observable<PaymentStatus> {
    return this.http.post<PaymentStatus>(`${this.url}/${encodeURIComponent(paymentId)}/commit`, { token });
  }
}
