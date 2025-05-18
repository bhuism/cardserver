package nl.appsource.cardserver.repository;

import org.springframework.beans.factory.annotation.Value;

public interface IdOnly {

    @Value("#{target.id}")
    String getId();
}
