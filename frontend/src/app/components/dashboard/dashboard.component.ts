// src/app/components/dashboard/dashboard.component.ts
//
// Esta pantalla solo se puede ver si MsalGuard + roleGuard dejaron pasar
// (ver app.routes.ts). Al llegar aca, YA hay una sesion activa con token -
// por eso los 3 servicios (AuthApiService, CatalogoService, CarritoService)
// se pueden llamar directo, sin pedir el token a mano: MsalInterceptor se
// encarga de pegarlo en cada request saliente.
import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';
import { AuthApiService } from '../../services/auth-api.service';
import { CatalogoService, Producto } from '../../services/catalogo.service';
import { CarritoService } from '../../services/carrito.service';
import { OrdersService } from '../../services/orders.service';
import { PaymentsService } from '../../services/payments.service';
import { AnalyticsService, MetricsSnapshot, Recommendation } from '../../services/analytics.service';
import { decodeJwtPayload, extraerPermisos } from '../../services/jwt.util';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  private msalService = inject(MsalService);
  private authApiService = inject(AuthApiService);
  private catalogoService = inject(CatalogoService);
  private carritoService = inject(CarritoService);
  private ordersService = inject(OrdersService);
  private paymentsService = inject(PaymentsService);
  private analyticsService = inject(AnalyticsService);
  private metricsSocket?: WebSocket;

  // Estado de la pantalla. Angular no tiene un equivalente directo a
  // useState de React - simplemente son propiedades normales de la clase,
  // y el template HTML se re-renderiza solo cuando Angular detecta un
  // cambio (deteccion de cambios automatica, no hay que "setear" nada
  // especial como setState).
  claimsBackend: any = null;
  productos: Producto[] = [];
  carrito: any = null;
  permisosDelToken: string[] = [];
  rolesUsuario: string[] = [];
  usuarioActual: string | null = null;
  cargando = true;
  error: string | null = null;
  mostrarCarrito = false;
  procesandoCompra = false;
  cardAlias = 'tarjetaDemo';
  paymentsEnabled = environment.paymentsEnabled;
  analyticsEnabled = environment.analyticsEnabled;
  metrics: MetricsSnapshot | null = null;
  recommendations: Recommendation[] = [];
  selectedProduct: Producto | null = null;
  ordenConfirmada: any = null;

  get totalItemsCarrito(): number {
    if (!this.carrito || !this.carrito.items) return 0;
    return this.carrito.items.reduce((acc: number, item: any) => acc + item.cantidad, 0);
  }

  ngOnInit(): void {
    const cuenta = this.msalService.instance.getActiveAccount();
    if (cuenta) {
      this.usuarioActual = cuenta.username || cuenta.name || null;
    }
    this.cargarTodo();
    if (this.analyticsEnabled) {
      this.analyticsService.metrics().subscribe({
        next: (snapshot) => (this.metrics = snapshot),
        error: (error) => console.warn('Metricas no disponibles', error),
      });
      this.metricsSocket = this.analyticsService.connect((snapshot) => (this.metrics = snapshot));
    }
  }

  ngOnDestroy(): void {
    this.metricsSocket?.close();
  }

  verDetalle(product: Producto): void {
    if (product.id == null) return;
    this.catalogoService.obtener(product.id).subscribe({
      next: (details) => {
        this.selectedProduct = details;
        if (this.analyticsEnabled) setTimeout(() => this.loadRecommendations(), 800);
      },
      error: (error) => console.warn('No se pudo abrir el producto', error),
    });
  }

  loadRecommendations(): void {
    this.analyticsService.recommendations().subscribe({
      next: (items) => (this.recommendations = items),
      error: (error) => console.warn('Recomendaciones no disponibles', error),
    });
  }

  private cargarTodo(): void {
    this.cargando = true;
    this.error = null;

    // Lee los permisos y roles directo del Access Token
    this.msalService.instance
      .acquireTokenSilent({
        scopes: environment.apiScopes,
        account: this.msalService.instance.getActiveAccount() ?? undefined,
      })
      .then((resultado) => {
        const payload = decodeJwtPayload(resultado.accessToken) as any;
        this.permisosDelToken = extraerPermisos(payload);
        // Si el token tiene roles (App Roles de Entra ID):
        if (payload && payload['roles']) {
          const roles = payload['roles'];
          this.rolesUsuario = Array.isArray(roles) ? roles : [roles];
        } else {
          this.rolesUsuario = ['Cliente'];
        }
      })
      .catch((err) => console.warn('No se pudo leer el token para mostrar permisos:', err));

    // Llama a las APIs
    this.authApiService.obtenerMisClaims().subscribe({
      next: (data) => (this.claimsBackend = data),
      error: (err) => console.error('Error en auth-api:', err),
    });

    this.catalogoService.listar().subscribe({
      next: (data) => {
        this.productos = data;
        this.cargando = false;
      },
      error: (err) => {
        this.error = `Error consultando catalogo-api: ${err.status} ${err.statusText}`;
        this.cargando = false;
      },
    });

    this.recargarCarrito();
  }

  recargarCarrito(): void {
    this.carritoService.obtenerMiCarrito().subscribe({
      next: (data) => (this.carrito = data),
      error: (err) => console.error('Error en carrito-api:', err),
    });
  }

  toggleCarrito(): void {
    this.mostrarCarrito = !this.mostrarCarrito;
  }

  agregarAlCarrito(prod: Producto): void {
    this.carritoService
      .agregarItem({
        productoId: prod.id!,
        nombreProducto: prod.nombre,
        precioUnitario: prod.precio,
        cantidad: 1,
      })
      .subscribe({
        next: (carritoActualizado) => {
          this.carrito = carritoActualizado;
          this.mostrarCarrito = true;
        },
        error: (err) => alert('Error agregando producto: ' + err.message),
      });
  }

  cambiarCantidad(item: any, nuevaCantidad: number): void {
    if (nuevaCantidad <= 0) {
      this.eliminarItem(item.id);
      return;
    }
    this.carritoService.actualizarCantidad(item.id, nuevaCantidad).subscribe({
      next: (carritoActualizado) => (this.carrito = carritoActualizado),
      error: (err) => console.error('Error actualizando cantidad:', err),
    });
  }

  eliminarItem(itemId: number): void {
    this.carritoService.eliminarItem(itemId).subscribe({
      next: (carritoActualizado) => (this.carrito = carritoActualizado),
      error: (err) => console.error('Error eliminando item:', err),
    });
  }

  vaciarCarrito(): void {
    this.carritoService.vaciarCarrito().subscribe({
      next: (carritoVacio) => (this.carrito = carritoVacio),
      error: (err) => console.error('Error vaciando carrito:', err),
    });
  }

  calcularTotalCarrito(): number {
    if (!this.carrito || !this.carrito.items) return 0;
    return this.carrito.items.reduce((acc: number, item: any) => acc + (item.subtotal || item.precioUnitario * item.cantidad), 0);
  }

  procesarCompra(): void {
    if (this.procesandoCompra || !this.carrito?.items?.length) return;
    this.procesandoCompra = true;
    this.error = null;
    if (environment.paymentsEnabled) {
      const pendingOrder = {
        clienteEmail: this.usuarioActual || '',
        total: this.calcularTotalCarrito(),
        items: this.carrito.items.map((item: any) => ({
          productoId: item.productoId,
          nombreProducto: item.nombreProducto,
          cantidad: item.cantidad,
          precioUnitario: item.precioUnitario,
        })),
      };
      this.paymentsService.start(pendingOrder.total, this.cardAlias).subscribe({
        next: (payment) => {
          if (payment.status !== 'INITIATED' || !payment.url || !payment.token) {
            this.error = 'El pago no pudo iniciarse.';
            this.procesandoCompra = false;
            return;
          }
          const paymentUrl = new URL(payment.url);
          if (paymentUrl.protocol !== 'https:' || !paymentUrl.hostname.endsWith('.transbank.cl')) {
            this.error = 'Webpay devolvio una URL inesperada.';
            this.procesandoCompra = false;
            return;
          }
          sessionStorage.setItem('pedidos360_pending_order', JSON.stringify({
            paymentId: payment.paymentId, order: pendingOrder,
          }));
          const form = document.createElement('form');
          form.method = 'POST';
          form.action = payment.url;
          const token = document.createElement('input');
          token.type = 'hidden';
          token.name = 'token_ws';
          token.value = payment.token;
          form.appendChild(token);
          document.body.appendChild(form);
          form.submit();
        },
        error: (err) => {
          this.error = err.status === 429
            ? 'Pago bloqueado: mas de tres intentos con el mismo alias en diez segundos.'
            : 'No se pudo iniciar Webpay. Revisa el servicio de pagos.';
          this.procesandoCompra = false;
        },
      });
      return;
    }
    this.ordersService.crearOrden({
      clienteEmail: this.usuarioActual || '',
      total: this.calcularTotalCarrito(),
      items: this.carrito.items.map((item: any) => ({
        productoId: item.productoId,
        nombreProducto: item.nombreProducto,
        cantidad: item.cantidad,
        precioUnitario: item.precioUnitario,
      })),
    }).subscribe({
      next: (order) => {
        this.ordenConfirmada = { id: order.orderId, total: order.total, fecha: order.fecha };
        this.carritoService.vaciarCarrito().subscribe({
          next: (carritoVacio) => { this.carrito = carritoVacio; this.mostrarCarrito = false; },
          error: () => { this.error = 'Orden publicada, pero no se pudo vaciar el carrito. No repitas la compra.'; },
        });
        this.procesandoCompra = false;
        this.catalogoService.listar().subscribe({ next: (data) => (this.productos = data) });
      },
      error: (err) => {
        this.error = `No se pudo publicar la orden: ${err.status || 0} ${err.statusText || ''}`;
        this.procesandoCompra = false;
      },
    });
  }

  cerrarSesion(): void {
    this.msalService.logoutRedirect();
  }
}
