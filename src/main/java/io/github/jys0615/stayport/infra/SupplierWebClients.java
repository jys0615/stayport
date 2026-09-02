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
 * 공급사별 WebClient 보관소.
 *
 * <p>공급사 설정이 맵으로 들어오므로, 새 공급사를 붙일 때 이 클래스도 손대지 않는다.
 *
 * <p>자동 구성된 {@code WebClient.Builder} 빈을 주입받지 않고 {@code WebClient.builder()}로
 * 직접 만든다. 이 버전에서는 해당 자동 구성이 클래스패스에 없고, 어차피 커넥터를 우리가 갈아끼우기
 * 때문에 빌려 쓸 이유가 크지 않다. 대신 JSON 코덱에는 애플리케이션의 {@code JsonMapper}를 꽂는다 —
 * 직접 만든 빌더의 기본 코덱을 쓰면 모르는 필드에서 터지는 설정이 딸려와, 공급사가 응답에 필드를
 * 하나 늘리는 순간 연동이 깨진다.
 *
 * <p><b>여기서 거는 제한은 연결 수립뿐이다.</b> 응답 대기 제한은 어댑터에서 Reactor
 * {@code .timeout()} 하나로만 건다. 커넥터의 response timeout과 겹쳐 걸면 어느 쪽이 발동해
 * 실패했는지 로그에서 갈라낼 수 없다.
 *
 * <p><b>여기서 거는 제한은 연결 수립뿐이다.</b> 응답 대기 제한은 어댑터에서 Reactor
 * {@code .timeout()} 하나로만 건다. 커넥터의 response timeout과 겹쳐 걸면 어느 쪽이 발동해
 * 실패했는지 로그에서 갈라낼 수 없다.
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
