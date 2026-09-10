package com.acme.service;

import com.acme.domain.User;
import com.acme.domain.UserDto;

public interface UserService {

    User find(Long id);

    User create(UserDto dto);
}
