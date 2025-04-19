package nl.appsource.cardserver.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Base class for all Lost Lemon JPA entities.
 * <p>
 * JpaAbstractPersistable.
 */

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(includeFieldNames = false, onlyExplicitlyIncluded = true)
public abstract class JpaAbstractPersistable implements JpaPersistable {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    private Long id;

    @Override
    @Transient
    public boolean isNew() {
        return null == getId();
    }

    @Override
    public void makeNew() {
        id = null;
    }

    @Override
    public boolean equals(final Object obj) {

        if (null == obj) {
            return false;
        }

        if (this == obj) {
            return true;
        }

        if (!getClass().isAssignableFrom(obj.getClass())) {
            return false;
        }

        final Long myId = this.getId();

        if (myId != null) {

            final JpaAbstractPersistable that = (JpaAbstractPersistable) obj;

            return myId.equals(that.getId());

        } else {
            return false;
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hashCode = 61;

        final Long myId = this.getId();

        if (myId != null) {
            hashCode += myId.hashCode() * 71;
        }

        return hashCode;
    }

}
