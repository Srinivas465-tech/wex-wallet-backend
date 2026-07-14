package com.example.demo.wallet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletDto() {

    public record walletRequest(@NotNull BigDecimal amount){}

    @Builder
    public record walletResponse(
            Long id,
            BigDecimal amount,
            LocalDateTime dateTime){}
}
