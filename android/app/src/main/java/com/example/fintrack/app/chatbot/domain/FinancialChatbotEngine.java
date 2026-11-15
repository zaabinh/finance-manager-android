package com.example.fintrack.app.chatbot.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.voice.domain.VoiceTransactionParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bộ xử lý logic thông minh của Chatbot tài chính (Đồng Bot).
 * Hỗ trợ:
 * 1. Nhận diện giao dịch tự nhiên (NL2Transaction) và tạo Action lưu nhanh.
 * 2. Truy vấn trực tiếp trạng thái tài chính từ FinanceBook (số dư ví, ngân sách, chi tiêu).
 * 3. Tư vấn kiến thức quản lý tài chính cá nhân (50/30/20, quỹ khẩn cấp, mẹo tiết kiệm, trả nợ).
 * 4. Gợi ý câu hỏi nhanh (Quick prompt suggestions).
 */
public final class FinancialChatbotEngine {

    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("yyyy-MM", Locale.ROOT);

    public static ChatMessage process(String input, FinanceBook book) {
        if (input == null || input.trim().isEmpty()) {
            return ChatMessage.botMessage(
                    "Tôi chưa nghe rõ bạn nói gì. Bạn có thể hỏi tôi về số dư, ngân sách, hoặc nhập giao dịch nhanh nhé!",
                    null,
                    defaultPrompts()
            );
        }

        String raw = input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Kiểm tra Ý định nhập giao dịch thu / chi bằng ngôn ngữ tự nhiên
        if (isTransactionIntent(lower)) {
            VoiceTransactionParser.ParsedResult parsed = VoiceTransactionParser.parse(raw);
            if (parsed.amount > 0) {
                String typeName = (parsed.type == TransactionType.INCOME) ? "Thu nhập" : "Chi tiêu";
                String formattedMoney = formatMoney(parsed.amount);

                String reply = "Tôi đã ghi nhận giao dịch của bạn:\n\n"
                        + "• <b>Loại:</b> " + typeName + "\n"
                        + "• <b>Số tiền:</b> " + formattedMoney + "\n"
                        + "• <b>Khoản mục:</b> " + parsed.note + "\n\n"
                        + "Bạn có muốn lưu ngay giao dịch này vào sổ tài chính không?";

                ChatMessage.ChatAction action = ChatMessage.ChatAction.createTransaction(
                        parsed.amount,
                        parsed.type,
                        parsed.note,
                        ""
                );

                List<String> suggestions = Arrays.asList(
                        "📊 Ngân sách tháng này?",
                        "💰 Kiểm tra số dư ví",
                        "💡 Mẹo tiết kiệm hiệu quả?"
                );

                return ChatMessage.botMessage(reply, action, suggestions);
            }
        }

        // 2. Kiểm tra Ý định hỏi số dư / ví tiền
        if (lower.contains("số dư") || lower.contains("tiền còn") || lower.contains("tổng tài sản")
                || lower.contains("trong ví") || lower.contains("ví tiền") || lower.contains("còn bao nhiêu tiền")) {
            return handleBalanceQuery(book);
        }

        // 3. Kiểm tra Ý định hỏi ngân sách / hạn mức
        if (lower.contains("ngân sách") || lower.contains("hạn mức") || lower.contains("vượt ngân sách")
                || lower.contains("tiêu được bao nhiêu") || lower.contains("còn lại bao nhiêu để tiêu")) {
            return handleBudgetQuery(book);
        }

        // 4. Kiểm tra Ý định hỏi báo cáo / thống kê chi tiêu
        if (lower.contains("báo cáo") || lower.contains("thống kê") || lower.contains("phân tích")
                || lower.contains("chi nhiều nhất") || lower.contains("chi bao nhiêu")) {
            return handleReportQuery(book);
        }

        // 5. Kiểm tra Ý định tư vấn kiến thức tài chính
        // 5.1. Quy tắc 50/30/20
        if (lower.contains("50/30/20") || lower.contains("50 30 20") || lower.contains("quy tắc chi tiêu")
                || lower.contains("phân bổ thu nhập") || lower.contains("chia tiền")) {
            return handle50_30_20Rule();
        }

        // 5.2. Quỹ khẩn cấp
        if (lower.contains("khẩn cấp") || lower.contains("dự phòng") || lower.contains("quỹ phòng ngừa")) {
            return handleEmergencyFund();
        }

        // 5.3. Mẹo tiết kiệm và cắt giảm chi tiêu
        if (lower.contains("tiết kiệm") || lower.contains("cắt giảm") || lower.contains("để dành")
                || lower.contains("tiết kiệm tiền") || lower.contains("bớt tiêu")) {
            return handleSavingTips();
        }

        // 5.4. Quản lý nợ
        if (lower.contains("trả nợ") || lower.contains("món nợ") || lower.contains("vay tiền")) {
            return handleDebtAdvice();
        }

        // 6. Xử lý xã giao, chào hỏi, cảm ơn
        if (lower.startsWith("chào") || lower.equals("hi") || lower.equals("hello") || lower.equals("alo")
                || lower.contains("bạn là ai") || lower.contains("làm được gì")) {
            return handleGreeting();
        }

        if (lower.contains("cảm ơn") || lower.contains("thanks") || lower.contains("thank you")) {
            return ChatMessage.botMessage(
                    "Rất vui được hỗ trợ bạn! Hãy cứ nhắn tin cho tôi bất cứ khi nào bạn cần quản lý chi tiêu hay tư vấn tài chính nhé. 🌿",
                    null,
                    defaultPrompts()
            );
        }

        if (lower.contains("tạm biệt") || lower.contains("bye")) {
            return ChatMessage.botMessage(
                    "Tạm biệt bạn! Chúc bạn một ngày tràn đầy niềm vui và quản lý chi tiêu thông thái!",
                    null,
                    defaultPrompts()
            );
        }

        // 7. Phản hồi mặc định khi chưa rõ yêu cầu
        return handleFallback();
    }

