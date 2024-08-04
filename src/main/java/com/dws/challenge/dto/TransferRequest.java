package com.dws.challenge.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class TransferRequest {

    private String accountFromId;
    private String accountToId;

    @Positive
    private BigDecimal amount;
}