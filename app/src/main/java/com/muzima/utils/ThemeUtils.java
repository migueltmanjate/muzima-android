/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.TypedValue;

import androidx.annotation.ColorInt;

import com.muzima.R;

public class ThemeUtils {

    private int lightThemeId;
    private int darkThemeId;
    private boolean lightMode;

    public ThemeUtils(boolean hideActionBar) {
        if(hideActionBar){
            System.out.println("Setting hidden Actionbar: "+hideActionBar);
            lightThemeId = R.style.AppTheme_Light_NoActionBar;
            darkThemeId = R.style.AppTheme_NoActionBar;
        } else {
            System.out.println("Setting shown Actionbar: "+hideActionBar);
            lightThemeId = R.style.AppTheme_Light;
            darkThemeId = R.style.AppTheme_Dark;
        }
    }

    public ThemeUtils(int lightThemeId, int darkThemeId) {
        this.lightThemeId = lightThemeId;
        this.darkThemeId = darkThemeId;
    }

    public void onCreate(Activity activity) {
        setThemeForActivity(activity);
    }

    public void onResume(Activity activity) {
        if (isLightModeChanged(activity)) {
            Intent intent = activity.getIntent();
            activity.finish();
            activity.overridePendingTransition(0, 0);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        }
    }

    private void setThemeForActivity(Activity activity) {
        lightMode = getPreferenceLightMode(activity);
        if (lightMode) {
            activity.setTheme(lightThemeId);
        } else {
            activity.setTheme(darkThemeId);
        }
        System.out.println("Theme is: "+lightMode+" => "+activity.getTheme().getResources()+" For activity : "+activity.getClass().getSimpleName());
    }

    public static boolean getPreferenceLightMode(Context context) {
        //check if night mode enabled or not
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String lightModeKey = context.getResources().getString(R.string.preference_light_mode);
        return preferences.getBoolean(lightModeKey, false);
    }

    public boolean isLightModeChanged(Context context) {
        return (lightMode != getPreferenceLightMode(context));
    }

    public static Drawable getIconWarning(Context context) {
        return context.getResources().getDrawable(R.drawable.ic_warning);
    }

    public static Drawable getIconRefresh(Context context) {
        return context.getResources().getDrawable(R.drawable.ic_refresh);
    }

    public int getThemeColor(Activity context){
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.primaryBackgroundColor, typedValue, true);
        @ColorInt int color = typedValue.data;
        return color;
    }
}
