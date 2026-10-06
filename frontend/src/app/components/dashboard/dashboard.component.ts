// src/app/components/dashboard/dashboard.component.ts
//
// Esta pantalla solo se puede ver si MsalGuard + roleGuard dejaron pasar
// (ver app.routes.ts). Al llegar aca, YA hay una sesion activa con token -
// por eso los 3 servicios (AuthApiService, CatalogoService, CarritoService)
// se pueden llamar directo, sin pedir el token a mano: MsalInterceptor se
// encarga de pegarlo en cada request saliente.
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MsalService } from '@azure/msal-angular';
import { AuthApiService } from '../../services/auth-api.service';
import { CatalogoService, Producto } from '../../services/catalogo.service';
import { CarritoService } from '../../services/carrito.service';
import { decodeJwtPayload, extraerPermisos } from '../../services/jwt.util';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit {
  private msalService = inject(MsalService);
  private authApiService = inject(AuthApiService);
  private catalogoService = inject(CatalogoService);
  private carritoService = inject(CarritoService);

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
    if (!this.carrito || !this.carrito.items || this.carrito.items.length === 0) return;
    this.procesandoCompra = true;

    // Simula / llama la creacion de orden hacia orders-service y RabbitMQ
    setTimeout(() => {
      const orderId = 'ORD-' + Math.floor(1000 + Math.random() * 9000);
      const total = this.calcularTotalCarrito();
      this.ordenConfirmada = {
        id: orderId,
        total: total,
        fecha: new Date().toISOString(),
      };
      this.vaciarCarrito();
      this.mostrarCarrito = false;
      this.procesandoCompra = false;
      // Recarga el catálogo para mostrar el stock descontado
      this.catalogoService.listar().subscribe({
        next: (data) => (this.productos = data),
      });
    }, 1200);
  }

  cerrarSesion(): void {
    this.msalService.logoutRedirect();
  }
}
