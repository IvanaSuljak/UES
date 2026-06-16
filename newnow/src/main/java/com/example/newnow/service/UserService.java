package com.example.newnow.service;

import com.example.newnow.model.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findByEmail(String email);
    User save(User user);
    void deleteById(Long id);
    boolean changePassword(String email, String oldPassword, String newPassword);

}
