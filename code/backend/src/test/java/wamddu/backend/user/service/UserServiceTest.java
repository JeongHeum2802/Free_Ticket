package wamddu.backend.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.dto.request.*;
import wamddu.backend.user.dto.response.*;
import wamddu.backend.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(1L, "홍길동", "encoded_password", "user@example.com", "01012345678", "customer_123", Role.USER);
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void signUp_Success() {
        // given
        SignupRequest dto = new SignupRequest("홍길동", "password123!", "user@example.com", "01012345678");

        given(userRepository.existsByEmail("user@example.com")).willReturn(false);
        given(userRepository.existsByPhonenumber("01012345678")).willReturn(false);
        given(passwordEncoder.encode("password123!")).willReturn("encoded_password");
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        SignupResponse response = userService.signUp(dto);

        // then
        assertThat(response.getMessage()).isEqualTo("회원가입이 완료되었습니다.");
        assertThat(response.getUser().getUsername()).isEqualTo("홍길동");
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signUp_EmailExists_ThrowsApiException() {
        // given
        SignupRequest dto = new SignupRequest("홍길동", "password123!", "user@example.com", "01012345678");

        given(userRepository.existsByEmail("user@example.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signUp(dto))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                });
    }

    @Test
    @DisplayName("로그인 성공 테스트")
    void login_Success() {
        // given
        LoginRequest dto = new LoginRequest("user@example.com", "password123!");

        given(userRepository.findByEmail("user@example.com")).willReturn(user);
        given(passwordEncoder.matches("password123!", "encoded_password")).willReturn(true);
        given(jwtProvider.generateJwtToken(1L, "USER")).willReturn("access_token");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh_token");

        // when
        LoginResult result = userService.login(dto);

        // then
        assertThat(result.response().getMessage()).isEqualTo("로그인에 성공했습니다.");
        assertThat(result.response().getAccessToken()).isEqualTo("access_token");
        assertThat(result.refreshToken()).isEqualTo("refresh_token");
        assertThat(result.response().getUser().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_InvalidPassword_ThrowsApiException() {
        // given
        LoginRequest dto = new LoginRequest("user@example.com", "wrong_password");

        given(userRepository.findByEmail("user@example.com")).willReturn(user);
        given(passwordEncoder.matches("wrong_password", "encoded_password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    @DisplayName("내 정보 조회 성공 테스트")
    void getMyInfo_Success() {
        // given
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("1")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        MyInfoResponse response = userService.getMyInfo(userDetails);

        // then
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getUsername()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("내 정보 수정 성공 테스트")
    void updateMyInfo_Success() {
        // given
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("1")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        UpdateMyInfoRequest request = new UpdateMyInfoRequest("김철수", null, null);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        UpdateMyInfoResponse response = userService.updateMyInfo(request, userDetails);

        // then
        assertThat(response.getMessage()).isEqualTo("회원 정보가 수정되었습니다.");
    }

    @Test
    @DisplayName("비밀번호 변경 성공 테스트")
    void updateMyPassword_Success() {
        // given
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("1")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest("password123!", "newPassword123!");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded_password")).willReturn(true);
        given(passwordEncoder.matches("newPassword123!", "encoded_password")).willReturn(false);
        given(passwordEncoder.encode("newPassword123!")).willReturn("new_encoded_password");

        // when
        MessageResponse response = userService.updateMyPassword(request, userDetails);

        // then
        assertThat(response.getMessage()).isEqualTo("비밀번호가 변경되었습니다.");
    }

    @Test
    @DisplayName("회원 탈퇴 성공 테스트")
    void deleteMyAccount_Success() {
        // given
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("1")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        DeleteAccountRequest request = new DeleteAccountRequest("password123!");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded_password")).willReturn(true);

        // when
        MessageResponse response = userService.deleteMyAccount(request, userDetails);

        // then
        assertThat(response.getMessage()).isEqualTo("회원 탈퇴가 완료되었습니다.");
    }
}
