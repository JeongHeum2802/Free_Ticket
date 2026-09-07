package wamddu.backend.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.loginRequestDTO;
import wamddu.backend.user.domain.signUpRequestDTO;
import wamddu.backend.user.repository.userRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private userRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private userService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(1L, "홍길동", "encoded_password", "user@example.com", "01012345678", "customer_123", Role.USER);
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void signUp_Success() {
        // given
        signUpRequestDTO dto = new signUpRequestDTO();
        dto.setUsername("홍길동");
        dto.setPassword("password123!");
        dto.setEmail("user@example.com");
        dto.setPhonenumber("01012345678");

        given(userRepository.existsByEmail("user@example.com")).willReturn(false);
        given(userRepository.existsByPhonenumber("01012345678")).willReturn(false);
        given(passwordEncoder.encode("password123!")).willReturn("encoded_password");
        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        ResponseEntity<Map<String, Object>> response = userService.signUp(dto);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("message", "회원가입이 완료되었습니다.");
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signUp_EmailExists_ReturnsConflict() {
        // given
        signUpRequestDTO dto = new signUpRequestDTO();
        dto.setEmail("user@example.com");

        given(userRepository.existsByEmail("user@example.com")).willReturn(true);

        // when
        ResponseEntity<Map<String, Object>> response = userService.signUp(dto);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("로그인 성공 테스트")
    void login_Success() {
        // given
        loginRequestDTO dto = new loginRequestDTO();
        dto.setEmail("user@example.com");
        dto.setPassword("password123!");

        given(userRepository.findByEmail("user@example.com")).willReturn(user);
        given(passwordEncoder.matches("password123!", "encoded_password")).willReturn(true);
        given(jwtProvider.generateJwtToken(1L, "USER")).willReturn("access_token");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh_token");

        // when
        ResponseEntity<Map<String, Object>> response = userService.login(dto);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("accessToken", "access_token");
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
        ResponseEntity<Map<String, Object>> response = userService.getMyInfo(userDetails);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("user");
    }
}
