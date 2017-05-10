package com.example.zoomview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

/**
 * Created by Administrator on 2017/5/8.
 */

public class ZoomImageView extends android.support.v7.widget.AppCompatImageView {
    private static final String TAG = ZoomImageView.class.getSimpleName();
    private static final float MAX_SCALE_RATIO = 4f;
    /**
     * 如果图片宽或高大于屏幕，此值将小于0
     */
    private float mInitialScaleRatio = 1f;
    private final float[] mMatrixValues = new float[9];
    private final Matrix mMatrix = new Matrix();
    private boolean mFirstInited = true;
    private ScaleGestureDetector mScaleGestureDetector;
    private boolean mIsDragging;
    private double mLastPointerCount;
    private float mLastX;
    private float mLastY;
    private int mTouchSlop;
    private boolean mRectWidthGreaterThanViewWidth;
    private boolean mRectHeightGreaterThanViewHeight;
    private GestureDetector mGestureDetector;
    private static final int SCALE_RATIO_MEDIUM = 2;
    private static final int SCALE_RATIO_MAX = 4;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setScaleType(ScaleType.MATRIX);
        setOnTouchListener(mOnTouchListener);
        mScaleGestureDetector = new ScaleGestureDetector(context, mOnScaleGestureListener);
        mGestureDetector = new GestureDetector(context, mSimpleOnGestureListener);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOnTouchListener(mOnTouchListener);
    }

    private OnTouchListener mOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mGestureDetector.onTouchEvent(event)) {
                return true;
            }
            mScaleGestureDetector.onTouchEvent(event);
            float x = 0;
            float y = 0;
            /* 计算坐标均值 */
            final int pointerCount = event.getPointerCount();
            for (int i = 0; i < pointerCount; i++) {
                x += event.getX(i);
                y += event.getY(i);
            }
            x = x / pointerCount;
            y = y / pointerCount;
            /* 触摸点变化则更新可拖动标志和上次坐标 */
            if (pointerCount != mLastPointerCount) {
                mIsDragging = false;
                mLastX = x;
                mLastY = y;
            }
            mLastPointerCount = pointerCount;
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    /* 计算移动值 */
                    float dx = x - mLastX;
                    float dy = y - mLastY;
                    if (!mIsDragging) {
                        mIsDragging = isDragging(dx, dy);
                    }
                    if (mIsDragging) {
                        final RectF transformedRectF = getRectFAppliedScaleMatrix();
                        if (getDrawable() != null) {
                            mRectWidthGreaterThanViewWidth = true;
                            mRectHeightGreaterThanViewHeight = true;
                            if (transformedRectF.width() < getWidth()) { /* 如果图片宽度小于view宽度，则水平不移动 */
                                dx = 0;
                                mRectWidthGreaterThanViewWidth = false;
                            }
                            if (transformedRectF.height() < getHeight()) { /* 如果图片宽度小于view宽度，则竖直不移动 */
                                dy = 0;
                                mRectHeightGreaterThanViewHeight = false;
                            }
                            mMatrix.postTranslate(dx, dy);
                            reviseRectBounds();
                            setImageMatrix(mMatrix);
                        }
                    }
                    mLastX = x;
                    mLastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mLastPointerCount = 0;
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    private boolean isDragging(float dx, float dy) {
        return Math.sqrt(dx * dx + dy * dy) >= mTouchSlop;
    }

    private void reviseRectBounds() {
        final RectF transformedRectF = getRectFAppliedScaleMatrix();
        float deltaX = 0;
        float deltaY = 0;
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        if (transformedRectF.left > 0 && mRectWidthGreaterThanViewWidth) {
            deltaX = -transformedRectF.left;
        }
        if (transformedRectF.right < viewWidth && mRectWidthGreaterThanViewWidth) {
            deltaX = viewWidth - transformedRectF.right;
        }
        if (transformedRectF.top > 0 && mRectHeightGreaterThanViewHeight) {
            deltaY = -transformedRectF.top;
        }
        if (transformedRectF.bottom < viewHeight && mRectHeightGreaterThanViewHeight) {
            deltaY = viewHeight - transformedRectF.bottom;
        }
        mMatrix.postTranslate(deltaX, deltaY);
    }

    private OnScaleGestureListener mOnScaleGestureListener = new OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (getDrawable() == null) {
                return true; /* 没有图片就不进行处理 */
            }
            final float matrixScaleRatio = getMatrixScaleRatio();
            float scaleFactor = detector.getScaleFactor();
            /* 判断当前矩阵缩放比例是否位于指定范围内，
            小于最大缩放比例并且缩放因子大于1；或者大于最小缩放比例且缩放因子小于1 */
            if (matrixScaleRatio < MAX_SCALE_RATIO && scaleFactor > 1.f ||
                    matrixScaleRatio > mInitialScaleRatio && scaleFactor < 1.f) {
                if (matrixScaleRatio * scaleFactor < mInitialScaleRatio) {
                    scaleFactor = mInitialScaleRatio / matrixScaleRatio;
                }
                if (matrixScaleRatio * scaleFactor > MAX_SCALE_RATIO) {
                    scaleFactor = MAX_SCALE_RATIO / matrixScaleRatio;
                }
                mMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                checkBorderAndCenterWhenScale();
                setImageMatrix(mMatrix);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    };

    private boolean mIsInProcessOfAutoScaling;
    GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mIsInProcessOfAutoScaling) {
                return true;
            }
            final float x = e.getX();
            final float y = e.getY();
            if (getMatrixScaleRatio() < SCALE_RATIO_MEDIUM) {
                ZoomImageView.this.postDelayed(new AutoScaleRunnable(SCALE_RATIO_MEDIUM, x, y), 16);
                mIsInProcessOfAutoScaling = true;
            } else if (getMatrixScaleRatio() < SCALE_RATIO_MAX) {
                ZoomImageView.this.postDelayed(new AutoScaleRunnable(SCALE_RATIO_MAX, x, y), 16);
                mIsInProcessOfAutoScaling = true;
            } else {
                ZoomImageView.this.postDelayed(new AutoScaleRunnable(mInitialScaleRatio, x, y), 16);
                mIsInProcessOfAutoScaling = true;
            }
            return true;
        }
    };

    private class AutoScaleRunnable implements Runnable {
        private static final float TEMP_BIGGER_RATIO = 1.07f;
        private static final float TEMP_SMALLER_RATIO = 0.93f;
        private float mTargetScaleRatio;
        private float mTempScaleRatio;
        private float mScaleCenterX;
        private float mScaleCenterY;

        public AutoScaleRunnable(float targetScaleRatio, float scaleCenterX, float scaleCenterY) {
            mTargetScaleRatio = targetScaleRatio;
            mScaleCenterX = scaleCenterX;
            mScaleCenterY = scaleCenterY;
            if (getMatrixScaleRatio() < mTargetScaleRatio) {
                mTempScaleRatio = TEMP_BIGGER_RATIO;
            } else {
                mTempScaleRatio = TEMP_SMALLER_RATIO;
            }
        }

        @Override
        public void run() {
            mMatrix.postScale(mTempScaleRatio, mTempScaleRatio, mScaleCenterX, mScaleCenterY);
            checkBorderAndCenterWhenScale();
            setImageMatrix(mMatrix);

            final float currentScaleRatio = getMatrixScaleRatio();
            if (mTempScaleRatio > 1f && currentScaleRatio < mTargetScaleRatio
                    || mTempScaleRatio < 1f && mTargetScaleRatio < currentScaleRatio) {
                ZoomImageView.this.postDelayed(this, 16);
            } else {
                final float ratioBetweenTargetAndCurrent = mTargetScaleRatio / currentScaleRatio;
                mMatrix.postScale(ratioBetweenTargetAndCurrent, ratioBetweenTargetAndCurrent, mScaleCenterX, mScaleCenterY);
                checkBorderAndCenterWhenScale();
                setImageMatrix(mMatrix);
                mIsInProcessOfAutoScaling = false;
            }

        }
    }

    ;

    private float getMatrixScaleRatio() {
        mMatrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X];
    }

    private void checkBorderAndCenterWhenScale() {
        final RectF transformedRect = getRectFAppliedScaleMatrix();
        float deltaX = 0;
        float deltaY = 0;
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        if (transformedRect.width() >= viewWidth) { /* 如果缩放后图片宽度大于View宽度 */
            if (transformedRect.left > 0) { /* 计算左侧空隙 */
                deltaX = -transformedRect.left;
            }
            if (transformedRect.right < viewWidth) { /* 计算右侧空隙 */
                deltaX = viewWidth - transformedRect.right;
            }
        } else { /* deltaX为view的中心点和rectF的中心点距离 */
            deltaX = viewWidth * .5f - (transformedRect.right - transformedRect.width() * .5f);
        }
        if (transformedRect.height() >= viewHeight) { /* 如果缩放后图片高度大于View高度 */
            if (transformedRect.top > 0) { /* 计算上侧空隙 */
                deltaY = -transformedRect.top;
            }
            if (transformedRect.bottom < viewHeight) { /* 计算下侧空隙 */
                deltaY = viewHeight - transformedRect.bottom;
            }
        } else {
            deltaY = viewHeight * .5f - (transformedRect.bottom - transformedRect.height() * .5f);
        }
        mMatrix.postTranslate(deltaX, deltaY);
    }

    private RectF getRectFAppliedScaleMatrix() {
        final RectF rectF = new RectF();
        final Drawable drawable = getDrawable();
        if (drawable != null) {
            rectF.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            mMatrix.mapRect(rectF); /* 对RectF进行矩阵变换 */
        }
        return rectF;
    }

    ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            if (!mFirstInited) {
                return;
            }
            final Drawable drawable = getDrawable();
            if (drawable == null) {
                return;
            }
            final int viewWidth = getWidth();
            final int viewHeight = getHeight();
            final int drawableWidth = drawable.getIntrinsicWidth();
            final int drawableHeight = drawable.getIntrinsicHeight();
            /* 计算缩放比 */
            float scale = 1f;
            if (drawableWidth > viewWidth && drawableHeight <= viewHeight) { /* 图片宽大于控件宽 */
                scale = drawableWidth * 1.0f / viewWidth;
            } else if (drawableHeight > viewHeight && drawableWidth <= viewWidth) { /* 图片高大于控件高 */
                scale = drawableHeight * 1.0f / viewHeight;
            } else if (drawableWidth > viewWidth && drawableHeight > viewHeight) { /* 图片宽高大于控件宽高 */
                scale = Math.min(drawableWidth * 1.0f / viewWidth, drawableHeight * 1.0f / viewHeight);
            }
            mInitialScaleRatio = scale;
            mMatrix.postTranslate((viewWidth - drawableWidth) / 2, (viewHeight - drawableHeight) / 2);
            mMatrix.postScale(scale, scale, getWidth() / 2, getHeight() / 2);
            setImageMatrix(mMatrix);
            mFirstInited = false;
        }
    };


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }
}
