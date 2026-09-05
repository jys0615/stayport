package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 서로 다른 공급사가 같은 숙소를 파는 것으로 보이는 쌍을 찾는다. 공급사 간 공통 키가 없으므로
 * 숙소명과 객실 구성으로 추정한다. 자동 병합은 하지 않는다 — 같은 건물이라도 조식 포함 여부처럼
 * 조건이 다른 상품일 수 있어, 후보를 사람에게 보여주는 데서 멈춘다(docs/design.md §8).
 */
@Service
public class DuplicateCandidateService {

    /** 이름이 이만큼 겹치지 않으면 같은 숙소로 보지 않는다. */
    private static final double NAME_THRESHOLD = 0.6;
    /** 한쪽 객실 구성의 절반 이상이 짝을 찾아야 한다. */
    private static final double ROOM_THRESHOLD = 0.5;

    private final MappingStore mappingStore;

    DuplicateCandidateService(MappingStore mappingStore) {
        this.mappingStore = mappingStore;
    }

    public List<DuplicateCandidate> findCandidates() {
        List<StayProfile> stays = profilesOf(mappingStore.findAll());

        List<DuplicateCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < stays.size(); i++) {
            for (int j = i + 1; j < stays.size(); j++) {
                StayProfile a = stays.get(i);
                StayProfile b = stays.get(j);
                if (a.supplier() == b.supplier()) {
                    continue; // 같은 공급사 안에서는 코드가 이미 유일하다
                }
                double nameSimilarity = jaccard(a.nameTokens(), b.nameTokens());
                if (nameSimilarity < NAME_THRESHOLD) {
                    continue;
                }
                double roomMatch = roomMatchRatio(a.rooms(), b.rooms());
                if (roomMatch < ROOM_THRESHOLD) {
                    continue;
                }
                candidates.add(new DuplicateCandidate(
                        StayRef.of(a), StayRef.of(b), round(nameSimilarity), round(roomMatch)));
            }
        }
        return candidates;
    }

    /** 매핑(객실 단위)을 숙소 단위 프로필로 접는다. */
    private static List<StayProfile> profilesOf(List<MappedRoomType> mappings) {
        Map<String, StayProfile> byStay = new LinkedHashMap<>();
        for (MappedRoomType mapping : mappings) {
            String key = mapping.supplier() + "/" + mapping.supplierStayCode();
            StayProfile profile = byStay.computeIfAbsent(key, ignored -> new StayProfile(
                    mapping.supplier(),
                    mapping.supplierStayCode(),
                    mapping.internalStayId(),
                    mapping.stayName(),
                    tokens(mapping.stayName()),
                    new ArrayList<>()));
            profile.rooms().add(new RoomSignature(tokens(mapping.roomTypeName()), mapping.maxOccupancy()));
        }
        return List.copyOf(byStay.values());
    }

    /**
     * 객실 구성 일치율 = 짝을 찾은 객실 수 ÷ 작은 쪽 객실 수.
     * 객실이 짝이라는 판정: 최대 수용 인원이 같고 이름 토큰이 절반 이상 겹친다.
     */
    private static double roomMatchRatio(List<RoomSignature> a, List<RoomSignature> b) {
        int matched = 0;
        for (RoomSignature roomA : a) {
            for (RoomSignature roomB : b) {
                if (roomA.maxOccupancy() == roomB.maxOccupancy()
                        && jaccard(roomA.nameTokens(), roomB.nameTokens()) >= ROOM_THRESHOLD) {
                    matched++;
                    break;
                }
            }
        }
        return (double) matched / Math.min(a.size(), b.size());
    }

    private static Set<String> tokens(String name) {
        Set<String> tokens = new TreeSet<>();
        for (String token : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / (a.size() + b.size() - shared);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private record StayProfile(SupplierId supplier, String stayCode, long internalStayId,
            String stayName, Set<String> nameTokens, List<RoomSignature> rooms) {
    }

    private record RoomSignature(Set<String> nameTokens, int maxOccupancy) {
    }

    /** 같은 숙소로 추정되는 한 쌍. 판정 근거(이름·객실 일치율)를 같이 준다. */
    public record DuplicateCandidate(StayRef first, StayRef second,
            double nameSimilarity, double roomMatchRatio) {
    }

    public record StayRef(SupplierId supplier, String supplierStayCode, long internalStayId, String stayName) {

        private static StayRef of(StayProfile profile) {
            return new StayRef(profile.supplier(), profile.stayCode(),
                    profile.internalStayId(), profile.stayName());
        }
    }
}
