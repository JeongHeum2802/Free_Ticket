package wamddu.backend.global.security;


import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        return path.equals("/api/auth/signup") ||
                path.equals("/api/auth/login")||
                path.equals("/api/auth/refresh") ||
                path.equals("/api/payments/confirm")||
                path.startsWith("/api/events");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = request.getHeader("Authorization");

        if(token == null){
            sendErrorMessage(response, HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_NOT_FOUND", "인증이 필요합니다.");
            return;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            if (jwtProvider.validateToken(token)) {
                Authentication authentication = jwtProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                sendErrorMessage(response, HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "유효하지 않은 Access Token 입니다.");
                return;
            }
        } catch (ExpiredJwtException ex) {
            sendErrorMessage(response, HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_EXPIRED", "Access Token이 만료되었습니다.");
            return;
        } catch (Exception ex) {
            sendErrorMessage(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류 발생");
            return;
        }
        filterChain.doFilter(request, response);
    }


    private void sendErrorMessage(HttpServletResponse response,
                             HttpStatus status,
                             String code,
                             String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
