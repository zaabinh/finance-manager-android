package com.example.fintrack.app.core.error;

/** Ngoại lệ dùng chung trong toàn bộ nghiệp vụ ứng dụng */
public final class AppFailure extends RuntimeException {
    public final AppProblem problem;

    public AppFailure(AppProblem problem) {
        super(problem.name());
        this.problem = problem;
    }
}
