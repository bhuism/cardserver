package nl.appsource.cardserver.converters.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;
import nl.appsource.generated.openapi.model.AiRisc;
import nl.appsource.generated.openapi.model.GameVariant;
import nl.appsource.generated.openapi.model.ScreenOrientation;
import nl.appsource.generated.openapi.model.Theme;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserToOpenApiConverter implements Converter<User, nl.appsource.generated.openapi.model.User> {

    @Override
    @NonNull
    public nl.appsource.generated.openapi.model.User convert(final User source) {

        final nl.appsource.generated.openapi.model.User target = new nl.appsource.generated.openapi.model.User();

        target.setId(source.getId());
        target.setUpdated(source.getUpdated());
        target.setDisplayName(source.getDisplayName());
        target.setPhotoURL(source.getPhotoURL());
        target.setSkipAnimation(Boolean.TRUE.equals(source.getSkipAnimation()));
        target.setGameVariant(GameVariant.valueOf(source.getGameVariant().name()));
        target.setScreenOrientation(ScreenOrientation.valueOf(source.getScreenOrientation().name()));
        target.setTheme(Theme.valueOf(source.getTheme().name()));
        target.setAiRisc(AiRisc.valueOf(source.getAiRisc().name()));
        target.setAutoKnock(source.getAutoKnock());

        return target;
    }
}
