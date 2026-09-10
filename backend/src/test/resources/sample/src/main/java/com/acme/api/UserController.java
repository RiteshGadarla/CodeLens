package com.acme.api;

import com.acme.domain.User;
import com.acme.domain.UserDto;
import com.acme.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return service.find(id);
    }

    @PostMapping
    public User create(@RequestBody UserDto dto) {
        return service.create(dto);
    }
}
