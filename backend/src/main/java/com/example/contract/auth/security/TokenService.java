package com.example.contract.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 基于内存的登录 token 管理（单实例部署）。token 有效期内有效，后端重启后需重新登录。 */
@Service
public class TokenService {

    private static final long TTL_MILLIS = Duration.ofHours(24).toMillis();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public String issue(String userName) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        tokens.put(token, new TokenEntry(userName, System.currentTimeMillis() + TTL_MILLIS));
        return token;
    }

    public Optional<String> resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        TokenEntry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt < System.currentTimeMillis()) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.userName);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            tokens.remove(token);
        }
    }

    private record TokenEntry(String userName, long expiresAt) {
    }
}
