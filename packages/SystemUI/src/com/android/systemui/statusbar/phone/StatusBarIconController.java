/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.rr.ColorHelper;
import com.android.internal.util.slim.DeviceUtils;
import com.android.internal.util.darkkat.StatusBarColorHelper;
import com.android.internal.util.NotificationColorUtil;
import com.android.systemui.BatteryLevelTextView;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Controls everything regarding the icons in the status bar and on Keyguard, including, but not
 * limited to: notification icons, signal cluster, additional status icons, and clock in the status
 * bar.
 */
public class StatusBarIconController implements Tunable {

    public static final long DEFAULT_TINT_ANIMATION_DURATION = 120;

    public static final String ICON_BLACKLIST = "icon_blacklist";

    private static final int GREETING_ALWAYS = 0;
    private static final int GREETING_HIDDEN = 2;

    private Context mContext;
    private View mStatusBar;
    private PhoneStatusBar mPhoneStatusBar;
    private Interpolator mLinearOutSlowIn;
    private Interpolator mFastOutSlowIn;
    private DemoStatusIcons mDemoStatusIcons;
    private NotificationColorUtil mNotificationColorUtil;

    private LinearLayout mSystemIconArea;
    private LinearLayout mStatusIcons;
    private SignalClusterView mSignalCluster;
    private LinearLayout mStatusIconsKeyguard;
    private LinearLayout mGreetingLayout;
    private TextView mGreetingView;
    private IconMerger mNotificationIcons;
    private View mNotificationIconArea;
    private ImageView mMoreIcon;
    private BatteryLevelTextView mBatteryLevelTextView;
    private BatteryMeterView mBatteryMeterView;
    private ClockController mClockController;
    private View mCenterClockLayout;

    private int mIconSize;
    private int mIconHPadding;

    private int mIconTint = Color.WHITE;
    private int mStatusIconsColor;
    private int mStatusIconsColorOld;
    private int mStatusIconsColorTint;

    private Ticker mTicker;
    private View mTickerView;

    private int mGreetingColor;
    private int mGreetingColorTint;
    private int mNetworkSignalColor;
    private int mNetworkSignalColorOld;
    private int mNetworkSignalColorTint;
    private int mNoSimColor;
    private int mNoSimColorOld;
    private int mNoSimColorTint;
    private int mAirplaneModeColor;
    private int mAirplaneModeColorOld;
    private int mAirplaneModeColorTint;
    private int mNotificationIconsColor;
    private int mNotificationIconsColorTint;
    private int mTickerTextColor;
    private int mTickerTextColorTint;

    private float mDarkIntensity;

    private boolean mTransitionPending;
    private boolean mTintChangePending;
    private float mPendingDarkIntensity;
    private ValueAnimator mTintAnimator;

    private int mDarkModeIconColorSingleTone;
    private int mLightModeIconColorSingleTone;


    private int mShowGreeting;
    private boolean mHideGreeting = false;
    private int mGreetingTimeout;
    private boolean mIsGreetingVisible = false;

    private static final int STATUS_ICONS_COLOR         = 0;
    private static final int NETWORK_SIGNAL_COLOR       = 1;
    private static final int NO_SIM_COLOR               = 2;
    private static final int AIRPLANE_MODE_COLOR        = 3;
    private int mColorToChange;

    private boolean mShowTicker;
    private boolean mTicking;
    private boolean mTickingEnd = false;

    private final Handler mHandler;
    private boolean mTransitionDeferring;
    private long mTransitionDeferringStartTime;
    private long mTransitionDeferringDuration;

    private Animator mColorTransitionAnimator;
    public Boolean mColorSwitch = false ;
    
    public  int CLOCK_STYLE_CENTERED  = mClockController.STYLE_CLOCK_CENTER;
    private int mClockStyle;

    private final ArraySet<String> mIconBlacklist = new ArraySet<>();

    private final Runnable mTransitionDeferringDoneRunnable = new Runnable() {
        @Override
        public void run() {
            mTransitionDeferring = false;
        }
    };

