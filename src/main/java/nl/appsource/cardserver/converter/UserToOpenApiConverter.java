package nl.appsource.cardserver.converter;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserToOpenApiConverter implements Converter<User, org.openapitools.model.User> {

    @Override
    public org.openapitools.model.User convert(final User source) {

        final org.openapitools.model.User target = new org.openapitools.model.User();

        target.setId(source.getId());
        target.setCreated(source.getCreated());
        target.setEmail(source.getEmail());
        target.setUpdated(source.getUpdated());
        target.setDisplayName(source.getDisplayName());
        target.setLastLogin(Optional.ofNullable(source.getLastLogin()));
        target.setName(source.getName());
        target.setPhotoURL(source.getPhotoURL());
        target.setProviderId(source.getProviderId());

        return target;
    }
}
