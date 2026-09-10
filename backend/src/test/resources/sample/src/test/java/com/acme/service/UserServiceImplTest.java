package com.acme.service;

import com.acme.domain.UserDto;
import org.junit.jupiter.api.Test;

class UserServiceImplTest {

    @Test
    void findsUser() {
        new UserServiceImpl(null).find(1L);
    }

    @Test
    void createsUser() {
        var svc = new UserServiceImpl(null);
        svc.create(new UserDto("x"));
    }
}
