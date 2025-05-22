package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    final UserRepository userRepository;

    @Override
    public Optional<org.openapitools.model.User> findById(String userId) {
        return userRepository.findById(userId).map(UserServiceImpl::convert);
    }

    private static org.openapitools.model.User convert(final User user) {

        final org.openapitools.model.User result = new org.openapitools.model.User();

        result.setId(user.getId());
        result.setCreated(user.getCreated());
        result.setEmail(user.getEmail());
        result.setUpdated(user.getUpdated());
        result.setInvites(user.getInvites());
        result.setDisplayName(user.getDisplayName());
        result.setLastLogin(user.getLastLogin());
        result.setName(user.getName());
        result.setPhotoURL(user.getPhotoURL());
        result.setProviderId(user.getProviderId());

        return result;

    }
}
