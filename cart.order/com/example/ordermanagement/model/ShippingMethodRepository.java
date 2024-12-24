package com.example.ordermanagement.model;

import com.example.ordermanagement.model.ShippingMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
    Optional<ShippingMethod> findByMethodName(String methodName);
}
