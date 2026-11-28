package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class VoiceCategoryPredictorTest {

    private List<Category> categories;

    @Before
    public void setUp() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        categories = book.categories();
    }

    @Test
    public void predictCategory_foodPhrases_returnsFoodCategory() {
        Category result1 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Ăn trưa", "Ăn trưa 45 nghìn");
        assertNotNull(result1);
        assertEquals("food", result1.id);

        Category result2 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Cà phê", "Chi 50k cà phê highlands");
        assertNotNull(result2);
        assertEquals("food", result2.id);

        Category result3 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Trà sữa", "Mua trà sữa 35k");
        assertNotNull(result3);
        assertEquals("food", result3.id);
    }

    @Test
    public void predictCategory_transportPhrases_returnsTransportCategory() {
        Category result1 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Đổ xăng", "Đổ xăng 70 nghìn");
        assertNotNull(result1);
        assertEquals("transport", result1.id);

        Category result2 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Grab", "Đi grab 45k");
        assertNotNull(result2);
        assertEquals("transport", result2.id);
    }

    @Test
    public void predictCategory_shoppingPhrases_returnsShoppingCategory() {
        Category result1 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Mua sách", "Mua sách 120.000");
        assertNotNull(result1);
        assertEquals("shopping", result1.id);

        Category result2 = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Quần áo", "Mua quần áo 2 triệu rưỡi");
        assertNotNull(result2);
        assertEquals("shopping", result2.id);
    }

    @Test
    public void predictCategory_billsPhrases_returnsBillsCategory() {
        Category result = VoiceCategoryPredictor.predictCategory(categories, TransactionType.EXPENSE, "Tiền điện", "Tiền điện tháng 11 500 nghìn");
        assertNotNull(result);
        assertEquals("bills", result.id);
    }

    @Test
    public void predictCategory_incomePhrases_returnsSalaryOrBonus() {
        Category salary = VoiceCategoryPredictor.predictCategory(categories, TransactionType.INCOME, "Lương tháng 11", "Nhận lương tháng 11 15 triệu");
        assertNotNull(salary);
        assertEquals("salary", salary.id);

        Category bonus = VoiceCategoryPredictor.predictCategory(categories, TransactionType.INCOME, "Thưởng dự án", "Thưởng dự án 2.5 triệu");
        assertNotNull(bonus);
        assertEquals("bonus", bonus.id);
    }

    @Test
    public void predictCategory_transfer_returnsNull() {
        Category result = VoiceCategoryPredictor.predictCategory(categories, TransactionType.TRANSFER, "Cho Nam", "Chuyển khoản 200k cho Nam");
        assertNull(result);
    }
}
