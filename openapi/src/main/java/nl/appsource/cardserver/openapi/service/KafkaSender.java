package nl.appsource.cardserver.openapi.service;

import nl.appsource.cardserver.openapi.MyServerSentEvent;

public interface KafkaSender {

    void send(String topic, String message);

    void sendSse(MyServerSentEvent<?> message);

}
