package com.tj90.prioritytodo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Small line icons drawn on a 24-unit grid, so the UI never depends on font
 * glyphs such as "‹", "▾", "×" or emoji that render inconsistently across devices.
 */
final class Glyph extends Drawable {
    static final String CLOCK = "clock";
    static final String CHEVRON_LEFT = "chevron_left";
    static final String CHEVRON_RIGHT = "chevron_right";
    static final String EXPAND_MORE = "expand_more";
    static final String EXPAND_LESS = "expand_less";
    static final String PLUS = "plus";
    static final String CLOSE = "close";
    static final String CHECK = "check";
    static final String BOLT = "bolt";
    static final String CALENDAR = "calendar";
    static final String REPEAT = "repeat";
    static final String ALERT = "alert";
    static final String UNDO = "undo";
    static final String NOTES = "notes";
    static final String LIST = "list";

    private final String kind;
    private final int sizePx;
    private final float strokeUnits;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    Glyph(String kind, int sizePx, int color) {
        this(kind, sizePx, color, 2f);
    }

    Glyph(String kind, int sizePx, int color, float strokeUnits) {
        this.kind = kind;
        this.sizePx = sizePx;
        this.strokeUnits = strokeUnits;
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        // Draw at most at the intrinsic size, centered, so a glyph can be used
        // directly as the background of a larger touch target.
        float side = Math.min(Math.min(bounds.width(), bounds.height()), sizePx);
        if (side <= 0) {
            return;
        }
        float unit = side / 24f;
        canvas.save();
        canvas.translate(bounds.exactCenterX() - 12f * unit, bounds.exactCenterY() - 12f * unit);
        canvas.scale(unit, unit);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeUnits);
        switch (kind) {
            case CLOCK:
                canvas.drawCircle(12f, 12f, 8.5f, paint);
                canvas.drawLine(12f, 12f, 12f, 7.5f, paint);
                canvas.drawLine(12f, 12f, 15f, 14f, paint);
                break;
            case CHEVRON_LEFT:
                polyline(14.5f, 6f, 8.5f, 12f, 14.5f, 18f);
                canvas.drawPath(path, paint);
                break;
            case CHEVRON_RIGHT:
                polyline(9.5f, 6f, 15.5f, 12f, 9.5f, 18f);
                canvas.drawPath(path, paint);
                break;
            case EXPAND_MORE:
                polyline(7f, 9.5f, 12f, 14.5f, 17f, 9.5f);
                canvas.drawPath(path, paint);
                break;
            case EXPAND_LESS:
                polyline(7f, 14.5f, 12f, 9.5f, 17f, 14.5f);
                canvas.drawPath(path, paint);
                break;
            case PLUS:
                canvas.drawLine(12f, 5f, 12f, 19f, paint);
                canvas.drawLine(5f, 12f, 19f, 12f, paint);
                break;
            case CLOSE:
                canvas.drawLine(7f, 7f, 17f, 17f, paint);
                canvas.drawLine(17f, 7f, 7f, 17f, paint);
                break;
            case CHECK:
                polyline(5f, 12.5f, 10f, 17.5f, 19f, 7.5f);
                canvas.drawPath(path, paint);
                break;
            case BOLT:
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(13.5f, 2.5f);
                path.lineTo(5f, 13.5f);
                path.lineTo(11f, 13.5f);
                path.lineTo(10.5f, 21.5f);
                path.lineTo(19f, 10f);
                path.lineTo(13f, 10f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case CALENDAR:
                oval.set(4f, 5.5f, 20f, 20f);
                canvas.drawRoundRect(oval, 2.5f, 2.5f, paint);
                canvas.drawLine(4f, 10f, 20f, 10f, paint);
                canvas.drawLine(8.5f, 3.5f, 8.5f, 7f, paint);
                canvas.drawLine(15.5f, 3.5f, 15.5f, 7f, paint);
                break;
            case REPEAT:
                path.reset();
                path.moveTo(4.5f, 11f);
                path.lineTo(4.5f, 10f);
                path.quadTo(4.5f, 7f, 7.5f, 7f);
                path.lineTo(19.5f, 7f);
                canvas.drawPath(path, paint);
                polyline(16.5f, 4f, 19.5f, 7f, 16.5f, 10f);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(19.5f, 13f);
                path.lineTo(19.5f, 14f);
                path.quadTo(19.5f, 17f, 16.5f, 17f);
                path.lineTo(4.5f, 17f);
                canvas.drawPath(path, paint);
                polyline(7.5f, 14f, 4.5f, 17f, 7.5f, 20f);
                canvas.drawPath(path, paint);
                break;
            case ALERT:
                canvas.drawCircle(12f, 12f, 8.5f, paint);
                canvas.drawLine(12f, 7.5f, 12f, 12.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 16f, strokeUnits * 0.65f, paint);
                break;
            case UNDO:
                polyline(8.5f, 4.5f, 4f, 9f, 8.5f, 13.5f);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(4f, 9f);
                path.lineTo(14.5f, 9f);
                path.quadTo(20f, 9f, 20f, 14f);
                path.quadTo(20f, 19f, 14.5f, 19f);
                path.lineTo(10f, 19f);
                canvas.drawPath(path, paint);
                break;
            case NOTES:
                canvas.drawLine(5f, 7f, 19f, 7f, paint);
                canvas.drawLine(5f, 12f, 19f, 12f, paint);
                canvas.drawLine(5f, 17f, 13f, 17f, paint);
                break;
            case LIST:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(5.5f, 7f, 1.4f, paint);
                canvas.drawCircle(5.5f, 12f, 1.4f, paint);
                canvas.drawCircle(5.5f, 17f, 1.4f, paint);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(9.5f, 7f, 19f, 7f, paint);
                canvas.drawLine(9.5f, 12f, 19f, 12f, paint);
                canvas.drawLine(9.5f, 17f, 19f, 17f, paint);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void polyline(float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
