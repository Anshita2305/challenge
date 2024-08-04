package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

  @Autowired
  private AccountsService accountsService;

  @MockBean
  private AccountsRepository accountsRepository;

  @MockBean
  private NotificationService notificationService;

  @BeforeEach
  void setUp() throws DuplicateAccountIdException {
    // Clear repository before each test
    when(accountsRepository.getAccount("1"))
            .thenReturn(new Account("1", BigDecimal.valueOf(1000)));
    when(accountsRepository.getAccount("2"))
            .thenReturn(new Account("2", BigDecimal.valueOf(500)));
  }

//  @Test
//  void addAccount() {
//    Account account = new Account("Id-123");
//    account.setBalance(new BigDecimal(1000));
//    this.accountsService.createAccount(account);
//
//    assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
//  }

  @Test
  void addAccount() throws DuplicateAccountIdException {

    Account account = new Account("Id-123");
    account.setBalance(new BigDecimal(1000));

    doNothing().when(accountsRepository).createAccount(account);
    when(accountsRepository.getAccount("Id-123")).thenReturn(account);

    accountsService.createAccount(account);

    verify(accountsRepository).createAccount(account);
    assertThat(accountsService.getAccount("Id-123")).isEqualTo(account);
  }

//  @Test
//  void addAccount_failsOnDuplicateId() {
//    String uniqueId = "Id-" + System.currentTimeMillis();
//    Account account = new Account(uniqueId);
//    this.accountsService.createAccount(account);
//
//    try {
//      this.accountsService.createAccount(account);
//      fail("Should have failed when adding duplicate account");
//    } catch (DuplicateAccountIdException ex) {
//      assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
//    }
//  }

  @Test
  void addAccount_failsOnDuplicateId() throws DuplicateAccountIdException {
    String uniqueId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueId);
    account.setBalance(new BigDecimal(1000));

    doThrow(new DuplicateAccountIdException("Account id " + uniqueId + " already exists!"))
            .when(accountsRepository).createAccount(account);

    try {
      accountsService.createAccount(account);
      fail("Should have failed when adding duplicate account");
    } catch (DuplicateAccountIdException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
    }
  }

  @Test
  void transfer_successful() {
    Account accountFrom = new Account("1", BigDecimal.valueOf(1000));
    Account accountTo = new Account("2", BigDecimal.valueOf(500));

    when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
    when(accountsRepository.getAccount("2")).thenReturn(accountTo);

    accountsService.transfer("1", "2", BigDecimal.valueOf(200));

    verify(accountsRepository).save(argThat(account ->
            "1".equals(account.getAccountId()) && BigDecimal.valueOf(800).equals(account.getBalance())));
    verify(accountsRepository).save(argThat(account ->
            "2".equals(account.getAccountId()) && BigDecimal.valueOf(700).equals(account.getBalance())));

    verify(notificationService).notifyAboutTransfer(eq(accountFrom), any(String.class));
    verify(notificationService).notifyAboutTransfer(eq(accountTo), any(String.class));
  }

  @Test
  void transfer_insufficientBalance() {
    Account accountFrom = new Account("1", BigDecimal.valueOf(100));
    Account accountTo = new Account("2", BigDecimal.valueOf(500));

    when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
    when(accountsRepository.getAccount("2")).thenReturn(accountTo);

    try {
      accountsService.transfer("1", "2", BigDecimal.valueOf(200));
    } catch (IllegalArgumentException e) {
      assertEquals("Insufficient balance", e.getMessage());
    }

    verify(accountsRepository, never()).save(any(Account.class));
    verify(notificationService, never()).notifyAboutTransfer(any(Account.class), any(String.class));
  }

  @Test
  public void transfer_invalidSourceAccountId() {
    Account accountTo = new Account("2", BigDecimal.valueOf(500));

    when(accountsRepository.getAccount("2")).thenReturn(accountTo);
    when(accountsRepository.getAccount("1")).thenReturn(null); // Source account is invalid

    try {
      accountsService.transfer("1", "2", BigDecimal.valueOf(100));
      fail("Expected IllegalArgumentException to be thrown");
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid account ID(s)", e.getMessage());
    }

    verify(accountsRepository, never()).save(any(Account.class));
    verify(notificationService, never()).notifyAboutTransfer(any(Account.class), any(String.class));
  }

  @Test
  public void transfer_invalidDestinationAccountId() {
    Account accountFrom = new Account("1", BigDecimal.valueOf(100));

    when(accountsRepository.getAccount("1")).thenReturn(accountFrom);
    when(accountsRepository.getAccount("2")).thenReturn(null); // Destination account is invalid

    try {
      accountsService.transfer("1", "2", BigDecimal.valueOf(100));
      fail("Expected IllegalArgumentException to be thrown");
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid account ID(s)", e.getMessage());
    }

    verify(accountsRepository, never()).save(any(Account.class));
    verify(notificationService, never()).notifyAboutTransfer(any(Account.class), any(String.class));
  }

  @Test
  public void transfer_bothAccountIdsInvalid() {
    when(accountsRepository.getAccount("1")).thenReturn(null); // Both accounts are invalid
    when(accountsRepository.getAccount("2")).thenReturn(null);

    try {
      accountsService.transfer("1", "2", BigDecimal.valueOf(100));
      fail("Expected IllegalArgumentException to be thrown");
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid account ID(s)", e.getMessage());
    }

    verify(accountsRepository, never()).save(any(Account.class));
    verify(notificationService, never()).notifyAboutTransfer(any(Account.class), any(String.class));
  }

  @Test
  void transfer_concurrent_successful() throws InterruptedException {
    Account accountIdFrom = new Account("1", BigDecimal.valueOf(1000));
    Account accountIdTo = new Account("2", BigDecimal.valueOf(500));

    when(accountsRepository.getAccount("1")).thenReturn(accountIdFrom);
    when(accountsRepository.getAccount("2")).thenReturn(accountIdTo);

    var executor = Executors.newFixedThreadPool(2);

    Runnable transferTask = () -> {
      try {
        accountsService.transfer("1", "2", BigDecimal.valueOf(200));
      } catch (Exception e) {
        fail("Transfer failed: " + e.getMessage());
      }
    };

    executor.submit(transferTask);
    executor.submit(transferTask);

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);

    // Verify repository interactions
    verify(accountsRepository, times(2)).getAccount(accountIdFrom.getAccountId());
    verify(accountsRepository, times(2)).getAccount(accountIdTo.getAccountId());

    assertEquals(BigDecimal.valueOf(600),accountIdFrom.getBalance());
    assertEquals(BigDecimal.valueOf(900),accountIdTo.getBalance());

    // Verify notifications
    verify(notificationService, times(2)).notifyAboutTransfer(eq(accountIdFrom), any(String.class));
    verify(notificationService, times(2)).notifyAboutTransfer(eq(accountIdTo), any(String.class));
  }

}
