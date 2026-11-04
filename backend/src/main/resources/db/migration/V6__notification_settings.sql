-- 1. Tạo bảng notification_settings
CREATE TABLE fintrack.notification_settings (
    user_id UUID PRIMARY KEY REFERENCES fintrack.users(id) ON DELETE CASCADE,
    daily_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_reminder_time TIME(0) DEFAULT '20:00:00',
    budget_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    budget_alert_threshold_percent SMALLINT NOT NULL DEFAULT 80,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Ràng buộc 1: Ngưỡng cảnh báo từ 1% đến 100%
    CONSTRAINT chk_budget_threshold_range 
        CHECK (budget_alert_threshold_percent BETWEEN 1 AND 100),

    -- Ràng buộc 2: Bật nhắc nhở hàng ngày thì bắt buộc phải có giờ nhắc
    CONSTRAINT chk_reminder_time_required 
        CHECK (daily_reminder_enabled = FALSE OR daily_reminder_time IS NOT NULL)
);

-- 2. Tạo Partial Index cho Scheduler quét thông báo hàng ngày
CREATE INDEX idx_notification_settings_reminder_scan 
ON fintrack.notification_settings (daily_reminder_time) 
WHERE daily_reminder_enabled = TRUE;

-- 3. Gắn Trigger tự động cập nhật cột updated_at khi có UPDATE
CREATE TRIGGER trg_notification_settings_updated_at
BEFORE UPDATE ON fintrack.notification_settings
FOR EACH ROW
EXECUTE FUNCTION fintrack.set_updated_at();