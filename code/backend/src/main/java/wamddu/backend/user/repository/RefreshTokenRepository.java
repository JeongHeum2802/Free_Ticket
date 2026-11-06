package wamddu.backend.user.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import wamddu.backend.user.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.id = :id")
    Optional<RefreshToken> findForUpdate(String id);

    @Modifying
    @Query("delete from RefreshToken token where token.userId = :userId")
    void revokeAllByUserId(Long userId);

    @Query("select token.id from RefreshToken token where token.expiresAt <= :cutoff")
    List<String> findExpiredIds(Instant cutoff, Pageable pageable);

    @Modifying
    @Query("delete from RefreshToken token where token.id in :ids and token.expiresAt <= :cutoff")
    int deleteExpiredByIds(List<String> ids, Instant cutoff);
}
