package com.example.demo.wallet.repository;

import com.example.demo.wallet.model.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {

    List<WalletBalance> findByUserIdOrderByCreatedAtDesc(Long userId);
}
