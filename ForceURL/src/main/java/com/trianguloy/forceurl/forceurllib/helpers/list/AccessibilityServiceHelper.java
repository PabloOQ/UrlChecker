package com.trianguloy.forceurl.forceurllib.helpers.list;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.trianguloy.forceurl.forceurllib.data.AccessibilityFunction;
import com.trianguloy.forceurl.forceurllib.helpers.AHelper;
import com.trianguloy.forceurl.forceurllib.lib.ForceUrl;
import com.trianguloy.forceurl.forceurllib.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.R;
import com.trianguloy.forceurl.forceurllib.services.UrlHelperService;

public class AccessibilityServiceHelper implements AHelper {
    @Override
    public void run(Context context, String url, String pckg, String mode) {
        var instance = UrlHelperService.getInstance();
        if (instance == null) {
            // FIXME: is this enough if it is not online? maybe do the check in open module?
            AndroidUtils.safeToast(context, R.string.helperService_notConnected, Toast.LENGTH_LONG);
        } else {
            var app = ForceUrl.getApps(ForceUrl.findId(context, new Intent().setPackage(pckg), mode));
            instance.openService(context, url, pckg, (node) -> ((AccessibilityFunction) app).putUrl(node, url, pckg));
        }
    }
}
