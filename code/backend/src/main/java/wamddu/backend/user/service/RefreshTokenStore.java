package wamddu.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.user.domain.RefreshToken;
import wamddu.backend.user.repository.RefreshTokenRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenStore {
    private final RefreshTokenRepository repository;

    public void save(String familyId, Long userId, String token, Instant expiresAt) {
        // ponytail: cleanup on login; batch expiry cleanup if the indexed delete becomes too large.
        repository.deleteExpired(Instant.now());
        repository.save(new RefreshToken(familyId, userId, hash(token), expiresAt));
    }

    // Commit replay revocation even when the caller subsequently throws a 401 ApiException.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rotate(String familyId, Long userId, String previous, String replacement) {
        var found = repository.findForUpdate(familyId);
        if (found.isEmpty()) return false;
        var stored = found.get();
        if (!stored.getUserId().equals(userId)) return false;
        if (!stored.getExpiresAt().isAfter(Instant.now()) || !MessageDigest.isEqual(
                stored.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(previous).getBytes(StandardCharsets.US_ASCII))) {
            repository.delete(stored);
            return false;
        }
        stored.setTokenHash(hash(replacement));
        return true;
    }

    public void revoke(String familyId) {
        repository.findForUpdate(familyId).ifPresent(repository::delete);
    }

    public void revokeAll(Long userId) {
        repository.revokeAllByUserId(userId);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
