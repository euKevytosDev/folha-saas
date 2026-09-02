package com.sacolao.user.controller;

import com.sacolao.user.dto.CreateUserRequest;
import com.sacolao.user.dto.UpdateUserRequest;
import com.sacolao.user.dto.UserResponse;
import com.sacolao.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createMember(request);
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listCurrentTenant();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.getInCurrentTenant(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateInCurrentTenant(id, request);
    }
}
