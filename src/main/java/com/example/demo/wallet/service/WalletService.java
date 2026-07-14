package com.example.demo.wallet.service;

import com.example.demo.wallet.dto.WalletDto;
import com.example.demo.wallet.model.Wallet;
import com.example.demo.wallet.model.WalletBalance;
import com.example.demo.wallet.repository.WalletBalanceRepository;
import com.example.demo.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletRepository walletRepository;

    public WalletBalance getWallet(Long id){
        return walletBalanceRepository.findById(id).orElseThrow(()->new RuntimeException("Record not found"));
    }

    public Wallet getWalletByUserId(Long userId){
        Optional<Wallet> optionalWallet = walletRepository.findByUserId(userId);
        if(optionalWallet.isEmpty()){
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setAmount(BigDecimal.valueOf(0.0));
            return walletRepository.save(wallet);
        }
            return optionalWallet.get();
    }

    public void addMoneyToWallet(Long userId, WalletDto.walletRequest request) {
        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setAmount(request.amount());
        walletBalance.setUserId(userId);
        walletBalanceRepository.save(walletBalance);

        Wallet wallet= getWalletByUserId(userId);
        wallet.setAmount(wallet.getAmount().add(request.amount()));
        walletRepository.save(wallet);
    }

    public void updateWallet(Long id, WalletDto.walletRequest request) {

        WalletBalance walletBalance = getWallet(id);
        Wallet wallet = getWalletByUserId(walletBalance.getUserId());
        BigDecimal delta = request.amount().subtract(walletBalance.getAmount());
        wallet.setAmount(wallet.getAmount().add(delta));
        walletRepository.save(wallet);

        walletBalance.setAmount(request.amount());
        walletBalanceRepository.save(walletBalance);
    }


    public List<WalletDto.walletResponse> getWalletHistory(Long userId) {
        List<WalletBalance> wallets = walletBalanceRepository.findByUserId(userId);
        if(wallets.isEmpty()){
            List.of();
        }
        return wallets.stream().map(this::buildWalletResponse).toList();
    }

    public WalletDto.walletResponse buildWalletResponse(WalletBalance wallet){
        return WalletDto.walletResponse.builder()
                .id(wallet.getId())
                .dateTime(wallet.getCreatedAt())
                .amount(wallet.getAmount())
                .build();
    }

    public BigDecimal getWalletTotal(Long userId) {
        Wallet wallet = getWalletByUserId(userId);
        return wallet.getAmount();
    }
}
