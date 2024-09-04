package com.trianguloy.forceurl.utilities;

import android.content.ClipData;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import com.trianguloy.forceurl.lib.Preferences;
import com.trianguloy.forceurl.utilities.methods.AndroidUtils;
import com.trianguloy.forceurl.BuildConfig;
import com.trianguloy.forceurl.R;

public class ClipboardBorrower {
    private volatile static ClipData previous;
    private volatile static ClipData borrowerData;
    private static final String LABEL = "clipboard_borrower_text";

    private static void clear() {
        previous = null;
        borrowerData = null;
    }

    // TODO: maybe notifications allows the app to read the clipboard even in background

    /**
     * {@link #borrow(Context, ClipData)} but we provide the label {@value LABEL}
     *
     * @param text The text which we will put into the clipboard
     */
    public static boolean borrow(Context context, String text) {
        return borrow(context, ClipData.newPlainText(LABEL, text));
    }

    /**
     * Puts content into the clipboard for the user to use. Can be called multiple times before
     * {@link #release}.
     *
     * @param borrowerData The content which we will put into the clipboard
     */
    public synchronized static boolean borrow(Context context, ClipData borrowerData) {
        if (store(context)) {
            ClipboardBorrower.borrowerData = borrowerData;
            AndroidUtils.setPrimaryClip(context, R.string.borrow_borrowSet, ClipboardBorrower.borrowerData);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Restores the content of the clipboard. We will restore the last thing the user copied to the
     * clipboard.
     *
     * @param storeBeforeRestore Check first if the clipboard has changed, since the last call to
     *                           {@link #release} or {@link #borrow}. Mainly used to avoid the toast.
     */
    public synchronized static boolean release(Context context, boolean storeBeforeRestore) {
        // Read clipboard and check if there has been any changes since we last borrowed it, store
        // the changes in previous
        boolean storeSuccess = false;
        if (storeBeforeRestore) storeSuccess = store(context);
        // We always restore, even if we couldn't check the clipboard, in practice this means that
        // if the user copies something to the clipboard after the last call to release or borrow,
        // we can't restore that.
        if (previous != null) {
            AndroidUtils.setPrimaryClip(context, R.string.borrow_releaseSet, previous);
        }

        clear();
        return storeSuccess;
    }

    /**
     * A way to use the {@link #release} method from the background in higher android
     * versions (10+).
     * <p>
     * Relies on window drawing, so it requires the SYSTEM_ALERT_WINDOW permission. Because it will
     * be run as {@link android.view.ViewTreeObserver.OnPreDrawListener}, it won't be done
     * immediately.
     * <p>
     * Can't store if we can't create a floating window, i.e. if the user is in the android system
     * sharing dialog.
     * <p>
     * Has some problems, i.e. if the user is scrolling we will get focus for an instant, so we
     * steal the scrolling from the user.
     */
    public static void releaseFromBubble(Context context) {
        if (!android.provider.Settings.canDrawOverlays(context)) {
            AndroidUtils.safeToast(context, R.string.borrow_drawError, Toast.LENGTH_LONG);
            return;
        }
        var windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        var floatView = LayoutInflater.from(context).inflate(R.layout.bubble, null);
        var layoutParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                0,
                0,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        LayoutParams.TYPE_APPLICATION_OVERLAY :
                        LayoutParams.TYPE_TOAST,
                LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        floatView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            // TODO: add way so that just when it is unrendering, we read the clipboard

            @Override
            public boolean onPreDraw() {
                // There is no reason to not storeBeforeRelease, otherwise the call could be done
                // without the bubble. The only reason might be emergencies.
                release(floatView.getContext(), true);
                floatView.getViewTreeObserver().removeOnPreDrawListener(this);
                windowManager.removeView(floatView);
                return false;
            }
        });

        AndroidUtils.runOnUiThread(() -> {
            windowManager.addView(floatView, layoutParams);
        });
    }

    public synchronized static boolean releaseSmart(Context context) {
        var storeBeforeRelease = Preferences.STOREBEFORERELEASE_PREF(context).get();
        return releaseSmart(context, storeBeforeRelease);
    }

    /**
     * Will choose between {@link #release} and its wrappers so that it selects the best
     * for the current moment, avoiding unnecessary compatibility layers. What this means is that it
     * won't call a background wrapper when in focus and things like that.
     */
    public synchronized static boolean releaseSmart(Context context, boolean storeBeforeRelease) {
        // If we don't store before releasing we don't need to check if we can read the clipboard
        var releaseNormally = !storeBeforeRelease || AndroidUtils.canReadClip(context);
        // Clipboard is always available under API 29 (Android 10)
        if (releaseNormally) {
            release(context, storeBeforeRelease);
        } else {
            // API level is AT LEAST 29 (can't use clipboard)
            if (android.provider.Settings.canDrawOverlays(context)) {
                releaseFromBubble(context);
            } else {
                // No permissions, we can't read the clipboard, so we are unable to respect changes
                // to the clipboard
                // TODO: recommend user give the app drawing permissions?
                // Last resort, ignore user preferences
                release(context, false);
            }
        }
        return releaseNormally;
    }

    /**
     * To keep the last thing the user copied to the clipboard
     *
     * @return if the clipboard is accessible
     */
    private synchronized static boolean store(Context context) {
        // Retrieve clip data
        ClipData current = AndroidUtils.getPrimaryClip(context, R.string.borrow_storeGet);

        // Clipboard not accessible
        if (current == null) {
            return false;
        }

        // If it is the first time this method is called in this lifecycle
        if (previous == null) {
            // Store clip data
            previous = current;
        } else {
            // if previous clipboard and current differ, that means the clipboard
            // has been used while borrowing, if that happens we keep the content
            if (!isEqualsToBorrower(current, context)) {
                // maybe the method should instead return if it entered this condition
                // that way we can avoid putting into the clipboard what is already there
                previous = current;
            }
        }
        return true;
    }

    private static boolean isEqualsToBorrower(ClipData clip, Context context) {
        return areClipDataEquals(clip, borrowerData, context);
    }

    private static boolean areClipDataEquals(ClipData clip1, ClipData clip2, Context context) {
        // ---
        // XXX: For debugging purposes: emulator shared clipboard may trip this up, as it is
        //  constantly syncing and messes up the label.
        //  It is recommended to disable it.
        //      - Android studio: File -> Settings -> Tools -> Emulator -> Enable clipboard sharing
        //
        //  Still it didn't work for me and it kept changing the label

        // Use case where the label is relevant:
        //   User opens URL in incognito, pastes URL, then remembers it wants to copy it to the
        //   clipboard so it copies it. Now the label is different, but the text is not.
        var checkLabel = !BuildConfig.DEBUG;

        // ---
        var count1 = clip1.getItemCount();
        var count2 = clip2.getItemCount();
        var label1 = clip1.getDescription().getLabel();
        var label2 = clip2.getDescription().getLabel();
        // ---


        if (count1 == count2 &&
                (!checkLabel || label1.equals(label2))) {
            for (int i = 0; i < clip1.getItemCount(); i++) {
                // ---
                var sameMimeType = clip1.getDescription().getMimeType(i).equals(
                        clip2.getDescription().getMimeType(i));
                var sameText = clip1.getItemAt(i).coerceToText(context).equals(
                        clip2.getItemAt(i).coerceToText(context));
                // ---

                if (sameMimeType && sameText) {
                    // more conditions if needed
                } else {
                    return false;
                }
            }

            // no differences, they are equal
            return true;
        } else {
            return false;
        }
    }
}