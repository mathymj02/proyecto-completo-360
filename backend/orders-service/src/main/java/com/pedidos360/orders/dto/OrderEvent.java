package com.pedidos360.orders.dto;

import java.io.Serializable;
import java.util.List;

public class OrderEvent implements Serializable {
    private String orderId;
    private String clienteEmail;
    private Double total;
    private List<OrderItem> items;
    private String fecha;

    public OrderEvent() {}

    public OrderEvent(String orderId, String clienteEmail, Double total, List<OrderItem> items, String fecha) {
        this.orderId = orderId;
        this.clienteEmail = clienteEmail;
        this.total = total;
        this.items = items;
        this.fecha = fecha;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public static class OrderItem implements Serializable {
        private Long productoId;
        private String nombreProducto;
        private Integer cantidad;
        private Double precioUnitario;

        public OrderItem() {}

        public OrderItem(Long productoId, String nombreProducto, Integer cantidad, Double precioUnitario) {
            this.productoId = productoId;
            this.nombreProducto = nombreProducto;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
        }

        public Long getProductoId() { return productoId; }
        public void setProductoId(Long productoId) { this.productoId = productoId; }

        public String getNombreProducto() { return nombreProducto; }
        public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        public Double getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(Double precioUnitario) { this.precioUnitario = precioUnitario; }
    }
}
