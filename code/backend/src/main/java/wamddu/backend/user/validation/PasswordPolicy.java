package wamddu.backend.user.validation;

import java.nio.charset.StandardCharsets;

/** One password rule for signup, change and reset. */
public final class PasswordPolicy {
    public static final String MESSAGE =
            "비밀번호는 공백 없이 8자 이상, 영문·숫자 기준 최대 72자로 입력해 주세요. 한글 등은 더 짧을 수 있습니다.";

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        return password != null
                && password.codePointCount(0, password.length()) >= 8
                // BCrypt only reads the first 72 bytes; longer input is rejected rather than silently truncated.
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                        || Character.isISOControl(c) || c == 0xFEFF);
    }
}
