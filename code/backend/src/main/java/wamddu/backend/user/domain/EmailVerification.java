package wamddu.backend.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {
    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 254, nullable = false, unique = true)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private Purpose purpose = Purpose.EMAIL_VERIFICATION;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 100)
    private String codeHash;
    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant expiresAt;
    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant sentAt;
    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant windowStartedAt;
    @Column(nullable = false)
    private int sendCount;
    @Column(nullable = false)
    private int failedAttempts;
    @Column(columnDefinition = "datetime(6)")
    private Instant verifiedAt;
    @Column(columnDefinition = "datetime(6)")
    private Instant consumedAt;
}
