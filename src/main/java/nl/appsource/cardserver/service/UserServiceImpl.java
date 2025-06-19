package nl.appsource.cardserver.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.appsource.cardserver.model.User;
import nl.appsource.cardserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public Optional<User> findById(final String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public List<User> getUsers(final List<String> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Override
    public Optional<User> findByEmail(final String email) {
        return userRepository.findOptionalByEmail(email);
    }


    @Override
    public List<User> findAllIncomingInvites(final String userId) {
        return userRepository.findAllIncomingInvites(userId);
    }

    @Override
    public User save(final User user) {
        return userRepository.save(user);
    }
}
