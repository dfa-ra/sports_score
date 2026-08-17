package com.studentleague.users.service;

import com.studentleague.auth.dto.UserResponse;
import com.studentleague.common.exception.ApiException;
import com.studentleague.users.dto.UpdateUserRequest;
import com.studentleague.users.entity.User;
import com.studentleague.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserAdminService {

    private final UserRepository userRepository;

    public UserAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled());
    }
}
