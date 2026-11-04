package com.fintrack.api.controller;

import com.fintrack.api.dto.response.ApiResponse;
import com.fintrack.api.dto.response.user.UserResponse;
import com.fintrack.api.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class UserController {
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        var user = principal.user();
        String name = user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
        return new ApiResponse<>(true, "User profile", new UserResponse(user.id(), user.username(), name, user.email()), 200);
    }
}
