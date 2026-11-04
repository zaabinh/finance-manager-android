package com.example.fintrack.app.finance.domain;

/** Ngoại lệ dùng chung trong toàn bộ nghiệp vụ tài chính */
public final class FinanceFailure extends RuntimeException {
    public final FinanceProblem problem;

    public FinanceFailure(FinanceProblem problem) {
        super(problem.name());
        this.problem = problem;
    }
}
