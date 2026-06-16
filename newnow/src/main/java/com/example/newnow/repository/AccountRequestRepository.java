package com.example.newnow.repository;

import com.example.newnow.model.AccountRequest;
import com.example.newnow.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRequestRepository extends JpaRepository<AccountRequest, Long> {
    List<AccountRequest> findByStatus(RequestStatus status);

    Optional<AccountRequest> findByEmail(String email);

    boolean existsByEmailAndStatus(String email, RequestStatus status);
}