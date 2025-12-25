package nl.appsource.cardserver.converter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserToOpenApiConverter implements Converter<User, org.openapitools.model.User> {

    @Override
    @NonNull
    public org.openapitools.model.User convert(final User source) {

        final org.openapitools.model.User target = new org.openapitools.model.User();

        target.setId(source.getId());
        target.setUpdated(source.getUpdated());
        target.setDisplayName(source.getDisplayName());
        target.setPhotoURL(source.getPhotoURL());
        target.setSkipAnimation(Boolean.TRUE.equals(source.getSkipAnimation()));
        target.setGameVariant(source.getGameVariant());
        target.setScreenOrientation(source.getScreenOrientation());
        target.setTheme(source.getTheme());

        return target;
    }
}
