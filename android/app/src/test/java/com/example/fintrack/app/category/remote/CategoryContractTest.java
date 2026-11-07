package com.example.fintrack.app.category.remote;

import static org.junit.Assert.*;
import com.example.fintrack.R;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

public class CategoryContractTest {
    private final Gson gson=new Gson();
    @Test public void metadataAndLifecycleSurviveResponseMapping() {
        CategoryResponse r=gson.fromJson("{\"categoryId\":\"id\",\"name\":\"Cafe\",\"categoryType\":\"EXPENSE\",\"iconKey\":\"coffee\",\"colorHex\":\"#ABCDEF\",\"sortOrder\":3,\"archivedAt\":\"2026-11-07T00:00:00Z\"}",CategoryResponse.class);
        FinanceBook.Category c=r.domain();
        assertEquals("coffee",c.iconKey); assertEquals("#ABCDEF",c.colorHex); assertEquals(3,c.sortOrder);
        assertNotNull(c.archivedAt);
    }
    @Test public void requestUsesStableKeyAndNoOwnerDescriptionOrLifecycle() {
        JsonObject body=gson.toJsonTree(new CategoryRequest("Cafe",FinanceBook.Type.EXPENSE,"coffee",null,0)).getAsJsonObject();
        assertEquals("coffee",body.get("iconKey").getAsString());
        assertFalse(body.has("description")); assertFalse(body.has("active")); assertFalse(body.has("userId"));
        assertTrue(gson.toJsonTree(new CategoryStatusRequest(true)).getAsJsonObject().get("active").getAsBoolean());
    }
    @Test public void iconKeysRoundTripAndUnknownKeysUseFallback() {
        for(int resource:CategoryIcons.RESOURCES) assertEquals(resource,CategoryIcons.resource(CategoryIcons.key(resource)));
        assertEquals(R.drawable.ic_category,CategoryIcons.resource("future-server-icon"));
        assertEquals(R.drawable.ic_category,CategoryIcons.resource(null));
    }
}
