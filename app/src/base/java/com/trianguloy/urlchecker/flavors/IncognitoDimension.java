package com.trianguloy.urlchecker.flavors;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;

import com.trianguloy.urlchecker.modules.companions.OnOffConfig;
import com.trianguloy.urlchecker.utilities.methods.JavaUtils;

public interface IncognitoDimension {
    static boolean isIncognito(Intent intent) {
        return false;
    }

    static void showSettings(Activity activity) {
    }

    static void applyAndLaunchHelper(Context context, Intent intent, String url, boolean state) {
    }
}
