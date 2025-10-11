package com.example.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.dto.Client;
import com.example.service.AccountService;
import com.example.service.ClientService;
import com.example.utils.Const;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JWTAuthorizeFilter extends OncePerRequestFilter {

    @Resource
    JwtUtils utils;

    @Resource
    ClientService service;

    @Resource
    AccountService accountService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // ✅ 放行 CORS 预检，避免被你自己的过滤器误拦
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || org.springframework.web.cors.CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String uri = request.getRequestURI();

        if (uri.startsWith("/monitor")) {
            // 探针侧：/monitor/** 用的是“客户端注册 token”，不是 JWT
            if (!uri.endsWith("/register")) {
                Client client = service.findClientByToken(authorization); // 按原样传入
                if (client == null) {
                    write401(response, "未注册");
                    return;
                } else {
                    request.setAttribute(Const.ATTR_CLIENT, client);
                }
            }
        } else {
            // 用户侧：/api/** 用 JWT，utils.resolveJwt 会自己从 "Bearer xxx" 中剥离
            DecodedJWT jwt = utils.resolveJwt(authorization);
            if (jwt != null) {
                UserDetails user = utils.toUser(jwt);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                request.setAttribute(Const.ATTR_USER_ID, utils.toId(jwt));
                request.setAttribute(Const.ATTR_USER_ROLE,
                        new ArrayList<>(user.getAuthorities()).get(0).getAuthority());

                // 终端会话权限校验：/terminal/{clientId}
                if (uri.startsWith("/terminal/")) {
                    String[] parts = uri.split("/");
                    if (parts.length >= 3) {
                        int clientId = Integer.parseInt(parts[2]);
                        int uid = (int) request.getAttribute(Const.ATTR_USER_ID);
                        String role = (String) request.getAttribute(Const.ATTR_USER_ROLE);
                        if (!accessShell(uid, role, clientId)) {
                            write401(response, "无权访问");
                            return;
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void write401(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(RestBean.failure(401, msg).asJsonString());
    }

    private boolean accessShell(int userId, String userRole, int clientId) {
        String pureRole = userRole != null && userRole.startsWith("ROLE_")
                ? userRole.substring(5) : userRole;
        if (Const.ROLE_ADMIN.equals(pureRole)) {
            return true;
        } else {
            Account account = accountService.getById(userId);
            return account != null && account.getClientList().contains(clientId);
        }
    }
}
