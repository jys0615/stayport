package io.github.jys0615.stayport.support;

import io.github.jys0615.stayport.StayportApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 테스트가 쓰는 공급사 흉내 서버.
 *
 * <p>별도 프로세스로 9090에 띄우는 방식이면 clean clone에서 {@code ./gradlew test}가 실패한다.
 * 그러면 재현 가능성이 테스트 실행 방법에 달리게 되므로, 테스트 JVM 안에서 임의 포트로 띄우고
 * 주소를 {@code @DynamicPropertySource}로 주입한다.
 *
 * <p>JVM당 한 번만 뜬다. 흉내 서버는 상태(모드)를 갖고 있으니 각 테스트가 {@link #reset()}으로
 * 정상 모드로 되돌려 놓아야 한다.
 */
public final class MockSupplierServer {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private static ConfigurableApplicationContext context;
    private static int port;

    private MockSupplierServer() {
    }

    /** 아직 안 떴으면 띄우고, 주소를 돌려준다. */
    public static synchronized String baseUrl() {
        if (context == null) {
            // 명령행 인자로 넘긴다. SpringApplicationBuilder.properties()는 기본 프로퍼티라
            // 우선순위가 가장 낮아서 application-mock.yml의 server.port: 9090에 밀린다.
            // 그러면 테스트가 9090을 점유하게 되고, 그 포트를 쓰는 무언가가 떠 있으면 실패한다.
            context = new SpringApplicationBuilder(StayportApplication.class)
                    .profiles("mock")
                    .run(
                            "--server.port=0",
                            "--spring.datasource.url=jdbc:h2:mem:mock-supplier-test",
                            "--spring.jpa.hibernate.ddl-auto=none",
                            "--stayport.sync.on-startup=false");
            Integer assigned = context.getEnvironment().getProperty("local.server.port", Integer.class);
            if (assigned == null) {
                throw new IllegalStateException("흉내 서버 포트를 알 수 없다");
            }
            port = assigned;
            Runtime.getRuntime().addShutdownHook(new Thread(MockSupplierServer::stop));
        }
        return "http://localhost:" + port;
    }

    /** normal | error | no-response | empty-body */
    public static void mode(String supplier, String value) {
        send("/control/" + supplier + "/mode?value=" + value);
    }

    /** 이 코드가 포함된 재고 조회만 실패시킨다. 빈 문자열이면 해제. */
    public static void failCode(String supplier, String code) {
        send("/control/" + supplier + "/fail-code?value=" + code);
    }

    /** 두 공급사 모두 정상으로. 테스트 간 상태 격리용. */
    public static void reset() {
        mode("a", "normal");
        mode("b", "normal");
        failCode("a", "");
        failCode("b", "");
    }

    private static void send(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("흉내 서버 제어 실패: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("흉내 서버에 연결할 수 없다", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("흉내 서버 제어가 중단됐다", e);
        }
    }

    private static synchronized void stop() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}
