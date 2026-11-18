import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface OrderItem {
  productoId: number;
  nombreProducto: string;
  cantidad: number;
  precioUnitario: number;
}

export interface OrderEvent {
  orderId: string;
  paymentId?: string;
  clienteEmail: string;
  total: number;
  items: OrderItem[];
  fecha: string;
}

@Injectable({ providedIn: 'root' })
export class OrdersService {
  constructor(private http: HttpClient) {}

  crearOrden(order: Pick<OrderEvent, 'clienteEmail' | 'total' | 'items'> & { paymentId?: string }): Observable<OrderEvent> {
    const ordersUrl = environment.apiUrls.auth.replace(/\/auth$/, '/orders');
    return this.http.post<OrderEvent>(ordersUrl, order);
  }
}
