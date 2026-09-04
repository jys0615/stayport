package io.github.jys0615.stayport.infra;

import io.github.jys0615.stayport.domain.SupplierId;
import io.netty.channel.ChannelOption;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급사별 WebClient. 새 공급사는 설정 항목 추가만으로 붙는다.
 *
 * <p>주의 두 가지:
 * <ul>
 *   <li>{@code WebClient.Builder} 자동 구성이 이 Boot 버전 클래스패스에 없어 직접 만든다.
 *       JSON 코덱에는 앱의 {@code JsonMapper}를 꽂는다 — 기본 코덱은 모르는 필드에서 터진다.
 *   <li>여기서 거는 제한은 연결 수립(connect)뿐. 응답 제한은 어댑터의 {@code .timeout()} 하나만 —
 *       겹쳐 걸면 어느 쪽이 발동했는지 로그로 구분할 수 없다.
 * </ul>
 */
@Component
public class SupplierWebClients {

    private static final Logger log = LoggerFactory.getLogger(SupplierWebClients.class);

    private final Map<SupplierId, WebClient> clients;

    public SupplierWebClients(StayportProperties properties, JsonMapper jsonMapper) {
        Map<SupplierId, WebClient> built = new EnumMap<>(SupplierId.class);
        properties.suppliers().forEach((id, config) -> {
            built.put(id, build(config, jsonMapper));
            log.info("supplier {} -> {} (connect {}ms, call {}ms, chunk {}, key {})",
                    id, config.baseUrl(), config.connectTimeout().toMillis(),
                    config.callTimeout().toMillis(), config.chunkSize(), config.maskedApiKey());
        });
        this.clients = Map.copyOf(built);
    }

    private static WebClient build(StayportProperties.Supplier config, JsonMapper jsonMapper) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis());

        return WebClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("X-Api-Key", config.apiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> {
                    codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                    codecs.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                })
                .build();
    }

    public WebClient forSupplier(SupplierId supplier) {
        WebClient client = clients.get(supplier);
        if (client == null) {
            throw new IllegalStateException(
                    "공급사 %s의 설정이 없다. stayport.suppliers 아래에 추가해야 한다.".formatted(supplier));
        }
        return client;
    }
}
