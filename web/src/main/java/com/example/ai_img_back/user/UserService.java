package com.example.ai_img_back.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public User create(String email, String displayName) {
        return userRepository.create(email, displayName);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Пользователь с id " + id + " не найден"
                ));
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public void delete(Long id) {
        getById(id);
        userRepository.delete(id);
    }
}
