package wamddu.backend.user.service;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.dto.request.ChangePasswordRequest;
import wamddu.backend.user.dto.request.LoginRequest;
import wamddu.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:token_login_race;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500",
        "spring.datasource.hikari.maximum-pool-size=3"
})
class TokenLoginRaceTest {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean RefreshTokenStore store;

    @Test
    void passwordChangeCannotPassAnOldPasswordLoginAndLeaveItsRefreshActive() throws Exception {
        var user = users.saveAndFlush(User.builder().username("Race user").email(UUID.randomUUID() + "@example.test")
                .password(encoder.encode("oldPassword123!")).build());
        var checkedPassword = new CountDownLatch(1);
        var releaseLogin = new CountDownLatch(1);
        doAnswer(invocation -> {
            checkedPassword.countDown();
            if (!releaseLogin.await(10, TimeUnit.SECONDS)) throw new AssertionError("login release timeout");
            return invocation.callRealMethod();
        }).when(store).save(anyString(), eq(user.getId()), anyString(), any());
        var pool = Executors.newSingleThreadExecutor();
        var pendingLogin = pool.submit(() -> service.login(new LoginRequest(user.getEmail(), "oldPassword123!")));
        var principal = org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password("").authorities("ROLE_USER").build();
        try {
            assertThat(checkedPassword.await(5, TimeUnit.SECONDS)).isTrue();
            // H2's bounded lock timeout gives deterministic evidence that login holds the credential row.
            assertThatThrownBy(() -> service.updateMyPassword(
                    new ChangePasswordRequest("oldPassword123!", "newPassword123!"), principal))
                    .isInstanceOf(PessimisticLockingFailureException.class);
        } finally {
            releaseLogin.countDown();
            pool.shutdown();
        }
        var login = pendingLogin.get(10, TimeUnit.SECONDS);
        service.updateMyPassword(new ChangePasswordRequest("oldPassword123!", "newPassword123!"), principal);
        assertThatThrownBy(() -> service.reissueToken(login.refreshToken())).isInstanceOf(ApiException.class);
    }
}
