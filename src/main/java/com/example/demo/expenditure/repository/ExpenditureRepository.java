package com.example.demo.expenditure.repository;

import com.example.demo.expenditure.model.Expenditure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenditureRepository extends JpaRepository<Expenditure,Long> {


    List<Expenditure> findByUserId(Long userId);
}
