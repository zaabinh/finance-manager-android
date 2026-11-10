package com.fintrack.api.controller;

import com.fintrack.api.dto.request.category.CategoryRequest;
import com.fintrack.api.dto.request.category.CategoryStatusRequest;
import com.fintrack.api.dto.response.category.CategoryResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.category.CategoryService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/categories")
@io.swagger.v3.oas.annotations.tags.Tag(name="Categories")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class CategoryController {
    private final CategoryService categories;
    public CategoryController(CategoryService categories) { this.categories=categories; }
    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) String status,@RequestParam(required=false) String categoryType) {
        return categories.list(user.id(),status,categoryType);
    }
    @GetMapping("/{id}")
    public CategoryResponse detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return categories.detail(user.id(),id); }
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody CategoryRequest body) {
        CategoryResponse result=categories.create(user.id(),body);
        return ResponseEntity.created(URI.create("/api/v1/categories/"+result.categoryId())).body(result);
    }
    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@RequestBody CategoryRequest body) {
        return categories.update(user.id(),id,body);
    }
    @PatchMapping("/{id}/status")
    public CategoryResponse status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@RequestBody CategoryStatusRequest body) {
        return categories.status(user.id(),id,body);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) {
        categories.archive(user.id(),id); return ResponseEntity.noContent().build();
    }
}