    public StatusBarIconController(Context context, View statusBar, View keyguardStatusBar,
            PhoneStatusBar phoneStatusBar) {
        mContext = context;
        mStatusBar = statusBar;
        mPhoneStatusBar = phoneStatusBar;
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
        mNotificationColorUtil = NotificationColorUtil.getInstance(context);
        mSystemIconArea = (LinearLayout) statusBar.findViewById(R.id.system_icon_area);
        mStatusIcons = (LinearLayout) statusBar.findViewById(R.id.statusIcons);
        mSignalCluster = (SignalClusterView) statusBar.findViewById(R.id.signal_cluster);
        mNotificationIconArea = statusBar.findViewById(R.id.notification_icon_area_inner);
        mGreetingLayout = (LinearLayout) statusBar.findViewById(R.id.status_bar_greeting_layout);
        mGreetingView = (TextView) statusBar.findViewById(R.id.status_bar_greeting_view);
        mNotificationIcons = (IconMerger) statusBar.findViewById(R.id.notificationIcons);
        mMoreIcon = (ImageView) statusBar.findViewById(R.id.moreIcon);
        mNotificationIcons.setOverflowIndicator(mMoreIcon);
        mStatusIconsKeyguard = (LinearLayout) keyguardStatusBar.findViewById(R.id.statusIcons);
        mBatteryLevelTextView =
                (BatteryLevelTextView) statusBar.findViewById(R.id.battery_level_text);
        mBatteryMeterView = (BatteryMeterView) statusBar.findViewById(R.id.battery);
        mLinearOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.linear_out_slow_in);
        mFastOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_slow_in);
        mDarkModeIconColorSingleTone = context.getColor(R.color.dark_mode_icon_color_single_tone);
        mLightModeIconColorSingleTone = context.getColor(R.color.light_mode_icon_color_single_tone);
        mHandler = new Handler();
        mClockController = new ClockController(statusBar, mNotificationIcons, mHandler);
        mCenterClockLayout = statusBar.findViewById(R.id.center_clock_layout);
        updateResources();

        TunerService.get(mContext).addTunable(this, ICON_BLACKLIST);
	
        setUpCustomColors();
        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);
    }

    private void setUpCustomColors() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if (mColorSwitch) {
        mStatusIconsColor = StatusBarColorHelper.getStatusIconsColor(mContext);
        mStatusIconsColorOld = mStatusIconsColor;
        mStatusIconsColorTint = mStatusIconsColor;
        mGreetingColor = StatusBarColorHelper.getGreetingColor(mContext);
        mGreetingColorTint = mGreetingColor;
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNetworkSignalColorOld = mNetworkSignalColor;
        mNetworkSignalColorTint = mNetworkSignalColor;
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mNoSimColorOld = mNoSimColor;
        mNoSimColorTint = mNoSimColor;
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mAirplaneModeColorOld = mAirplaneModeColor;
        mAirplaneModeColorTint = mAirplaneModeColor;
        mTickerTextColor = StatusBarColorHelper.getTickerTextColor(mContext);
        mTickerTextColorTint = mTickerTextColor;
        mNotificationIconsColor = StatusBarColorHelper.getNotificationIconsColor(mContext);
        mNotificationIconsColorTint = mNotificationIconsColor; 
	}
        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!ICON_BLACKLIST.equals(key)) {
            return;
        }
        mIconBlacklist.clear();
        mIconBlacklist.addAll(getIconBlacklist(newValue));
        ArrayList<StatusBarIconView> views = new ArrayList<StatusBarIconView>();
        // Get all the current views.
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            views.add((StatusBarIconView) mStatusIcons.getChildAt(i));
        }
        // Remove all the icons.
        for (int i = views.size() - 1; i >= 0; i--) {
            removeSystemIcon(views.get(i).getSlot(), i, i);
        }
        // Add them all back
        for (int i = 0; i < views.size(); i++) {
            addSystemIcon(views.get(i).getSlot(), i, i, views.get(i).getStatusBarIcon());
        }
    };

    public void updateResources() {
        mIconSize = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_size);
        mIconHPadding = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_padding);
        //mClockController.updateFontSize();
    }

    public void addSystemIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        boolean blocked = mIconBlacklist.contains(slot);
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
        StatusBarIconView view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIconsKeyguard.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        applyIconTint();
	if(mColorSwitch) {
        updateStatusIconsKeyguardColor();
	}
    }

    public void updateSystemIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {	
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
        StatusBarIconView view = (StatusBarIconView) mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
        view = (StatusBarIconView) mStatusIconsKeyguard.getChildAt(viewIndex);
        view.set(icon);
        applyIconTint();
        if(mColorSwitch) {
        updateStatusIconsKeyguardColor();
	}
    }

    public void removeSystemIcon(String slot, int index, int viewIndex) {
        mStatusIcons.removeViewAt(viewIndex);
        mStatusIconsKeyguard.removeViewAt(viewIndex);
    }

    public void updateNotificationIcons(NotificationData notificationData) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mIconSize + 2*mIconHPadding, mPhoneStatusBar.getStatusBarHeight());

        ArrayList<NotificationData.Entry> activeNotifications =
                notificationData.getActiveNotifications();
        final int N = activeNotifications.size();
        ArrayList<StatusBarIconView> toShow = new ArrayList<>(N);

        // Filter out ambient notifications and notification children.
        for (int i = 0; i < N; i++) {
            NotificationData.Entry ent = activeNotifications.get(i);
            if (notificationData.isAmbient(ent.key)
                    && !NotificationData.showNotificationEvenIfUnprovisioned(ent.notification)) {
                continue;
            }
            if (!PhoneStatusBar.isTopLevelChild(ent)) {
                continue;
            }
            toShow.add(ent.icon);
        }

        ArrayList<View> toRemove = new ArrayList<>();
        for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        final int toRemoveCount = toRemove.size();
        for (int i = 0; i < toRemoveCount; i++) {
            mNotificationIcons.removeView(toRemove.get(i));
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mNotificationIcons.addView(v, i, params);
            }
        }

        // Resort notification icons
        final int childCount = mNotificationIcons.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View actual = mNotificationIcons.getChildAt(i);
            StatusBarIconView expected = toShow.get(i);
            if (actual == expected) {
                continue;
            }
            mNotificationIcons.removeView(expected);
            mNotificationIcons.addView(expected, i);
        }

        applyNotificationIconsTint();
    }

    public void showGreeting(boolean isPreview) {
        if (mIsGreetingVisible || mTicking) {
            return;
        }
        mIsGreetingVisible = true;
        if (isPreview) {
            hideSystemIconArea(true);
            hideNotificationIconArea(true);
        } else {
            animateHide(mSystemIconArea, false);
            if (mClockStyle == CLOCK_STYLE_CENTERED) {
                animateHide(mCenterClockLayout, false);
            }
            animateHide(mNotificationIconArea, false);
        }
        animateShow(mGreetingLayout, true, true);
    }

    public void hideGreeting() {
        animateShow(mSystemIconArea, true);
        if (mClockStyle == CLOCK_STYLE_CENTERED) {
            animateShow(mCenterClockLayout, true);
        }
        animateShow(mNotificationIconArea, true);
        animateHide(mGreetingLayout, true, true);
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        animateHide(mCenterClockLayout, animate);
    }

    public void showSystemIconArea(boolean animate) {
        if (mShowGreeting != GREETING_HIDDEN && !mHideGreeting && animate) {
            showGreeting(false);
        } else {
            animateShow(mSystemIconArea, animate);
            if (mClockStyle == CLOCK_STYLE_CENTERED) {
                animateShow(mCenterClockLayout, animate);
            }
        }
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconArea, animate);
        animateHide(mCenterClockLayout, animate);
    }

    public void showNotificationIconArea(boolean animate) {
        if (mShowGreeting == GREETING_HIDDEN || mHideGreeting || !animate) {
            animateShow(mNotificationIconArea, animate);
            animateShow(mCenterClockLayout, animate);
        }
    }

    public void setClockVisibility(boolean visible) {
        mClockController.setVisibility(visible);
    }

    public void dump(PrintWriter pw) {
        int N = mStatusIcons.getChildCount();
        pw.println("  system icons: " + N);
        for (int i=0; i<N; i++) {
            StatusBarIconView ic = (StatusBarIconView) mStatusIcons.getChildAt(i);
            pw.println("    [" + i + "] icon=" + ic);
        }
    }

    public void dispatchDemoCommand(String command, Bundle args) {
        if (mDemoStatusIcons == null) {
            mDemoStatusIcons = new DemoStatusIcons(mStatusIcons, mIconSize);
        }
        mDemoStatusIcons.dispatchDemoCommand(command, args);
    }

    private void animateHide(final View v, boolean animate) {
        animateHide(v, animate, false);
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate, final boolean isGreeting) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(View.INVISIBLE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(mIsGreetingVisible && isGreeting ? mGreetingTimeout : 0)
                .setInterpolator(PhoneStatusBar.ALPHA_OUT)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.INVISIBLE);
                        if (mTickingEnd) {
                            mTicking = false;
                            mTickingEnd = false;
                        }
                        if (isGreeting) {
                            mIsGreetingVisible = false;
                            mHideGreeting = true;
                        }
                    }
                });
    }

    private void animateShow(View v, boolean animate) {
        animateShow(v, animate, false);
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate, boolean isGreeting) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(PhoneStatusBar.ALPHA_IN)
                .setStartDelay(mIsGreetingVisible && !isGreeting ? mGreetingTimeout : 50);

        if (isGreeting) {
            v.animate()
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            hideGreeting();
                        }
                    });
        } else {
            // We need to clean up any pending end action from animateHide if we call
            // both hide and show in the same frame before the animation actually gets started.
            // cancel() doesn't really remove the end action.
            v.animate()
                    .withEndAction(null);
        }

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mPhoneStatusBar.isKeyguardFadingAway() && !mIsGreetingVisible && !isGreeting) {
            v.animate()
                    .setDuration(mPhoneStatusBar.getKeyguardFadingAwayDuration())
                    .setInterpolator(mLinearOutSlowIn)
                    .setStartDelay(mPhoneStatusBar.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    public void setIconsDark(boolean dark, boolean animate) {
        if (!animate) {
            setIconTintInternal(dark ? 1.0f : 0.0f);
        } else if (mTransitionPending) {
            deferIconTintChange(dark ? 1.0f : 0.0f);
        } else if (mTransitionDeferring) {
            animateIconTint(dark ? 1.0f : 0.0f,
                    Math.max(0, mTransitionDeferringStartTime - SystemClock.uptimeMillis()),
                    mTransitionDeferringDuration);
        } else {
            animateIconTint(dark ? 1.0f : 0.0f, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
    }

    private void animateIconTint(float targetDarkIntensity, long delay,
            long duration) {
        if (mTintAnimator != null) {
            mTintAnimator.cancel();
        }
        if (mDarkIntensity == targetDarkIntensity) {
            return;
        }
        mTintAnimator = ValueAnimator.ofFloat(mDarkIntensity, targetDarkIntensity);
        mTintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setIconTintInternal((Float) animation.getAnimatedValue());
            }
        });
        mTintAnimator.setDuration(duration);
        mTintAnimator.setStartDelay(delay);
        mTintAnimator.setInterpolator(mFastOutSlowIn);
        mTintAnimator.start();
    }

    private void setIconTintInternal(float darkIntensity) {
        mDarkIntensity = darkIntensity;
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;	
 
	if (mColorSwitch) {
        mStatusIconsColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mStatusIconsColor, StatusBarColorHelper.getStatusIconsColorDark(mContext));
        mGreetingColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mGreetingColor,  StatusBarColorHelper.getGreetingColorDark(mContext));
        mNetworkSignalColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mNetworkSignalColor, StatusBarColorHelper.getNetworkSignalColorDark(mContext));
        mNoSimColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mNoSimColor, StatusBarColorHelper.getNoSimColorDark(mContext));
        mAirplaneModeColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mAirplaneModeColor, StatusBarColorHelper.getAirplaneModeColorDark(mContext));
        mTickerTextColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mTickerTextColor, StatusBarColorHelper.getTickerTextColorDark(mContext));
        mNotificationIconsColorTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
                mNotificationIconsColor, StatusBarColorHelper.getNotificationIconsColorDark(mContext));
	} else {
		 mIconTint = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity,
              mLightModeIconColorSingleTone, mDarkModeIconColorSingleTone);
	}
        applyIconTint();
    }  

    private void deferIconTintChange(float darkIntensity) {
        if (mTintChangePending && darkIntensity == mPendingDarkIntensity) {
            return;
        }
        mTintChangePending = true;
        mPendingDarkIntensity = darkIntensity;
    }

    public void applyIconTint() {
        mGreetingView.setTextColor(mGreetingColorTint);
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;	
	int batterytext = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.BATTERY_TEXT_COLOR, 0xFFFFFFFF);
        int mBatteryIconColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.BATTERY_ICON_COLOR, 0xFFFFFFFF);
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mStatusIcons.getChildAt(i);
	    if (mColorSwitch) {
            v.setImageTintList(ColorStateList.valueOf(mStatusIconsColorTint));
	    } else {
	    v.setImageTintList(ColorStateList.valueOf(mIconTint));
        	}
	}
	if (mColorSwitch) {
        mSignalCluster.setIconTint(
                mNetworkSignalColorTint, mNoSimColorTint, mAirplaneModeColorTint, mDarkIntensity);
        mMoreIcon.setImageTintList(ColorStateList.valueOf(mNotificationIconsColorTint));
	mBatteryLevelTextView.setTextColor(batterytext);
	mBatteryMeterView.setDarkIntensity(mBatteryIconColor);
	} else {
	mSignalCluster.setIconStockTint(mIconTint, mDarkIntensity);
        mMoreIcon.setImageTintList(ColorStateList.valueOf(mIconTint));
	mBatteryLevelTextView.setTextColor(mIconTint);
        mBatteryMeterView.setDarkIntensity(mDarkIntensity);
        }
        //mClockController.setTextColor(mIconTint);
        applyNotificationIconsTint();	
        if (mShowTicker && mTicker != null && mTickerView != null) {
            mTicker.setTextColor(mTickerTextColorTint);
        }
    }

    private void applyNotificationIconsTint() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;	
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
            boolean colorize = !isPreL || isGrayscale(v);
            if (colorize) {
		if (mColorSwitch) {
                v.setImageTintList(ColorStateList.valueOf(mNotificationIconsColorTint));
		} else {
		v.setImageTintList(ColorStateList.valueOf(mIconTint));
		}
            }
        }
        if (mShowTicker && mTicker != null && mTickerView != null) {
            mTicker.setIconColorTint(ColorStateList.valueOf(mNotificationIconsColorTint));
        }
    }

    private boolean isGrayscale(StatusBarIconView v) {
        Object isGrayscale = v.getTag(R.id.icon_is_grayscale);
        if (isGrayscale != null) {
            return Boolean.TRUE.equals(isGrayscale);
        }
        boolean grayscale = mNotificationColorUtil.isGrayscaleIcon(v.getDrawable());
        v.setTag(R.id.icon_is_grayscale, grayscale);
        return grayscale;
    }

    public void appTransitionPending() {
        mTransitionPending = true;
    }

    public void appTransitionCancelled() {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
        mTransitionPending = false;
    }

    public void appTransitionStarting(long startTime, long duration) {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity,
                    Math.max(0, startTime - SystemClock.uptimeMillis()),
                    duration);

        } else if (mTransitionPending) {

            // If we don't have a pending tint change yet, the change might come in the future until
            // startTime is reached.
            mTransitionDeferring = true;
            mTransitionDeferringStartTime = startTime;
            mTransitionDeferringDuration = duration;
            mHandler.removeCallbacks(mTransitionDeferringDoneRunnable);
            mHandler.postAtTime(mTransitionDeferringDoneRunnable, startTime);
        }
        mTransitionPending = false;
    }

    public static ArraySet<String> getIconBlacklist(String blackListStr) {
        ArraySet<String> ret = new ArraySet<String>();
        if (blackListStr != null) {
            String[] blacklist = blackListStr.split(",");
            for (String slot : blacklist) {
                if (!TextUtils.isEmpty(slot)) {
                    ret.add(slot);
                }
            }
        }
        return ret;
    }

    private ValueAnimator createColorTransitionAnimator(float start, float end) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float position = animation.getAnimatedFraction();
                int blendedFrame;
                int blended;
                if (mColorToChange == NETWORK_SIGNAL_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mNetworkSignalColorOld, mNetworkSignalColor, position);
                    mSignalCluster.applyNetworkSignalTint(blended);
                } else if (mColorToChange == NO_SIM_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mNoSimColorOld, mNoSimColor, position);
                    mSignalCluster.applyNoSimTint(blended);
                } else if (mColorToChange == AIRPLANE_MODE_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mAirplaneModeColorOld, mAirplaneModeColor, position);
                    mSignalCluster.applyAirplaneModeTint(blended);
                 } else if (mColorToChange == STATUS_ICONS_COLOR) {
                    blended = ColorHelper.getBlendColor(
                            mStatusIconsColorOld, mStatusIconsColor, position);
                    for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
                        StatusBarIconView v = (StatusBarIconView) mStatusIcons.getChildAt(i);
                        v.setImageTintList(ColorStateList.valueOf(blended));
                    }
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mColorToChange == NETWORK_SIGNAL_COLOR) {
                    mNetworkSignalColorOld = mNetworkSignalColor;
                    mNetworkSignalColorTint = mNetworkSignalColor;
                } else if (mColorToChange == NO_SIM_COLOR) {
                    mNoSimColorOld = mNoSimColor;
                    mNoSimColorTint = mNoSimColor;
                } else if (mColorToChange == AIRPLANE_MODE_COLOR) {
                    mAirplaneModeColorOld = mAirplaneModeColor;
                    mAirplaneModeColorTint = mAirplaneModeColor;
                } else if (mColorToChange == STATUS_ICONS_COLOR) {
                    mStatusIconsColorOld = mStatusIconsColor;
                    mStatusIconsColorTint = mStatusIconsColor;
                }
            }
        });
        return animator;
    }

    public void refreshAllStatusBarIcons() {
        refreshAllIconsForLayout(mStatusIcons);
        refreshAllIconsForLayout(mStatusIconsKeyguard);
        refreshAllIconsForLayout(mNotificationIcons);
    }

    public LinearLayout getStatusIcons() {
        return mStatusIcons;
    }

    private void refreshAllIconsForLayout(LinearLayout ll) {
        final int count = ll.getChildCount();
        for (int n = 0; n < count; n++) {
            View child = ll.getChildAt(n);
            if (child instanceof StatusBarIconView) {
                ((StatusBarIconView) child).updateDrawable();
            }
        }
    }

    public void updateStatusIconsColor() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
        
	if(mColorSwitch) {
	mStatusIconsColor = StatusBarColorHelper.getStatusIconsColor(mContext);
       		 if (mStatusIcons.getChildCount() > 0) {
           		 mColorToChange = STATUS_ICONS_COLOR;
            		 mColorTransitionAnimator.start();
       		} else {
            		mStatusIconsColorOld = mStatusIconsColor;
            		mStatusIconsColorTint = mStatusIconsColor;
       			 }
	} else {
      	applyIconTint();
	}
    }

    public void updateStatusIconsKeyguardColor() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if(mColorSwitch) {
        if (mStatusIconsKeyguard.getChildCount() > 0) {
            for (int index = 0; index < mStatusIconsKeyguard.getChildCount(); index++) {
                StatusBarIconView v = (StatusBarIconView) mStatusIconsKeyguard.getChildAt(index);
                v.setImageTintList(ColorStateList.valueOf(mStatusIconsColor));
        	    }
        	}
	} else {
	applyIconTint();
	}
    }


    public void updateNetworkIconColors() {
	mColorSwitch =  Settings.System.getInt(mContext.getContentResolver(),
				 Settings.System.STATUSBAR_COLOR_SWITCH, 0) == 1;
	if (mColorSwitch) {
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mNetworkSignalColorOld = mNetworkSignalColor;
        mNoSimColorOld = mNoSimColor;
        mAirplaneModeColorOld = mAirplaneModeColor;
        mNetworkSignalColorTint = mNetworkSignalColor;
        mNoSimColorTint = mNoSimColor;
        mAirplaneModeColorTint = mAirplaneModeColor;
        mSignalCluster.setIconTint(mNetworkSignalColor, mNoSimColor, mAirplaneModeColor, mDarkIntensity);
	} 
    }

    public void updateNetworkSignalColor() {
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mColorToChange = NETWORK_SIGNAL_COLOR;
        mColorTransitionAnimator.start();
	}

    public void updateNoSimColor() {
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mColorToChange = NO_SIM_COLOR;
        mColorTransitionAnimator.start();
	}

    public void updateAirplaneModeColor() {
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mColorToChange = AIRPLANE_MODE_COLOR;
        mColorTransitionAnimator.start();
	}

    public void resetHideGreeting() {
        if (mShowGreeting == GREETING_ALWAYS) {
            mHideGreeting = false;
        }
    }

    public void updateShowGreeting(int show) {
        mShowGreeting = show;
    }

    public void updateGreetingText(String text) {
        mGreetingView.setText(text);
    }

    public void updateGreetingTimeout(int timeout) {
        mGreetingTimeout = timeout;
    }

    public void updateGreetingColor() {
        mGreetingColor = StatusBarColorHelper.getGreetingColor(mContext);
        mGreetingView.setTextColor(mGreetingColor);
        mGreetingColorTint = mGreetingColor;
    }

    public void updateShowTicker(boolean show) {
        mShowTicker = show;
        inflateTickerView();
    }

    public void updateNotificationIconsColor() {
	if(mColorSwitch) {
        mNotificationIconsColor = StatusBarColorHelper.getNotificationIconsColor(mContext);
        mNotificationIconsColorTint = mNotificationIconsColor;
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
            boolean colorize = !isPreL || isGrayscale(v);
            if (colorize) {
                v.setImageTintList(ColorStateList.valueOf(mNotificationIconsColor));
            }
        }
        mMoreIcon.setImageTintList(ColorStateList.valueOf(mNotificationIconsColor));
	} else {
	applyIconTint();
		}
	updateTickerIconColor(mNotificationIconsColor);
	}


    private void updateTickerIconColor(int color) {
        if (mShowTicker && mTicker != null && mTickerView != null) {
            mTicker.setIconColorTint(ColorStateList.valueOf(color));
        }
    }

    public void updateTickerTextColor() {
        mTickerTextColor = StatusBarColorHelper.getTickerTextColor(mContext);
        mTickerTextColorTint = mTickerTextColor;
        if (mShowTicker && mTicker != null && mTickerView != null) {
            mTicker.setTextColor(mTickerTextColor);
        }
    }

    private void inflateTickerView() {
        if (mShowTicker && (mTicker == null || mTickerView == null)) {
            final ViewStub tickerStub = (ViewStub) mStatusBar.findViewById(R.id.ticker_stub);
            if (tickerStub != null) {
                mTickerView = tickerStub.inflate();
                mTicker = new MyTicker(mContext, mStatusBar);

                TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
                tickerView.mTicker = mTicker;
                updateTickerIconColor(mNotificationIconsColor);
                updateTickerTextColor();
            } else {
                mShowTicker = false;
            }
        }
    }

    public void addTickerEntry(StatusBarNotification n) {
        mTicker.addEntry(n);
    }

    public void removeTickerEntry(StatusBarNotification n) {
        mTicker.removeEntry(n);
    }

    public void haltTicker() {
        if (mTicking) {
            mTicker.halt();
        }
    }

    private class MyTicker extends Ticker {
        MyTicker(Context context, View sb) {
            super(context, sb);
        }

        @Override
        public void tickerStarting() {
            if (!mShowTicker || mIsGreetingVisible) return;
            mTicking = true;
            hideSystemIconArea(true);
            hideNotificationIconArea(true);
            animateShow(mTickerView, true);
        }

        @Override
        public void tickerDone() {
            if (!mShowTicker || mIsGreetingVisible) return;
            animateShow(mSystemIconArea, true);
            if (mClockStyle == CLOCK_STYLE_CENTERED) {
                animateShow(mCenterClockLayout, true);
            }
            animateShow(mNotificationIconArea, true);
            mTickingEnd = true;
            animateHide(mTickerView, true);
        }

        public void tickerHalting() {
            if (!mShowTicker || mIsGreetingVisible) return;
            animateShow(mSystemIconArea, true);
            if (mClockStyle == CLOCK_STYLE_CENTERED) {
                animateShow(mCenterClockLayout, true);
            }
            animateShow(mNotificationIconArea, true);
            // we do not animate the ticker away at this point, just get rid of it (b/6992707)
            mTickerView.setVisibility(View.GONE);
        }
    }
}
