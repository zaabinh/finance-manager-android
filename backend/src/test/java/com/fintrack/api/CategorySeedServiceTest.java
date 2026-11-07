package com.fintrack.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.service.category.CategorySeedService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CategorySeedServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UUID owner=UUID.randomUUID();
    @Test void markerPreventsReseedingEvenWhenAllCategoriesAreArchived() {
        when(jdbc.queryForObject(contains("FOR UPDATE"),eq(Boolean.class),eq(owner))).thenReturn(true);
        new CategorySeedService(jdbc).initialize(owner);
        verify(jdbc,never()).update(contains("INSERT INTO fintrack.categories"),any(),any(),any(),any(),any());
        verify(jdbc,never()).queryForObject(contains("EXISTS"),eq(Boolean.class),eq(owner));
    }
    @Test void existingUserCategoriesArePreserved() {
        when(jdbc.queryForObject(contains("FOR UPDATE"),eq(Boolean.class),eq(owner))).thenReturn(false);
        when(jdbc.queryForObject(contains("EXISTS"),eq(Boolean.class),eq(owner))).thenReturn(true);
        new CategorySeedService(jdbc).initialize(owner);
        verify(jdbc,never()).update(contains("INSERT INTO fintrack.categories"),any(),any(),any(),any(),any());
        verify(jdbc).update(contains("SET categories_initialized=true"),eq(owner));
    }
    @Test void genuinelyNewUserReceivesEightOwnedSeeds() {
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(owner))).thenReturn(false);
        new CategorySeedService(jdbc).initialize(owner);
        verify(jdbc,times(8)).update(contains("INSERT INTO fintrack.categories"),eq(owner),anyString(),anyString(),anyString(),anyInt());
        verify(jdbc).update(contains("SET categories_initialized=true"),eq(owner));
    }
}
