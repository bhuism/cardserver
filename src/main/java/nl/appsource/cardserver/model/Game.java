package nl.appsource.cardserver.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "games")
public class Game extends TimedAbstractJpaPersistable {

    public Game(final Long id) {
        super(id);
    }

}
