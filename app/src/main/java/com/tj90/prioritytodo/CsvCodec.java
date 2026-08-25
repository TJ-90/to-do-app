package com.tj90.prioritytodo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CsvCodec {
    private static final String[] HEADERS = {
            "id", "title", "notes", "completed", "impact", "effort", "urgent", "quickTask",
            "snoozed", "recurringMit", "category", "dependency", "reminderAt", "repeatUnit",
            "repeatEvery", "createdAt"
    };

    private CsvCodec() { }

    static String exportTasks(List<TodoTask> tasks) {
        StringBuilder out = new StringBuilder();
        appendRow(out, HEADERS);
        for (TodoTask task : tasks) {
            appendRow(out, new String[]{
                    task.id,
                    task.title,
                    task.notes,
                    Boolean.toString(task.completed),
                    task.impact,
                    task.effort,
                    Boolean.toString(task.urgent),
                    Boolean.toString(task.quickTask),
                    Boolean.toString(task.snoozed),
                    Boolean.toString(task.recurringMit),
                    task.category == null ? "" : task.category,
                    task.dependency,
                    Long.toString(task.reminderAt),
                    task.reminderRepeatUnit,
                    Integer.toString(task.reminderRepeatEvery),
                    Long.toString(task.createdAt)
            });
        }
        return out.toString();
    }

    static List<TodoTask> importTasks(String csv) {
        List<List<String>> rows = parseRows(csv);
        List<TodoTask> tasks = new ArrayList<>();
        if (rows.isEmpty()) {
            return tasks;
        }
        Map<String, Integer> columns = headerMap(rows.get(0));
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            String title = value(row, columns, "title").trim();
            if (title.isEmpty()) {
                continue;
            }
            TodoTask task = new TodoTask();
            String id = value(row, columns, "id").trim();
            if (!id.isEmpty()) {
                task.id = id;
            }
            task.title = title;
            task.notes = value(row, columns, "notes");
            task.completed = bool(value(row, columns, "completed"));
            task.impact = level(value(row, columns, "impact"), TodoTask.HIGH);
            task.effort = level(value(row, columns, "effort"), TodoTask.MEDIUM);
            task.urgent = bool(value(row, columns, "urgent"));
            task.quickTask = bool(value(row, columns, "quickTask"));
            task.snoozed = bool(value(row, columns, "snoozed"));
            task.recurringMit = bool(value(row, columns, "recurringMit"));
            String category = value(row, columns, "category").trim();
            task.category = category.isEmpty() ? null : category;
            String dependency = value(row, columns, "dependency").trim();
            task.dependency = dependency.isEmpty() ? "None" : dependency;
            task.reminderAt = longValue(value(row, columns, "reminderAt"), 0);
            task.reminderRepeatUnit = repeatUnit(value(row, columns, "repeatUnit"));
            task.reminderRepeatEvery = Math.max(1, intValue(value(row, columns, "repeatEvery"), 1));
            task.createdAt = longValue(value(row, columns, "createdAt"), System.currentTimeMillis());
            tasks.add(task);
        }
        return tasks;
    }

    private static void appendRow(StringBuilder out, String[] values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append(escape(values[index]));
        }
        out.append('\n');
    }

    private static String escape(String value) {
        String safe = value == null ? "" : value;
        boolean quoted = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        if (!quoted) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static List<List<String>> parseRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char ch = csv.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    boolean escaped = index + 1 < csv.length() && csv.charAt(index + 1) == '"';
                    if (escaped) {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Integer> headerMap(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            columns.put(headers.get(index).trim().toLowerCase(Locale.US), index);
        }
        return columns;
    }

    private static String value(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name.toLowerCase(Locale.US));
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private static boolean bool(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }

    private static String level(String value, String fallback) {
        String normalized = value.trim().toUpperCase(Locale.US);
        if (TodoTask.HIGH.equals(normalized) || "HIGH".equals(normalized)) {
            return TodoTask.HIGH;
        }
        if (TodoTask.MEDIUM.equals(normalized) || "MEDIUM".equals(normalized)) {
            return TodoTask.MEDIUM;
        }
        if (TodoTask.LOW.equals(normalized) || "LOW".equals(normalized)) {
            return TodoTask.LOW;
        }
        return fallback;
    }

    private static String repeatUnit(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        return TodoTask.normalizeRepeatUnit(normalized);
    }

    private static int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
