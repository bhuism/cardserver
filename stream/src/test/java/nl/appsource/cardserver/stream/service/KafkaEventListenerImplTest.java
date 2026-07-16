package nl.appsource.cardserver.stream.service;

import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.model.Game;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventListenerImplTest {

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private GameToOpenApiConverter gameToOpenApiConverter;

    @Mock
    private SseEventSender sseEventSender;

    private KafkaEventListenerImpl kafkaEventListener;

    @BeforeEach
    void setUp() {
        kafkaEventListener = new KafkaEventListenerImpl(jsonMapper, gameToOpenApiConverter, sseEventSender);
    }

    @Test
    void testListenAndEmit() throws Exception {
        String documentPayload = "{\"_class\":\"nl.appsource.cardserver.model.Game\"}";
        JsonNode jsonNode = mock(JsonNode.class);
        JsonNode classNode = mock(JsonNode.class);
        when(classNode.asString()).thenReturn("nl.appsource.cardserver.model.Game");
        when(jsonNode.get("_class")).thenReturn(classNode);
        
        Game game = new Game();
        game.setPlayers(List.of("user1", "user2"));
        
        nl.appsource.generated.openapi.model.Game openApiGame = new nl.appsource.generated.openapi.model.Game();
        
        when(jsonMapper.readTree(documentPayload)).thenReturn(jsonNode);
        when(jsonMapper.convertValue(jsonNode, Game.class)).thenReturn(game);
        when(gameToOpenApiConverter.convert(game)).thenReturn(openApiGame);
        when(sseEventSender.gamesChanged(any())).thenReturn(Mono.empty());

        StepVerifier.create(kafkaEventListener.gamesChanged("user1"))
            .then(() -> kafkaEventListener.listen("id", documentPayload))
            .expectNextMatches(event -> "updateGame".equals(event.event()) && openApiGame.equals(event.data()))
            .thenCancel()
            .verify();

        verify(sseEventSender).gamesChanged(any());
    }
}
