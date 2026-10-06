package com.pedidos360.envios.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class GuiaDespacho implements Serializable {
    private String trackingNumber;
    private String orderId;
    private String clienteEmail;
    private String estado; // EN_PREPARACION, DESPACHADO, ENTREGADO
    private String direccionDestino;
    private String fechaEstimadaEntrega;

    public GuiaDespacho() {}

    public GuiaDespacho(String trackingNumber, String orderId, String clienteEmail, String estado, String direccionDestino, String fechaEstimadaEntrega) {
        this.trackingNumber = trackingNumber;
        this.orderId = orderId;
        this.clienteEmail = clienteEmail;
        this.estado = estado;
        this.direccionDestino = direccionDestino;
        this.fechaEstimadaEntrega = fechaEstimadaEntrega;
    }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getDireccionDestino() { return direccionDestino; }
    public void setDireccionDestino(String direccionDestino) { this.direccionDestino = direccionDestino; }

    public String getFechaEstimadaEntrega() { return fechaEstimadaEntrega; }
    public void setFechaEstimadaEntrega(String fechaEstimadaEntrega) { this.fechaEstimadaEntrega = fechaEstimadaEntrega; }
}
