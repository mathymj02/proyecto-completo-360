import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Recommendation {
  productId: number;
  name: string;
  category: string;
  views: number;
}

export interface MetricsSnapshot {
  productViews: number;
  cartAdds: number;
  paymentAttempts: number;
  fraudAlerts: number;
  topProducts: Recommendation[];
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly url = environment.apiUrls.auth.replace(/\/auth$/, '/analytics');

  constructor(private http: HttpClient) {}

  recommendations(): Observable<Recommendation[]> {
    return this.http.get<Recommendation[]>(`${this.url}/recommendations/me`);
  }

  metrics(): Observable<MetricsSnapshot> {
    return this.http.get<MetricsSnapshot>(`${this.url}/metrics`);
  }

  connect(onMetrics: (snapshot: MetricsSnapshot) => void): WebSocket {
    const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = environment.analyticsWsUrl || `${scheme}//${location.host}/ws/metrics`;
    const socket = new WebSocket(url);
    socket.onmessage = (event) => {
      try {
        onMetrics(JSON.parse(event.data) as MetricsSnapshot);
      } catch (error) {
        console.warn('Metrica WebSocket invalida', error);
      }
    };
    return socket;
  }
}
