package com.example.fintrack.app.finance.domain;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Domain failure metadata; presentation uses AppError rather than exception text. */
public final class FinanceFailure extends RuntimeException {
    public final FinanceProblem problem;
    public final String code;
    public final List<String> fields;
    public final Long shortfallMinor;
    public FinanceFailure(FinanceProblem problem) { this(problem, null, Collections.emptyList(), null); }
    public FinanceFailure(FinanceProblem problem, Throwable cause) { this(problem); initCause(cause); }
    public FinanceFailure(FinanceProblem problem, String code, List<String> fields, Long shortfallMinor) {
        super(problem.name()); this.problem = problem; this.code = code;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields)); this.shortfallMinor = shortfallMinor;
    }
}
