package com.fintrack.api.service.notification;

import com.fintrack.api.dto.response.budget.BudgetResponse;
import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Only server-owned paths and plain variables reach the existing HTML-escaping renderer. */
public final class FinanceEmailContent {
    private FinanceEmailContent() {}
    public static String origin(String value) {
        URI uri=URI.create(value);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))
            throw new IllegalArgumentException("Finance email requires an HTTPS origin without credentials, path, query or fragment");
        return value.replaceAll("/$","");
    }
    public static Map<String,String> daily(String username,LocalDate date,LocalTime time,String zone,String origin) {
        var vars=common(username,"Đồng · Nhắc ghi chép ngày "+date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),"Dành một phút cập nhật thu chi hôm nay trong Đồng.",origin,"/app/transactions/new");
        vars.put("localDateLabel",date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        vars.put("reminderTimeLabel",time.format(DateTimeFormatter.ofPattern("HH:mm"))); vars.put("timeZoneLabel",zone); return vars;
    }
    public static Map<String,String> budget(String username,String category,BudgetResponse b,boolean exceeded,String origin) {
        String percent=b.usagePercent().stripTrailingZeros().toPlainString();
        String title=exceeded?"Đồng · Ngân sách "+category+" đã chạm hạn mức":"Đồng · Ngân sách "+category+" đã dùng "+percent+"%";
        var vars=common(username,title,"Tháng "+b.month()+": đã chi "+money(b.spentMinor())+" trên hạn mức "+money(b.limitMinor())+".",origin,"/app/budgets/"+b.budgetId());
        vars.put("categoryName",category); vars.put("monthLabel",YearMonth.parse(b.month()).format(DateTimeFormatter.ofPattern("MM/uuuu")));
        vars.put("spentAmount",money(b.spentMinor())); vars.put("limitAmount",money(b.limitMinor())); vars.put("usagePercent",percent);
        if(exceeded) vars.put("overAmount",money(Math.max(b.spentMinor()-b.limitMinor(),0)));
        else { vars.put("remainingAmount",money(b.remainingMinor())); vars.put("thresholdPercent",Integer.toString(b.warningThresholdPercent())); }
        return vars;
    }
    private static Map<String,String> common(String username,String title,String preheader,String origin,String path) {
        var vars=new HashMap<String,String>(); vars.put("username",username==null?"":username);
        vars.put("title",subject(title)); vars.put("preheader",preheader); vars.put("actionUrl",origin+path); vars.put("settingsUrl",origin+"/app/settings/notifications"); return vars;
    }
    public static String subject(String value) {
        String subject=value.replace('\r',' ').replace('\n',' ').strip();
        if(subject.isBlank()||subject.length()>300) throw new IllegalArgumentException("Invalid finance email subject"); return subject;
    }
    public static void validate(String template,Map<String,String> vars,String configuredOrigin) {
        Set<String> expected=new HashSet<>(Set.of("title","preheader","username","actionUrl","settingsUrl"));
        switch(template) {
            case "daily-reminder" -> expected.addAll(Set.of("localDateLabel","reminderTimeLabel","timeZoneLabel"));
            case "budget-warning" -> expected.addAll(Set.of("categoryName","monthLabel","spentAmount","limitAmount","remainingAmount","usagePercent","thresholdPercent"));
            case "budget-exceeded" -> expected.addAll(Set.of("categoryName","monthLabel","spentAmount","limitAmount","overAmount","usagePercent"));
            default -> throw new IllegalArgumentException("Unsupported finance template");
        }
        if(!expected.equals(vars.keySet())||vars.values().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Invalid finance template variables");
        if(!subject(vars.get("title")).equals(vars.get("title"))) throw new IllegalArgumentException("Invalid finance subject");
        if(!vars.get("settingsUrl").equals(configuredOrigin+"/app/settings/notifications")) throw new IllegalArgumentException("Invalid settings link");
        String action=vars.get("actionUrl");
        boolean valid=template.equals("daily-reminder")?action.equals(configuredOrigin+"/app/transactions/new"):
            action.matches(java.util.regex.Pattern.quote(configuredOrigin+"/app/budgets/")+"[0-9a-fA-F-]{36}");
        if(!valid) throw new IllegalArgumentException("Invalid finance action link");
    }
    public static String money(long value) { if(value<0) throw new IllegalArgumentException("Negative amount"); return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value)+" đ"; }
}
