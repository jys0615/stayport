package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/**
 * 공급사 숙소 목록 조회 결과. 매핑 동기화의 입력이 된다.
 */
public sealed interface CatalogResult {

    SupplierId supplier();

    record Success(SupplierId supplier, List<SupplierStay> stays) implements CatalogResult {

        public Success {
            stays = List.copyOf(stays);
        }
    }

    record Failure(SupplierId supplier, FailureType type, String detail) implements CatalogResult {
    }
}
