ALTER TABLE fintrack.user_preferences ADD COLUMN categories_initialized BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve every existing user's categories, including archived or renamed defaults.
INSERT INTO fintrack.user_preferences(user_id)
SELECT id FROM fintrack.users ON CONFLICT DO NOTHING;
UPDATE fintrack.user_preferences p SET categories_initialized = TRUE
WHERE EXISTS (SELECT 1 FROM fintrack.categories c WHERE c.user_id = p.user_id);

INSERT INTO fintrack.categories(user_id,name,category_type,icon_key,sort_order)
SELECT p.user_id,s.name,s.category_type,s.icon_key,s.sort_order
FROM fintrack.user_preferences p
CROSS JOIN (VALUES
    ('Ăn uống','EXPENSE','coffee',0),('Đi lại','EXPENSE','car',1),
    ('Mua sắm','EXPENSE','shopping_bag',2),('Hóa đơn','EXPENSE','receipt',3),('Khác','EXPENSE','more_horiz',4),
    ('Lương','INCOME','briefcase',5),('Thưởng','INCOME','gift',6),('Thu nhập khác','INCOME','more_horiz',7)
) AS s(name,category_type,icon_key,sort_order)
WHERE NOT p.categories_initialized;
UPDATE fintrack.user_preferences SET categories_initialized = TRUE WHERE NOT categories_initialized;
