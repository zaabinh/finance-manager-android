package com.fintrack.api.service.category;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class CategorySeedService {
    private final JdbcTemplate jdbc;
    public CategorySeedService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public void initialize(UUID userId) {
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        boolean initialized = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT categories_initialized FROM fintrack.user_preferences WHERE user_id=? FOR UPDATE", Boolean.class,userId));
        if (initialized) return;
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM fintrack.categories WHERE user_id=?)", Boolean.class,userId));
        if (!exists) {
            String[][] seeds = {{"Ăn uống","EXPENSE","coffee"},{"Đi lại","EXPENSE","car"},
                    {"Mua sắm","EXPENSE","shopping_bag"},{"Hóa đơn","EXPENSE","receipt"},{"Khác","EXPENSE","more_horiz"},
                    {"Lương","INCOME","briefcase"},{"Thưởng","INCOME","gift"},{"Thu nhập khác","INCOME","more_horiz"}};
            for (int i=0;i<seeds.length;i++) jdbc.update(
                    "INSERT INTO fintrack.categories(user_id,name,category_type,icon_key,sort_order) VALUES (?,?,?,?,?)",
                    userId,seeds[i][0],seeds[i][1],seeds[i][2],i);
        }
        jdbc.update("UPDATE fintrack.user_preferences SET categories_initialized=true WHERE user_id=?",userId);
    }
}
