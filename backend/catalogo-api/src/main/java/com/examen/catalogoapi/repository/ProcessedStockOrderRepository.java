package com.examen.catalogoapi.repository;

import com.examen.catalogoapi.model.ProcessedStockOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStockOrderRepository extends JpaRepository<ProcessedStockOrder, String> {
}
