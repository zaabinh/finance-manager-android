package com.fintrack.api.repository;

import com.fintrack.api.dto.response.category.CategoryResponse;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class CategoryRepository {
    private final JdbcTemplate jdbc;
    public CategoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static CategoryResponse map(ResultSet rs, int row) throws SQLException {
        return new CategoryResponse(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("category_type"), rs.getString("icon_key"), rs.getString("color_hex"),
                rs.getInt("sort_order"), rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    public List<CategoryResponse> list(UUID owner, String status, String type) {
        return jdbc.query("SELECT * FROM fintrack.categories WHERE user_id=?"
                + (status.equals("ACTIVE") ? " AND archived_at IS NULL" : status.equals("ARCHIVED") ? " AND archived_at IS NOT NULL" : "")
                + (type == null ? "" : " AND category_type=?") + " ORDER BY sort_order,name,id",
                CategoryRepository::map, type == null ? new Object[]{owner} : new Object[]{owner,type});
    }
    public CategoryResponse find(UUID owner, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM fintrack.categories WHERE user_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
                CategoryRepository::map, owner,id).stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public CategoryResponse create(UUID owner, String name, String type, String icon, String color, int sort) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type,icon_key,color_hex,sort_order) VALUES (?,?,?,?,?,?,?)",
                id,owner,name,type,icon,color,sort);
        return find(owner,id,false);
    }
    public void update(UUID owner, UUID id, String name, String type, String icon, String color, int sort) {
        jdbc.update("UPDATE fintrack.categories SET name=?,category_type=?,icon_key=?,color_hex=?,sort_order=?,updated_at=now() WHERE user_id=? AND id=?",
                name,type,icon,color,sort,owner,id);
    }
    public boolean referenced(UUID owner, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fintrack.transactions WHERE user_id=? AND category_id=?) OR EXISTS(SELECT 1 FROM fintrack.budgets WHERE user_id=? AND category_id=?)",
                Boolean.class,owner,id,owner,id));
    }
    public void status(UUID owner, UUID id, boolean active) {
        jdbc.update("UPDATE fintrack.categories SET archived_at=" + (active ? "NULL" : "coalesce(archived_at,now())")
                + ",updated_at=now() WHERE user_id=? AND id=?",owner,id);
    }
}
