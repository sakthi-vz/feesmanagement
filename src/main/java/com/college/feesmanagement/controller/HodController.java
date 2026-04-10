package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.Hod;
import com.college.feesmanagement.repository.HodRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hods")
public class HodController {

    private final HodRepository hodRepository;

    public HodController(HodRepository hodRepository) {
        this.hodRepository = hodRepository;
    }

    @GetMapping("/all")
    public ResponseEntity<List<Hod>> getAllHods() {
        return ResponseEntity.ok(hodRepository.findAll());
    }
}