package com.example.demo.expenditure.service;

import com.example.demo.expenditure.dto.ExpenditureDto;
import com.example.demo.expenditure.model.Expenditure;
import com.example.demo.expenditure.repository.ExpenditureRepository;
import com.example.demo.wallet.model.Wallet;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpenditureService {

    private final ExpenditureRepository expenditureRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    private Expenditure getOrThrow(Long id){
        return expenditureRepository.findById(id).orElseThrow(()-> new RuntimeException("Record not found"));
    }

    public void addExpenditure(Long userId, ExpenditureDto.ExpenditureRequest request) {
        Expenditure expenditure = new Expenditure();
        expenditure.setAmount(request.amount());
        expenditure.setCategory(request.category());
        expenditure.setUserId(userId);

        // Update wallet balance
        Wallet wallet = walletService.getWalletByUserId(userId);
        var remainingBalance = wallet.getAmount().subtract(request.amount());
        expenditure.setRemainingBalance(remainingBalance);
        wallet.setAmount(remainingBalance);
        expenditureRepository.save(expenditure);
        walletRepository.save(wallet);
    }

    public void updateExpenditure(Long id, ExpenditureDto.ExpenditureRequest request) {
        Expenditure expenditure = getOrThrow(id);
        expenditure.setAmount(request.amount());
        expenditure.setCategory(request.category());
        expenditureRepository.save(expenditure);

        // Update wallet balance
        Wallet wallet = walletService.getWalletByUserId(expenditure.getUserId());
        BigDecimal delta = request.amount().subtract(expenditure.getAmount());
        wallet.setAmount(wallet.getAmount().subtract(delta));
        walletRepository.save(wallet);
    }

    public void deleteExpenditure(Long id) {
        getOrThrow(id);
        expenditureRepository.deleteById(id);
        // Update wallet balance

        Expenditure expenditure = getOrThrow(id);
        Wallet wallet = walletService.getWalletByUserId(expenditure.getUserId());
        wallet.setAmount(wallet.getAmount().add(expenditure.getAmount()));
        walletRepository.save(wallet);
    }

    public List<ExpenditureDto.ExpenditureResponse> getAllExpenditures(Long userId) {
        List<Expenditure> expenditureList = expenditureRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if(expenditureList.isEmpty()){
            return List.of();
        }
        return expenditureList.stream().map(this::buildResponse).toList();
    }

    public ExpenditureDto.ExpenditureResponse getExpenditureById(Long id) {
        Expenditure expenditure = getOrThrow(id);
        return buildResponse(expenditure);
    }

    public ExpenditureDto.ExpenditureResponse buildResponse(Expenditure expenditure){
        return ExpenditureDto.ExpenditureResponse.builder()
                .id(expenditure.getId())
                .amount(expenditure.getAmount())
                .remainingAmount(expenditure.getRemainingBalance())
                .category(expenditure.category)
                .dateTime(expenditure.getCreatedAt())
                .build();
    }
}
