package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;
  private final NotificationService notificationService;
  private final Map<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

  @Autowired
  public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
    this.accountsRepository = accountsRepository;
    this.notificationService = notificationService;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  public void transfer(String accountFromId, String accountToId, BigDecimal amount) {

    log.info("Initiating transfer: from {} to {} amount {}", accountFromId, accountToId, amount);

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      log.error("Transfer failed: Amount must be positive. Provided amount: {}", amount);
      throw new IllegalArgumentException("Amount must be positive");
    }

    String firstId = accountFromId.compareTo(accountToId) < 0 ? accountFromId : accountToId;
    String secondId = accountFromId.compareTo(accountToId) < 0 ? accountToId : accountFromId;

    ReentrantLock firstLock = accountLocks.computeIfAbsent(firstId, id -> new ReentrantLock());
    ReentrantLock secondLock = accountLocks.computeIfAbsent(secondId, id -> new ReentrantLock());

    firstLock.lock();
    try {
      secondLock.lock();
    try {
      Optional<Account> accountFromOpt = Optional.ofNullable(this.accountsRepository.getAccount(accountFromId));
      Optional<Account> accountToOpt = Optional.ofNullable(this.accountsRepository.getAccount(accountToId));

      if (accountFromOpt.isEmpty() || accountToOpt.isEmpty()) {
        log.error("Transfer failed: Account(s) not found. accountFromId: {}, accountToId: {}", accountFromId, accountToId);
        throw new IllegalArgumentException("Invalid account ID(s)");
      }

      Account accountFrom = accountFromOpt.get();
      Account accountTo = accountToOpt.get();

      if (!accountFrom.hasSufficientBalance(amount)) {
        log.error("Transfer failed: Insufficient funds in account {}", accountFromId);
        throw new IllegalArgumentException("Insufficient balance");
      }

      accountFrom.setBalance(accountFrom.getBalance().subtract(amount));
      accountTo.setBalance(accountTo.getBalance().add(amount));

      this.accountsRepository.save(accountFrom);
      this.accountsRepository.save(accountTo);

      log.info("Transfer successful: {} transferred from {} to {} ", amount, accountFromId, accountToId) ;

      String transferDescriptionFrom = String.format("Transferred %s to account %s", amount, accountToId);
      String transferDescriptionTo = String.format("Received %s from account %s", amount, accountFromId);

      this.notificationService.notifyAboutTransfer(accountFrom, transferDescriptionFrom);
      this.notificationService.notifyAboutTransfer(accountTo, transferDescriptionTo);

      log.info("Notifications sent for transfer: {} from {} to {}", amount, accountFromId, accountToId);

    } finally {
      secondLock.unlock();
    }
    } finally {
      firstLock.unlock();
    }
  }
}
