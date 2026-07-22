package nl.appsource.cardserver.stream.service;

import nl.appsource.cardserver.converters.service.BoomToOpenApiConverter;
import nl.appsource.cardserver.converters.service.GameToOpenApiConverter;
import nl.appsource.cardserver.converters.service.UserToOpenApiConverter;
import nl.appsource.cardserver.openapi.service.SseEventSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaEventListenerImplTest {

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private GameToOpenApiConverter gameToOpenApiConverter;

    @Mock
    private BoomToOpenApiConverter boomToOpenApiConverter;

    @Mock
    private UserToOpenApiConverter userToOpenApiConverter;

    @Mock
    private SseEventSender sseEventSender;

    private KafkaEventListenerImpl kafkaEventListener;

    @BeforeEach
    void setUp() {
        kafkaEventListener = new KafkaEventListenerImpl(jsonMapper, gameToOpenApiConverter, boomToOpenApiConverter, userToOpenApiConverter);
    }

//    @Test
//    void testListenAndEmit() {
//        String documentPayload = "{\"_class\":\"nl.appsource.cardserver.model.Game\"}";
//        JsonNode jsonNode = mock(JsonNode.class);
//        when(jsonNode.asString()).thenReturn("nl.appsource.cardserver.model.Game");
//        when(jsonNode.get("_class")).thenReturn(jsonNode);
//
//        Game game = new Game();
//        game.setPlayers(List.of("user1", "user2"));
//
//        nl.appsource.generated.openapi.model.Game openApiGame = new nl.appsource.generated.openapi.model.Game();
//        openApiGame.setPlayers(List.of("user1", "user2"));
//
//        when(jsonMapper.readTree(documentPayload)).thenReturn(jsonNode);
//        when(jsonMapper.convertValue(jsonNode, Game.class)).thenReturn(game);
//        when(gameToOpenApiConverter.convert(game)).thenReturn(openApiGame);
//
////        StepVerifier.create(kafkaEventListener.kafkaStreams())
////            .then(() -> kafkaEventListener.listen("id", documentPayload))
////            .expectNextMatches(event -> "updateGame".equals(event.event()) && openApiGame.equals(event.data()))
////            .thenCancel()
////            .verify();
//
//    }
}
