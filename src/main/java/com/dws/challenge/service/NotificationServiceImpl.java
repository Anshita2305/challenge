package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void notifyAboutTransfer(Account account, String transferDescription) {
        // Implement the notification logic
    }
}