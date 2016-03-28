package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import cyanogenmod.providers.CMSettings;

public class QSPanelTopView extends FrameLayout {

    public static final int TOAST_DURATION = 2000;

    protected View mEditTileInstructionView;
    protected View mDropTarget;
    protected View mBrightnessView;
    protected TextView mToastView;

    private boolean mEditing = false;
    private boolean mDisplayingInstructions = false;
    private boolean mDisplayingTrash = false;
    private boolean mDisplayingToast = false;
    public boolean mHasBrightnessSliderToDisplay = true;

    private AnimatorSet mAnimator;

    private SettingsObserver mSettingsObserver;
    private boolean mListening;

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mEditing;
        setClipToPadding(false);
    }

    public View getDropTarget() {
        return mDropTarget;
    }

    public View getBrightnessView() {
        return mBrightnessView;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTarget = findViewById(R.id.delete_container);
        mEditTileInstructionView = findViewById(R.id.edit_container);
        mBrightnessView = findViewById(R.id.brightness_container);
        mToastView = (TextView) findViewById(R.id.qs_toast);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        mBrightnessView.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        int dh = mBrightnessView.getMeasuredHeight();

        mDropTarget.measure(exactly(width), atMost(dh));
        mEditTileInstructionView.measure(exactly(width), atMost(dh));
        mToastView.measure(exactly(width), atMost(dh));

        setMeasuredDimension(width, dh);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean animateToState = !isLaidOut();
        super.onLayout(changed, left, top, right, bottom);
        if (animateToState) {
            Log.e(TAG, "first layout animating to state!");
            animateToState();
        }
    }

        mDropTarget.setVisibility(View.GONE);
        mEditTileInstructionView.setVisibility(View.GONE);
    }

    public void setEditing(boolean editing) {
        mEditing = editing;
        if (editing) {
            mDisplayingInstructions = true;
            mDisplayingTrash = false;
        } else {
            mDisplayingInstructions = false;
            mDisplayingTrash = false;
        }
        animateToState();
    }

    public void onStopDrag() {
        mDisplayingTrash = false;
        animateToState();
    }

    public void onStartDrag() {
        mDisplayingTrash = true;
        animateToState();
    }

    public void setDropIcon(int resourceId, int colorResourceId) {
        mDropTargetIcon.setImageResource(resourceId);
        final Drawable drawable = mDropTargetIcon.getDrawable();

        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_ATOP);
        DrawableCompat.setTint(drawable, mContext.getColor(colorResourceId));

        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
    }

    public void toast(int textStrResId) {
        mDisplayingToast = true;
        mToastView.setText(textStrResId);
        animateToState();
    }

    private Runnable mAnimateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAnimator != null) {
                mAnimator.cancel();
            }
            mAnimator = new AnimatorSet();

            final boolean showToast = mDisplayingToast;
            final boolean showTrash = mDisplayingTrash && !mDisplayingToast;
            final boolean showBrightness = !mEditing && !mDisplayingToast;
            final boolean showInstructions = mEditing
                    && mDisplayingInstructions
                    && !mDisplayingTrash
                    && !mDisplayingToast;

            /*Log.d(TAG, "animating to state: "
                    + " showBrightness: " + showBrightness
                    + " showInstructions: " + showInstructions
                    + " showTrash: " + showTrash
                    + " showToast: " + showToast
            );*/

            final Animator brightnessAnimator = showBrightnessSlider(showBrightness);
            final Animator instructionAnimator = showInstructions(showInstructions);
            final Animator trashAnimator = showTrash(showTrash);
            final Animator toastAnimator = showToast(showToast);

            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // if the view is already visible, keep it visible on animation start
                    // to animate it out, otherwise set it as invisible (to not affect view height)
                    mEditTileInstructionView.setVisibility(
                            getVisibilityForAnimation(mEditTileInstructionView, showInstructions));
                    mDropTarget.setVisibility(
                            getVisibilityForAnimation(mDropTarget, showTrash));
                    mToastView.setVisibility(
                            getVisibilityForAnimation(mToastView, showToast));
                    if (mHasBrightnessSliderToDisplay) {
                        mBrightnessView.setVisibility(
                                getVisibilityForAnimation(mBrightnessView, showBrightness));
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mToastView.setVisibility(showToast ? View.VISIBLE : View.GONE);
                    mEditTileInstructionView.setVisibility(showInstructions
                            ? View.VISIBLE : View.GONE);
                    mDropTarget.setVisibility(showTrash ? View.VISIBLE : View.GONE);
                    if (mHasBrightnessSliderToDisplay) {
                        mBrightnessView.setVisibility(showBrightness ? View.VISIBLE : View.GONE);
                    }

                    mAnimator = null;

                    requestLayout();

                    if (showToast) {
                        mToastView.bringToFront();
                        mToastView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mDisplayingToast = false;
                                animateToState();
                            }
                        }, TOAST_DURATION);
                    }
                }
            });

            mAnimator.setDuration(500);
            mAnimator.setInterpolator(new FastOutSlowInInterpolator());
            mAnimator.setStartDelay(100);
            mAnimator.playTogether(instructionAnimator, trashAnimator,
                    brightnessAnimator, toastAnimator);
            mAnimator.start();
        }
    };

    private int getVisibilityForAnimation(View view, boolean show) {
        if (show || view.getVisibility() != View.GONE) {
            return View.VISIBLE;
        }
        return View.INVISIBLE;
    }

    private void animateToState() {
        post(mAnimateRunnable);
    }

    private Animator animateView(View v, boolean show) {
        return ObjectAnimator.ofFloat(v, "translationY", show ? 0 : -getMeasuredHeight());
    }

    private Animator showBrightnessSlider(boolean show) {
        return animateView(mBrightnessView, show);
=======
    private void animateToState() {
        showBrightnessSlider(!mEditing && !mDisplayingToast);
        showInstructions(mEditing
                && mDisplayingInstructions
                && !mDisplayingTrash
                && !mDisplayingToast);
        showTrash(mEditing && mDisplayingTrash && !mDisplayingToast);
        showToast(mDisplayingToast);
    }

    private void showBrightnessSlider(boolean show) {
        if (show) {
            // slide brightness in
            mBrightnessView
                    .animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mBrightnessView.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // slide out brightness
            mBrightnessView
                    .animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mBrightnessView.setVisibility(View.INVISIBLE);
                        }
                    });
        }
    }

    private void showInstructions(boolean show) {
        if (show) {
            // slide in instructions
            mEditTileInstructionView.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // animate instructions out
            mEditTileInstructionView.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showTrash(boolean show) {
        if (show) {
            // animate drop target in
            mDropTarget.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mDropTarget.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // drop target animates up
            mDropTarget.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mDropTarget.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mDropTarget.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showToast(boolean show) {
        if (show) {
            mToastView.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mToastView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mDisplayingToast = false;
                            mToastView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    animateToState();
                                }
                            }, TOAST_DURATION);
                        }
                    });
        } else {
            mToastView.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mToastView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mToastView.setVisibility(View.GONE);
                        }
                    });
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mListening) {
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }

    }

    class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.QS_SHOW_BRIGHTNESS_SLIDER), false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int currentUserId = ActivityManager.getCurrentUser();
            boolean showSlider = CMSettings.System.getIntForUser(resolver,
                    CMSettings.System.QS_SHOW_BRIGHTNESS_SLIDER, 1, currentUserId) == 1;
            if (showSlider != mHasBrightnessSliderToDisplay) {
                mHasBrightnessSliderToDisplay = showSlider;
                if (mAnimator == null && mBrightnessView != null) {
                    // no animations, set the visibility manually
                    mBrightnessView.setVisibility(showSlider ? View.VISIBLE : View.GONE);
                }
                requestLayout();
                animateToState();
            }
        }
    }
}
