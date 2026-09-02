package io.github.jys0615.stayport.adapter.persistence;

import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.domain.mapping.RoomTypeMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomTypeMappingRepository extends JpaRepository<RoomTypeMapping, Long> {

    Optional<RoomTypeMapping> findBySupplierAndSupplierStayCodeAndSupplierRoomCode(
            SupplierId supplier, String supplierStayCode, String supplierRoomCode);
}