    private static boolean isTransactionIntent(String lower) {
        // Có chứa từ khóa chỉ hành động giao dịch
        boolean hasAction = lower.contains("ăn") || lower.contains("mua") || lower.contains("chi")
                || lower.contains("uống") || lower.contains("tiêu") || lower.contains("trả")
                || lower.contains("hết") || lower.contains("đổ xăng") || lower.contains("nhận")
                || lower.contains("lương") || lower.contains("thưởng") || lower.contains("thu");

        // Có chứa đơn vị tiền tệ hoặc số
        boolean hasMoney = lower.contains("k") || lower.contains("nghìn") || lower.contains("ngàn")
                || lower.contains("triệu") || lower.contains("tr") || lower.contains("đ")
                || lower.contains("đồng") || lower.matches(".*\\d+.*");

        return hasAction && hasMoney;
    }

    private static ChatMessage handleBalanceQuery(FinanceBook book) {
        if (book == null) {
            return ChatMessage.botMessage(
                    "Dữ liệu tài chính đang được đồng bộ. Bạn vui lòng quay lại sau giây lát hoặc xem trực tiếp trên màn hình Tổng quan nhé!",
                    null,
                    defaultPrompts()
            );
        }

        long totalAssets = book.assets(false);
        long savings = book.assets(true);
        long available = totalAssets - savings;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>Tình hình tài chính hiện tại của bạn:</b><br/><br/>");
        sb.append("• <b>Tổng tài sản:</b> ").append(formatMoney(totalAssets)).append("<br/>");
        sb.append("• <b>Tiền khả dụng:</b> ").append(formatMoney(available)).append("<br/>");
        sb.append("• <b>Tiết kiệm:</b> ").append(formatMoney(savings)).append("<br/><br/>");

        List<Wallet> wallets = book.wallets();
        if (!wallets.isEmpty()) {
            sb.append("<b>Chi tiết ví tiền:</b><br/>");
            for (Wallet w : wallets) {
                if (w.archivedAt == null) {
                    long bal = book.balance(w.id);
                    sb.append("• ").append(w.name).append(": <b>").append(formatMoney(bal)).append("</b><br/>");
                }
            }
        }

        List<String> prompts = Arrays.asList(
                "📊 Ngân sách tháng này?",
                "💡 Quy tắc 50/30/20 là gì?",
                "📈 Mẹo tiết kiệm hiệu quả?"
        );

        return ChatMessage.botMessage(sb.toString(), null, prompts);
    }

