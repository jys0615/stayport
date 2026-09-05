package io.github.jys0615.stayport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 패키지 경계를 빌드가 지키게 한다. 규칙의 근거는 docs/design.md §3 — 문서에 적힌 경계와
 * 코드가 어긋나면 문서를 다시 읽어 발견하는 것보다 테스트가 깨지는 편이 낫다.
 */
@AnalyzeClasses(
        packages = "io.github.jys0615.stayport",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 도메인은 공급사도, 저장 기술도, HTTP도 모른다. */
    @ArchTest
    static final ArchRule domainKnowsNoOuterLayer = noClasses()
            .that().resideInAPackage("..stayport.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..stayport.adapter..", "..stayport.infra..", "..stayport.api..",
                    "..stayport.application..", "..stayport.mock..")
            .because("도메인이 바깥 계층을 참조하면 공급사 사정이 표준 모델로 스며든다 (design.md §3)");

    /** 공급사 전용 DTO와 변환은 각자의 어댑터 패키지 안에서 끝난다. */
    @ArchTest
    static final ArchRule supplierPackagesStayIsolated = noClasses()
            .that().resideOutsideOfPackage("..stayport.adapter.suppliera..")
            .should().dependOnClassesThat().resideInAPackage("..stayport.adapter.suppliera..")
            .because("A의 응답 형식이 어댑터 밖으로 새면 신규 공급사 추가 시 도메인을 고치게 된다");

    @ArchTest
    static final ArchRule supplierBPackageStaysIsolated = noClasses()
            .that().resideOutsideOfPackage("..stayport.adapter.supplierb..")
            .should().dependOnClassesThat().resideInAPackage("..stayport.adapter.supplierb..")
            .because("B의 응답 형식도 마찬가지다");

    /** 흉내 서버는 운영 코드가 아니다 — 어느 패키지도 mock을 참조하지 않는다. */
    @ArchTest
    static final ArchRule nothingDependsOnMock = noClasses()
            .that().resideOutsideOfPackage("..stayport.mock..")
            .should().dependOnClassesThat().resideInAPackage("..stayport.mock..")
            .because("본 앱이 흉내 서버에 기대면 흉내 없이는 돌 수 없는 앱이 된다");

    /** 유스케이스·포트는 특정 공급사 구현을 모른다 — 포트 인터페이스로만 접근한다. */
    @ArchTest
    static final ArchRule applicationDependsOnPortsNotAdapters = noClasses()
            .that().resideInAPackage("..stayport.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..stayport.adapter..", "..stayport.api..")
            .because("신규 공급사 추가 시 유스케이스가 바뀌지 않는다는 주장(README)의 전제다");
}
