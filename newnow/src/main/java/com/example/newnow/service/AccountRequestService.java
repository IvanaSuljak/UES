package com.example.newnow.service;

import com.example.newnow.model.AccountRequest;
import com.example.newnow.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface AccountRequestService {

    List<AccountRequest> findAll();

    List<AccountRequest> findByStatus(RequestStatus status);

    Optional<AccountRequest> findById(Long id);

    AccountRequest save(AccountRequest request);

    AccountRequest updateStatus(Long id, RequestStatus status);
}