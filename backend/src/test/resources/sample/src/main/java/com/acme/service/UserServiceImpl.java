package com.acme.service;

import com.acme.domain.User;
import com.acme.domain.UserDto;
import com.acme.repo.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repo;

    public UserServiceImpl(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public User find(Long id) {
        return repo.findById(id).orElseThrow();
    }

    @Override
    public User create(UserDto dto) {
        if (!repo.findByName(dto.name()).isEmpty()) {
            throw new IllegalStateException("exists");
        }
        User user = new User(dto.name());
        validate(user);
        return repo.save(user);
    }

    private void validate(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            throw new IllegalArgumentException("name");
        }
        for (char c : user.getName().toCharArray()) {
            if (!Character.isLetter(c)) throw new IllegalArgumentException("letters only");
        }
    }
}
