package com.tj90.prioritytodo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class ConfettiView extends View {
    private static final long LIFE_MS = 650L;

    private static final class Particle {
        float ox;
        float oy;
        float tx;
        float ty;
        float size;
        float rot;
        int color;
        boolean circle;
        long start;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random random = new Random();

    ConfettiView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    void burst(float cx, float cy, int[] colors) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 11; i++) {
            double angle = (i / 11.0) * Math.PI * 2 + (random.nextFloat() - 0.5f) * 0.5;
            double dist = dp(26) + random.nextFloat() * dp(24);
            Particle p = new Particle();
            p.ox = cx;
            p.oy = cy;
            p.tx = (float) (Math.cos(angle) * dist);
            p.ty = (float) (Math.sin(angle) * dist);
            p.size = dp(4) + random.nextFloat() * dp(4);
            p.rot = (random.nextFloat() - 0.5f) * 180f;
            p.color = colors[i % colors.length];
            p.circle = random.nextFloat() > 0.45f;
            p.start = now;
            particles.add(p);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean alive = false;
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            float t = (now - p.start) / (float) LIFE_MS;
            if (t >= 1f) {
                particles.remove(i);
                continue;
            }
            alive = true;
            float ease = 1f - (1f - t) * (1f - t);
            float x = p.ox + p.tx * ease;
            float y = p.oy + p.ty * ease;
            float scale = 1f - 0.7f * t;
            float half = (p.size * scale) / 2f;
            int alpha = (int) (255 * Math.max(0f, 1f - t));
            paint.setColor(p.color);
            paint.setAlpha(alpha);
            if (p.circle) {
                canvas.drawCircle(x, y, half, paint);
            } else {
                canvas.save();
                canvas.rotate(p.rot * ease, x, y);
                rect.set(x - half, y - half, x + half, y + half);
                canvas.drawRect(rect, paint);
                canvas.restore();
            }
        }
        if (alive) {
            postInvalidateOnAnimation();
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