    private static ChatMessage handleBudgetQuery(FinanceBook book) {
        if (book == null) {
            return ChatMessage.botMessage(
                    "Chưa tải được dữ liệu ngân sách. Bạn có thể kiểm tra mục Ngân sách trong menu chính nhé!",
                    ChatMessage.ChatAction.viewBudget(),
                    defaultPrompts()
            );
        }

        String month = currentMonth();
        long limit = book.budgetLimit(month);
        long spent = book.budgetSpent(month);

        if (limit <= 0) {
            String msg = "Bạn chưa thiết lập ngân sách tổng cho tháng " + month + ".<br/><br/>"
                    + "💡 <i>Đặt ngân sách giúp bạn kiểm soát dòng tiền và tránh chi tiêu quá tay. Hãy bấm nút bên dưới để tạo ngay!</i>";
            return ChatMessage.botMessage(msg, ChatMessage.ChatAction.viewBudget(), defaultPrompts());
        }

        int percent = (int) (spent * 100 / limit);
        long remaining = limit - spent;

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>Ngân sách tháng ").append(month).append(":</b><br/><br/>");
        sb.append("• <b>Hạn mức:</b> ").append(formatMoney(limit)).append("<br/>");
        sb.append("• <b>Đã chi:</b> ").append(formatMoney(spent)).append(" (").append(percent).append("%)<br/>");

        if (remaining >= 0) {
            sb.append("• <b>Còn lại:</b> <font color='#287B47'><b>").append(formatMoney(remaining)).append("</b></font><br/><br/>");
            if (percent < 70) {
                sb.append("🌿 <i>Rất tốt! Bạn đang kiểm soát chi tiêu rất hợp lý và còn nhiều dư địa an toàn.</i>");
            } else if (percent < 90) {
                sb.append("⚠️ <i>Bạn đã dùng hơn ").append(percent).append("% ngân sách. Hãy chú ý các khoản chi phát sinh sắp tới nhé.</i>");
            } else {
                sb.append("⚡ <i>Ngân sách sắp cạn (còn ").append(formatMoney(remaining)).append("). Cân nhắc chỉ chi cho các nhu cầu thiết yếu thôi nhé!</i>");
            }
        } else {
            sb.append("• <b>Vượt mức:</b> <font color='#B73E36'><b>").append(formatMoney(-remaining)).append("</b></font><br/><br/>");
            sb.append("🚨 <i>Bạn đã chi vượt ngân sách đề ra. Hãy mở báo cáo để xem danh mục nào phát sinh nhiều nhất và điều chỉnh lại nhé!</i>");
        }

        return ChatMessage.botMessage(sb.toString(), ChatMessage.ChatAction.viewBudget(), Arrays.asList(
                "💰 Kiểm tra số dư ví",
                "📈 Mẹo tiết kiệm hiệu quả?",
                "❓ Quy tắc 50/30/20 là gì?"
        ));
    }

