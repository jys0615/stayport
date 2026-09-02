package io.github.jys0615.stayport.adapter.persistence;

import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.domain.mapping.RoomTypeMapping;
import io.github.jys0615.stayport.domain.mapping.StayMapping;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매핑 저장소의 JPA 구현.
 *
 * <p>조회-후-삽입이 정상 경로다. 동기화가 직렬화되어 있어 쓰는 주체가 하나뿐이므로 이걸로 충분하다.
 * 그 전제가 깨져 UNIQUE 제약을 밟으면 재조회로 흡수한다 — 락을 잡는 대신, 락이 필요한 상황을
 * 만들지 않는 쪽을 택했고 제약은 그 판단이 틀렸을 때의 그물이다.
 */
@Component
class JpaMappingStore implements MappingStore {

    private final StayMappingRepository stays;
    private final RoomTypeMappingRepository roomTypes;

    JpaMappingStore(StayMappingRepository stays, RoomTypeMappingRepository roomTypes) {
        this.stays = stays;
        this.roomTypes = roomTypes;
    }

    @Override
    @Transactional
    public long upsertStay(SupplierId supplier, String stayCode, String stayName) {
        return stays.findBySupplierAndSupplierStayCode(supplier, stayCode)
                .map(existing -> {
                    if (!Objects.equals(existing.stayName(), stayName)) {
                        existing.renameTo(stayName);
                    }
                    return existing.id();
                })
                .orElseGet(() -> insertStay(supplier, stayCode, stayName));
    }

    private long insertStay(SupplierId supplier, String stayCode, String stayName) {
        try {
            return stays.save(new StayMapping(supplier, stayCode, stayName)).id();
        } catch (DataIntegrityViolationException raced) {
            // 다른 작성자가 먼저 넣었다. 그 사람이 만든 식별자를 쓴다.
            return stays.findBySupplierAndSupplierStayCode(supplier, stayCode)
                    .orElseThrow(() -> raced)
                    .id();
        }
    }

    @Override
    @Transactional
    public long upsertRoomType(
            SupplierId supplier,
            String stayCode,
            String roomCode,
            long internalStayId,
            String roomTypeName,
            int maxOccupancy) {

        return roomTypes.findBySupplierAndSupplierStayCodeAndSupplierRoomCode(supplier, stayCode, roomCode)
                .map(existing -> {
                    if (!Objects.equals(existing.roomTypeName(), roomTypeName)
                            || existing.maxOccupancy() != maxOccupancy) {
                        existing.refresh(roomTypeName, maxOccupancy);
                    }
                    return existing.id();
                })
                .orElseGet(() -> insertRoomType(supplier, stayCode, roomCode, internalStayId, roomTypeName, maxOccupancy));
    }

    private long insertRoomType(
            SupplierId supplier,
            String stayCode,
            String roomCode,
            long internalStayId,
            String roomTypeName,
            int maxOccupancy) {
        try {
            RoomTypeMapping saved = roomTypes.save(new RoomTypeMapping(
                    supplier, stayCode, roomCode, internalStayId, roomTypeName, maxOccupancy));
            return saved.id();
        } catch (DataIntegrityViolationException raced) {
            return roomTypes.findBySupplierAndSupplierStayCodeAndSupplierRoomCode(supplier, stayCode, roomCode)
                    .orElseThrow(() -> raced)
                    .id();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappedRoomType> findAll() {
        // 숙소명은 stay_mapping에만 있으므로 두 테이블을 합쳐야 한다. 지금 규모에서는 전량을
        // 읽어 메모리에서 잇는 편이 조인 쿼리보다 읽기 쉽고 충분히 빠르다.
        Map<Long, StayMapping> staysById = stays.findAll().stream()
                .collect(Collectors.toMap(StayMapping::id, Function.identity()));

        return roomTypes.findAll().stream()
                .map(roomType -> {
                    StayMapping stay = staysById.get(roomType.internalStayId());
                    return new MappedRoomType(
                            roomType.supplier(),
                            roomType.supplierStayCode(),
                            roomType.supplierRoomCode(),
                            roomType.internalStayId(),
                            stay == null ? null : stay.stayName(),
                            roomType.id(),
                            roomType.roomTypeName(),
                            roomType.maxOccupancy());
                })
                .toList();
    }

    @Override
    public long countStays() {
        return stays.count();
    }

    @Override
    public long countRoomTypes() {
        return roomTypes.count();
    }
}
