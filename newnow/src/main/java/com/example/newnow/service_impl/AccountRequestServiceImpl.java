package com.example.newnow.service_impl;

import com.example.newnow.model.AccountRequest;
import com.example.newnow.model.RequestStatus;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.AccountRequestRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.service.AccountRequestService;
import com.example.newnow.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AccountRequestServiceImpl implements AccountRequestService {

    @Autowired
    private AccountRequestRepository accountRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Override
    public List<AccountRequest> findAll() {
        return accountRequestRepository.findAll();
    }

    @Override
    public List<AccountRequest> findByStatus(RequestStatus status) {
        return accountRequestRepository.findByStatus(status);
    }

    @Override
    public Optional<AccountRequest> findById(Long id) {
        return accountRequestRepository.findById(id);
    }

    @Override
    public AccountRequest save(AccountRequest request) {
        if (request.getPassword() != null && !request.getPassword().startsWith("$2a$")) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return accountRequestRepository.save(request);
    }

    @Override
    public AccountRequest updateStatus(Long id, RequestStatus status) {
        AccountRequest request = accountRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zahtev nije pronađen"));

        request.setStatus(status);
        accountRequestRepository.save(request);

        //odobren - kreiram User nalog i pošalji email
        if (status == RequestStatus.APPROVED) {
            User user = new User();
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());
            user.setFullName(request.getFullName());
            user.setRole(Role.USER);
            user.setEnabled(true);
            userRepository.save(user);

            emailService.sendAccountApprovedEmail(request.getEmail(), request.getFullName());
        }
        //odbijeno - samo email
        else if (status == RequestStatus.REJECTED) {
            emailService.sendAccountRejectedEmail(request.getEmail(), request.getFullName());
        }

        return request;
    }
}