    private static ChatMessage handleReportQuery(FinanceBook book) {
        String month = currentMonth();
        long totalExpense = book != null ? book.total(TransactionType.EXPENSE, month) : 0;
        long totalIncome = book != null ? book.total(TransactionType.INCOME, month) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("📈 <b>Tổng quan thu chi tháng ").append(month).append(":</b><br/><br/>");
        sb.append("• <b>Tổng thu:</b> <font color='#287B47'>+").append(formatMoney(totalIncome)).append("</font><br/>");
        sb.append("• <b>Tổng chi:</b> <font color='#B73E36'>-").append(formatMoney(totalExpense)).append("</font><br/>");
        long net = totalIncome - totalExpense;
        sb.append("• <b>Chênh lệch:</b> <b>").append(formatMoney(net)).append("</b><br/><br/>");
        sb.append("Bấm nút bên dưới để xem biểu đồ phân tích chi tiết theo từng danh mục!");

        return ChatMessage.botMessage(sb.toString(), ChatMessage.ChatAction.viewAnalytics(), Arrays.asList(
                "📊 Ngân sách tháng này?",
                "💡 Mẹo tiết kiệm hiệu quả?"
        ));
    }

    private static ChatMessage handle50_30_20Rule() {
        String reply = "💡 <b>Quy tắc quản lý tài chính 50/30/20:</b><br/><br/>"
                + "Đây là phương pháp phân bổ thu nhập đơn giản nhưng cực kỳ hiệu quả:<br/><br/>"
                + "• <b>50% — Nhu cầu thiết yếu (Needs):</b> Tiền thuê nhà, tiền ăn, hóa đơn điện nước, đi lại, học phí. Đây là các khoản bắt buộc phải chi.<br/><br/>"
                + "• <b>30% — Mong muốn cá nhân (Wants):</b> Mua sắm, cà phê bạn bè, xem phim, du lịch, sở thích. Khoản này giúp bạn tận hưởng cuộc sống nhưng có thể cắt giảm khi cần.<br/><br/>"
                + "• <b>20% — Tiết kiệm & Tích lũy (Savings):</b> Quỹ khẩn cấp, trả nợ, đầu tư hoặc mục tiêu dài hạn.<br/><br/>"
                + "<i>Mẹo: Nếu đang ở giai đoạn cần tích lũy nhanh hoặc trả nợ, bạn có thể điều chỉnh linh hoạt thành 50/20/30 hoặc 60/20/20 nhé!</i>";

        List<String> prompts = Arrays.asList(
                "🛡️ Cách lập quỹ khẩn cấp?",
                "📈 Mẹo tiết kiệm hiệu quả?",
                "📊 Ngân sách tháng này?"
        );

        return ChatMessage.botMessage(reply, null, prompts);
    }

    private static ChatMessage handleEmergencyFund() {
        String reply = "🛡️ <b>Cách xây dựng Quỹ Khẩn Cấp (Emergency Fund):</b><br/><br/>"
                + "Quỹ khẩn cấp là tấm lá chắn bảo vệ bạn trước các rủi ro bất ngờ (ốm đau, sửa xe, gián đoạn công việc):<br/><br/>"
                + "1. <b>Quy mô lý tưởng:</b> Bằng <b>3 đến 6 tháng</b> chi phí sinh hoạt thiết yếu của bạn.<br/>"
                + "2. <b>Nơi cất giữ:</b> Gửi vào tài khoản tiết kiệm linh hoạt hoặc ví riêng có thể rút ngay khi cần, <b>không đem đi đầu tư rủi ro</b>.<br/>"
                + "3. <b>Cách bắt đầu:</b> Đặt mục tiêu nhỏ trước (ví dụ 5 triệu hoặc 10 triệu), trích 10–20% thu nhập mỗi tháng cho đến khi đạt mục tiêu.<br/><br/>"
                + "<i>Khi có quỹ khẩn cấp, bạn sẽ luôn an tâm trước mọi biến cố trong cuộc sống!</i>";

        List<String> prompts = Arrays.asList(
                "💡 Quy tắc 50/30/20 là gì?",
                "📈 Mẹo tiết kiệm hiệu quả?",
                "💰 Kiểm tra số dư ví"
        );

        return ChatMessage.botMessage(reply, null, prompts);
    }

