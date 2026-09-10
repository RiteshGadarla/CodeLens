package com.codelens.auth;

import com.codelens.domain.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AuthProperties props;

    public TokenService(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    // subject = user id
    public String issue(AppUser user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("codelens")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .claim("name", user.getName())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
