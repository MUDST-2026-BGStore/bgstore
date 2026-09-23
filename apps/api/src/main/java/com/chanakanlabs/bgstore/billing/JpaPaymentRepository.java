package com.chanakanlabs.bgstore.billing;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaPaymentRepository extends JpaRepository<PaymentEntity, Long> {}
