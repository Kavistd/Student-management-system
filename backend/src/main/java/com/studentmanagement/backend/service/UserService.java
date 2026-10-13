
package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.UserRequest;
import com.studentmanagement.backend.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserRequest request);

    List<UserResponse> getAllUsers();

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);
}
