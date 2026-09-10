package com.example.contract.auth.security;

import com.example.contract.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 登录态与权限校验拦截器：校验 token、还原当前用户与权限、按 @RequirePermission 放行/拒绝。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthInterceptor(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (isPublic(method)) {
            return true;
        }

        String userName = tokenService.resolveUser(extractToken(request)).orElse(null);
        if (userName == null) {
            writeError(response, 401, "未登录或登录已过期");
            return false;
        }

        List<String> permissionList = userRepository.findPermissionsByUserId(userName);
        Set<String> permissions = permissionList == null ? Set.of() : new HashSet<>(permissionList);
        AuthContext.set(userName, permissions);

        RequirePermission required = requiredPermission(method);
        if (required != null && !permissions.contains(required.value())) {
            writeError(response, 403, "无权限执行该操作");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublic(HandlerMethod method) {
        return method.getMethodAnnotation(PublicApi.class) != null
                || method.getBeanType().getAnnotation(PublicApi.class) != null;
    }

    private RequirePermission requiredPermission(HandlerMethod method) {
        RequirePermission onMethod = method.getMethodAnnotation(RequirePermission.class);
        return onMethod != null ? onMethod : method.getBeanType().getAnnotation(RequirePermission.class);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
