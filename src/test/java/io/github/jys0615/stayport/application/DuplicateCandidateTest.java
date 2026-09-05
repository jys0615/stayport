package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.DuplicateCandidateService.DuplicateCandidate;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 같은 건물을 두 공급사가 다른 코드로 팔 때 후보로 잡히는지. 공급사끼리 공통 키가 없어
 * 이름·객실 구성으로 추정한다는 것이 전제 — 판정 근거는 docs/duplicate-matching.md.
 * 매핑을 직접 구성하므로 다른 테스트와 DB를 공유하지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:duplicates-e2e;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class DuplicateCandidateTest {

    @Autowired
    private DuplicateCandidateService service;

    @Autowired
    private MappingStore mappingStore;

    @BeforeEach
    void seedMappings() {
        // 흉내 서버 데이터와 같은 구도: A-10023 ≡ B77120 (같은 호텔, 객실명은 "Room" 접미사만 차이)
        long riversideA = mappingStore.upsertStay(SupplierId.A, "A-10023", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.A, "A-10023", "DLX-TWN", riversideA, "Deluxe Twin", 2);

        long riversideB = mappingStore.upsertStay(SupplierId.B, "B77120", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.B, "B77120", "R-401", riversideB, "Deluxe Twin Room", 2);

        long namsan = mappingStore.upsertStay(SupplierId.A, "A-10044", "Namsan Garden Stay");
        mappingStore.upsertRoomType(SupplierId.A, "A-10044", "STD-DBL", namsan, "Standard Double", 2);
    }

    @Test
    @DisplayName("이름이 같고 객실 구성이 겹치는 다른 공급사 숙소만 후보로 잡힌다")
    void detectsCrossSupplierSameStay() {
        List<DuplicateCandidate> candidates = service.findCandidates();

        assertThat(candidates).hasSize(1);
        DuplicateCandidate candidate = candidates.getFirst();
        assertThat(candidate.first().supplierStayCode()).isEqualTo("A-10023");
        assertThat(candidate.second().supplierStayCode()).isEqualTo("B77120");
        assertThat(candidate.nameSimilarity()).isEqualTo(1.0);
        assertThat(candidate.roomMatchRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("이름이 비슷해도 객실 구성이 다르면 후보가 아니다")
    void differentRoomCompositionIsNotACandidate() {
        // 이름은 거의 같지만 4인실만 있는 B 숙소 — 객실 짝이 없다
        long other = mappingStore.upsertStay(SupplierId.B, "B99001", "Namsan Garden Stay");
        mappingStore.upsertRoomType(SupplierId.B, "B99001", "R-901", other, "Family Suite", 4);

        List<DuplicateCandidate> candidates = service.findCandidates();

        assertThat(candidates)
                .noneMatch(candidate -> candidate.second().supplierStayCode().equals("B99001"));
    }
}
