package com.reelshort.backend.points;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyEarningRuleRepository extends JpaRepository<DailyEarningRule, LocalDate> {
}
