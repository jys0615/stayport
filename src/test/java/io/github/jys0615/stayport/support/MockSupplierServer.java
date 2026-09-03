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
            context = new SpringApplicationBuilder(StayportApplication.class)
                    .profiles("mock")
                    .properties(
                            "server.port=0",
                            "spring.datasource.url=jdbc:h2:mem:mock-supplier-test",
                            "spring.jpa.hibernate.ddl-auto=none",
                            "stayport.sync.on-startup=false")
                    .run();
            Integer assigned = context.getEnvironment().getProperty("local.server.port", Integer.class);
            if (assigned == null) {
                throw new IllegalStateException("흉내 서버 포트를 알 수 없다");
            }
            port = assigned;
            Runtime.getRuntime().addShutdownHook(new Thread(MockSupplierServer::stop));
        }
        return "http://localhost:" + port;
    }

    /** normal | error | no-response */
    public static void mode(String supplier, String value) {
        send("/control/" + supplier + "/mode?value=" + value);
    }

    /** 두 공급사 모두 정상으로. 테스트 사이에 상태가 새지 않게 한다. */
    public static void reset() {
        mode("a", "normal");
        mode("b", "normal");
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
