package io.github.jys0615.stayport.adapter.persistence;

import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.domain.mapping.StayMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StayMappingRepository extends JpaRepository<StayMapping, Long> {

    Optional<StayMapping> findBySupplierAndSupplierStayCode(SupplierId supplier, String supplierStayCode);
}
