package com.example.demo.wallet.controller;

import com.example.demo.wallet.dto.WalletDto;
import com.example.demo.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public void addMoneyToWallet(@PathVariable Long userId, @RequestBody WalletDto.walletRequest request){
        walletService.addMoneyToWallet(userId, request);
    }

    @PatchMapping("/{id}")
    public void updateWallet(@PathVariable Long id ,@RequestBody WalletDto.walletRequest request){
        walletService.updateWallet(id, request);
    }

    @GetMapping
    public List<WalletDto.walletResponse> getWalletHistory(@PathVariable Long userId){
        return walletService.getWalletHistory(userId);
    }

    @GetMapping("/total")
    public BigDecimal getTotalWalletBalance(@PathVariable Long userId) {
        return walletService.getWalletTotal(userId);
    }
}
