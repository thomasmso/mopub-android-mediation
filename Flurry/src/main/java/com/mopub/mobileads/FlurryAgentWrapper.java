package com.mopub.mobileads;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.flurry.android.FlurryAgent;
import com.flurry.android.FlurryAgentListener;

public final class FlurryAgentWrapper {
    public static final String PARAM_API_KEY = "apiKey";
    public static final String PARAM_AD_SPACE_NAME = "adSpaceName";

    private static final String ORIGIN_IDENTIFIER = "Flurry_Mopub_Android";
    private static final String ORIGIN_VERSION = "6.5.0";

    private FlurryAgent.Builder mAgentBuilder;

    public static FlurryAgentWrapper getInstance() {
        return FlurryAgentLoader.INSTANCE;
    }

    private FlurryAgentWrapper() {
        mAgentBuilder = new FlurryAgent.Builder()
                .withLogEnabled(false)
                .withLogLevel(Log.INFO);

        FlurryAgent.addOrigin(ORIGIN_IDENTIFIER, ORIGIN_VERSION);
    }

    public synchronized void startSession(@NonNull final Context context,
                                          final String apiKey,
                                          @Nullable FlurryAgentListener flurryAgentListener) {
        // validate parameters
        if (TextUtils.isEmpty(apiKey)) {
            return;
        }

        // init
        if (!FlurryAgent.isSessionActive()) {
            mAgentBuilder.withListener(flurryAgentListener) // withListener allows nulls
                    .build(context, apiKey);
            FlurryAgent.onStartSession(context);
        }
    }

    public synchronized void endSession(final Context context) {
        // validate parameters
        if (context == null) {
            return;
        }

        if (FlurryAgent.isSessionActive()) {
            FlurryAgent.onEndSession(context);
        }
    }

    public synchronized boolean isSessionActive() {
        return FlurryAgent.isSessionActive();
    }

    private static class FlurryAgentLoader {
        private static final FlurryAgentWrapper INSTANCE = new FlurryAgentWrapper();
    }
}
