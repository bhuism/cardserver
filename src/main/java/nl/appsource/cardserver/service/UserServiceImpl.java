package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.converter.UserToOpenApiConverter;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserToOpenApiConverter userToOpenApiConverter;

    @Override
    public Optional<org.openapitools.model.User> findById(final String userId) {
        return userRepository.findById(userId).map(userToOpenApiConverter::convert);
    }

    @Override
    public Optional<org.openapitools.model.User> findByEmail(final String email) {
        return userRepository.findOptionalByEmail(email).map(userToOpenApiConverter::convert);
    }


    @Override
    public List<org.openapitools.model.User> findAllIncomingInvites(final String userId) {
        return userRepository.findAllIncomingInvites(userId).stream().map(userToOpenApiConverter::convert).collect(Collectors.toList());
    }


}
