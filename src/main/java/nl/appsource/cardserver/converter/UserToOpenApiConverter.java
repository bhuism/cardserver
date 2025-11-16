package nl.appsource.cardserver.converter;

import lombok.RequiredArgsConstructor;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.service.SseEmitterRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserToOpenApiConverter implements Converter<User, org.openapitools.model.User> {

    private final ApplicationContext applicationContext;

    private SseEmitterRepository sseEmitterRepository;

    @Override
    public org.openapitools.model.User convert(final User source) {

        if (sseEmitterRepository == null) {
            sseEmitterRepository = applicationContext.getBean(SseEmitterRepository.class);
        }

        final org.openapitools.model.User target = new org.openapitools.model.User();

        target.setId(source.getId());
        target.setUpdated(source.getUpdated());
        target.setDisplayName(source.getDisplayName());
        target.setPhotoURL(source.getPhotoURL());
        target.setOnline(sseEmitterRepository.isUserOnline(source.getId()));
        target.setSkipAnimation(Boolean.TRUE.equals(source.getSkipAnimation()));
        target.setGameVariant(source.getGameVariant());
        target.setScreenOrientation(source.getScreenOrientation());
        target.setTheme(source.getTheme());

        return target;
    }
}
