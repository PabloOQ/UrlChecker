package com.trianguloy.forceurllib.helpers.list;

import android.content.Context;

import com.trianguloy.forceurllib.utilities.ClipboardBorrower;
import com.trianguloy.forceurllib.helpers.AHelper;
import com.trianguloy.forceurllib.lib.Preferences;
import com.trianguloy.forceurllib.utilities.Bubble;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SemiautoBubbleHelper implements AHelper {
    // Only one bubble
    private static volatile Bubble bubble = null;
    private static volatile ScheduledThreadPoolExecutor executor = null;
    private static volatile ScheduledFuture<?> task = null;
    private static final Object lock = ClipboardBorrower.class;

    /**
     * Launches a Bubble so we can access the clipboard "from the background", if tapped it
     * restores the clipboard at that moment.
     * When restoring the clipboard the Bubble dissapears.
     */
    @Override
    public void run(Context context, String url, String pckg, String mode) {
        synchronized (lock) {
            // Is it possible this could run before relaseSafely(from background) finishes
            // releasing? I think not, but I'm not sure
            ClipboardBorrower.borrow(context, url);

            // The bubble will probably outlive any context
            Context finalContext = context.getApplicationContext();

            if (executor == null) {
                executor = new ScheduledThreadPoolExecutor(1);
            }
            if (task != null) {
                task.cancel(true);
            }
            if (bubble == null) {
                // bubble wil be popped synchronized UNTIL this action is executed
                bubble = new Bubble(finalContext, bubbleContext -> {
                    synchronized (lock) {
                        if (bubble != null) {
// FIXME: breakpoint here, run app, open in incognito, tap bubble, wait for "URLCheck isn't
//  responding", continue execution, can't access clipboard
//  just after getPrimaryClip() on getPrimaryClip() when URLCheck isn't responding
//  Probably can't be fixed, it just loses clipboard access.
                            ClipboardBorrower.releaseSmart(bubbleContext);
                            task.cancel(true);
                            executor = null;
                            task = null;
                            bubble = null;
                        }
                    }
                });
            }
            task = executor.schedule(() -> {
                synchronized (lock) {
                    if (!Thread.interrupted()) {
                        bubble.pop();
                    }
                }
            }, Preferences.TIMER_PREF(context).get(), TimeUnit.SECONDS);
        }
    }
}
