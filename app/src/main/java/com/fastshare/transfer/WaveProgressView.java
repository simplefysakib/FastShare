package com.fastshare.transfer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class WaveProgressView extends View {
    private float progress = 0.0f;
    private Paint wavePaint1;
    private Paint wavePaint2;
    private Path wavePath1;
    private Path wavePath2;
    private float waveShift = 0.0f;

    public WaveProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wavePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint1.setColor(Color.parseColor("#4D2196F3"));
        wavePaint1.setStyle(Paint.Style.FILL);

        wavePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint2.setColor(Color.parseColor("#993F51B5"));
        wavePaint2.setStyle(Paint.Style.FILL);

        wavePath1 = new Path();
        wavePath2 = new Path();

        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setDuration(1500L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            waveShift = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float waterHeight = height - height * (progress / 100.0f);
        float waveLength = width * 1.5f;

        wavePath1.reset();
        wavePath2.reset();

        wavePath1.moveTo(0.0f, height);
        wavePath2.moveTo(0.0f, height);
        
        wavePath1.lineTo(0.0f, waterHeight);
        wavePath2.lineTo(0.0f, waterHeight);

        for (float x = 0.0f; x <= width; x += 10.0f) {
            float y1 = (float) Math.sin(x / waveLength * 2.0f * Math.PI + waveShift * 2.0f * Math.PI);
            float y2 = (float) Math.cos(x / waveLength * 2.0f * Math.PI + waveShift * 2.0f * Math.PI + 1.5);
            
            wavePath1.lineTo(x, y1 * 25.0f + waterHeight);
            wavePath2.lineTo(x, y2 * 25.0f + waterHeight);
        }

        wavePath1.lineTo(width, height);
        wavePath2.lineTo(width, height);

        wavePath1.close();
        wavePath2.close();

        canvas.drawPath(wavePath1, wavePaint1);
        canvas.drawPath(wavePath2, wavePaint2);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }
}