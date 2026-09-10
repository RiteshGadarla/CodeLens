package com.codelens.api.dto;

import com.codelens.auth.AuthService;
import com.codelens.domain.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email @Size(max = 320) String email,
                                  // bcrypt reads at most 72 bytes
                                  @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserDto(long id, String name, String email, Instant createdAt) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserDto user) {
        public static AuthResponse of(AuthService.Session s) {
            return new AuthResponse(s.token(), UserDto.of(s.user()));
        }
    }
}
