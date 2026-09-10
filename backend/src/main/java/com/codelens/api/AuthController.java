package com.codelens.api;

import com.codelens.api.dto.AuthDtos.AuthResponse;
import com.codelens.api.dto.AuthDtos.LoginRequest;
import com.codelens.api.dto.AuthDtos.RegisterRequest;
import com.codelens.api.dto.AuthDtos.UserDto;
import com.codelens.auth.AuthService;
import com.codelens.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "Auth", description = "Accounts and bearer tokens")
public class AuthController {

    private final AuthService auth;
    private final boolean allowLocalPaths;

    public AuthController(AuthService auth, @Value("${codelens.allow-local-paths:true}") boolean allowLocalPaths) {
        this.auth = auth;
        this.allowLocalPaths = allowLocalPaths;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and return a token")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return AuthResponse.of(auth.register(req.name(), req.email(), req.password()));
    }

    @PostMapping("/api/auth/login")
    @Operation(summary = "Exchange email and password for a token")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return AuthResponse.of(auth.login(req.email(), req.password()));
    }

    @GetMapping("/api/auth/me")
    public UserDto me() {
        return UserDto.of(auth.get(CurrentUser.id()));
    }

    // public: lets the ui hide options the server refuses
    @GetMapping("/api/config")
    public Map<String, Object> config() {
        return Map.of("allowLocalPaths", allowLocalPaths);
    }
}
