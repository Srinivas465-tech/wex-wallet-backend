package com.example.demo.expenditure.controller;

import com.example.demo.expenditure.dto.ExpenditureDto;
import com.example.demo.expenditure.service.ExpenditureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/expenditures")
@RequiredArgsConstructor
public class ExpenditureController {

    private final ExpenditureService expenditureService;

    @PostMapping
    public void addExpenditure(@PathVariable Long userId,
                               @RequestBody ExpenditureDto.ExpenditureRequest request){
        expenditureService.addExpenditure(userId, request);
    }

    @PatchMapping("/{id}")
    public void updateExpenditure(@PathVariable Long id,
                                  @RequestBody ExpenditureDto.ExpenditureRequest request){
        expenditureService.updateExpenditure(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteExpenditure(@PathVariable Long id){
        expenditureService.deleteExpenditure(id);
    }

    @GetMapping
    public List<ExpenditureDto.ExpenditureResponse>
    getAllExpenditures(@PathVariable Long userId){
        return expenditureService.getAllExpenditures(userId);
    }

    @GetMapping("/{id}")
    public ExpenditureDto.ExpenditureResponse getExpenditureById(@PathVariable Long id){
        return expenditureService.getExpenditureById(id);
    }

}