    private static ChatMessage handleSavingTips() {
        String reply = "🌿 <b>4 Mẹo tiết kiệm tiền thông minh & bền vững:</b><br/><br/>"
                + "• <b>Quy tắc 24 giờ:</b> Trước khi mua một món đồ không thiết yếu, hãy chờ 24h. Đa số cảm giác 'muốn mua' sẽ biến mất sau khi cảm xúc lắng xuống.<br/><br/>"
                + "• <b>Để ý hiệu ứng Latte:</b> Những khoản chi nhỏ 30k – 50k mỗi ngày (cà phê, trà sữa, snack) tưởng ít nhưng có thể lên tới 1.000.000 – 1.500.000 đ mỗi tháng.<br/><br/>"
                + "• <b>Trả cho mình trước (Pay Yourself First):</b> Khi nhận lương, hãy trích ngay 15-20% vào ví tiết kiệm trước khi bắt đầu chi tiêu.<br/><br/>"
                + "• <b>Ghi chép đều đặn:</b> Chỉ cần ghi lại chi tiêu, bạn sẽ tự động cân nhắc kỹ hơn trước mỗi lần rút ví!";

        List<String> prompts = Arrays.asList(
                "💡 Quy tắc 50/30/20 là gì?",
                "🛡️ Cách lập quỹ khẩn cấp?",
                "📊 Ngân sách tháng này?"
        );

        return ChatMessage.botMessage(reply, null, prompts);
    }

    private static ChatMessage handleDebtAdvice() {
        String reply = "⚖️ <b>Chiến lược trả nợ thông minh:</b><br/><br/>"
                + "Có 2 phương pháp phổ biến nhất được các chuyên gia tài chính khuyên dùng:<br/><br/>"
                + "• <b>Phương pháp Tuyết lăn (Debt Snowball):</b> Trả hết các khoản nợ có số tiền nhỏ nhất trước, bất kể lãi suất. Điều này tạo chiến thắng tâm lý và động lực lớn.<br/><br/>"
                + "• <b>Phương pháp Tuyết lở (Debt Avalanche):</b> Ưu tiên trả khoản nợ có <b>lãi suất cao nhất</b> trước để giảm tối đa chi phí lãi vay.<br/><br/>"
                + "<i>Quan trọng nhất: Ngừng tạo thêm các khoản nợ tiêu dùng mới trong thời gian đang trả nợ!</i>";

        return ChatMessage.botMessage(reply, null, defaultPrompts());
    }

    private static ChatMessage handleGreeting() {
        String reply = "Chào bạn! Tôi là <b>Đồng Bot</b> — trợ lý tài chính thông minh của bạn. 🌿<br/><br/>"
                + "Tôi luôn sẵn sàng đồng hành cùng bạn để:<br/>"
                + "• Ghi chép thu chi nhanh (vd: <i>'vừa ăn trưa 45k'</i>)<br/>"
                + "• Kiểm tra ngân sách & số dư các ví<br/>"
                + "• Tư vấn các quy tắc tài chính (50/30/20, quỹ khẩn cấp...)<br/><br/>"
                + "Bạn muốn tìm hiểu thông tin nào?";

        return ChatMessage.botMessage(reply, null, defaultPrompts());
    }

    private static ChatMessage handleFallback() {
        String reply = "Tôi chưa hiểu rõ câu hỏi này. Bạn có thể thử một trong các gợi ý bên dưới hoặc gõ giao dịch như: <i>'Ăn phở 45k'</i>, <i>'Nhận thưởng 2 triệu'</i> nhé!";
        return ChatMessage.botMessage(reply, null, defaultPrompts());
    }

    public static List<String> defaultPrompts() {
        return Arrays.asList(
                "📊 Ngân sách tháng này?",
                "💰 Kiểm tra số dư ví",
                "🍜 Vừa ăn trưa 45k",
                "💡 Quy tắc 50/30/20 là gì?",
                "🛡️ Cách lập quỹ khẩn cấp?",
                "📈 Mẹo tiết kiệm hiệu quả?"
        );
    }

    private static String formatMoney(long amount) {
        return String.format(Locale.ROOT, "%,d đ", amount).replace(',', '.');
    }

    private static String currentMonth() {
        return MONTH_FORMAT.format(new Date());
    }
}
