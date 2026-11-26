import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { OrdersService, OrderItem } from '../../services/orders.service';
import { PaymentsService } from '../../services/payments.service';
import { CarritoService } from '../../services/carrito.service';

interface PendingOrder {
  paymentId: string;
  order: { clienteEmail: string; total: number; items: OrderItem[] };
}

@Component({
  selector: 'app-payment-return',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main style="max-width: 680px; margin: 10vh auto; padding: 2rem; font-family: sans-serif">
      <h1>Resultado de pago</h1>
      <p *ngIf="loading">Confirmando la transaccion con Webpay...</p>
      <p *ngIf="message">{{ message }}</p>
      <p *ngIf="orderId">Orden publicada: <strong>{{ orderId }}</strong></p>
      <a routerLink="/dashboard">Volver a la tienda</a>
    </main>
  `,
})
export class PaymentReturnComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private payments = inject(PaymentsService);
  private orders = inject(OrdersService);
  private cart = inject(CarritoService);
  loading = true;
  message = '';
  orderId: string | null = null;

  ngOnInit(): void {
    const paymentId = this.route.snapshot.paramMap.get('paymentId');
    const token = new URLSearchParams(this.route.snapshot.fragment || '').get('token_ws');
    const raw = sessionStorage.getItem('pedidos360_pending_order');
    if (!paymentId || !token || !raw) {
      this.message = 'Pago cancelado o faltan datos de la compra. Revisa el estado antes de intentarlo otra vez.';
      this.loading = false;
      return;
    }
    let pending: PendingOrder;
    try {
      pending = JSON.parse(raw) as PendingOrder;
      if (pending.paymentId !== paymentId || !pending.order?.items?.length) throw new Error('Orden invalida');
    } catch {
      this.message = 'No se pudo recuperar la compra de esta pestana.';
      this.loading = false;
      return;
    }
    this.payments.commit(paymentId, token).subscribe({
      next: (payment) => {
        if (payment.status !== 'AUTHORIZED' || payment.amount !== pending.order.total) {
          this.message = 'Webpay no autorizo el pago. No se publico ninguna orden.';
          this.loading = false;
          return;
        }
        this.orders.crearOrden({ ...pending.order, paymentId }).subscribe({
          next: (order) => {
            this.orderId = order.orderId;
            this.message = 'Pago aprobado y orden enviada a RabbitMQ.';
            sessionStorage.removeItem('pedidos360_pending_order');
            this.cart.vaciarCarrito().subscribe({
              error: () => { this.message += ' El carrito no se pudo vaciar; no repitas el pago.'; },
            });
            this.loading = false;
          },
          error: () => {
            this.message = 'Pago aprobado, pero no se pudo confirmar la orden. No repitas el pago; revisa el estado de la orden.';
            this.loading = false;
          },
        });
      },
      error: () => {
        this.message = 'No se pudo confirmar el pago con Webpay. No repitas el pago hasta verificar su estado.';
        this.loading = false;
      },
    });
  }
}
