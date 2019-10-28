package com.mopub.mobileads;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.AdListener;
import com.facebook.ads.AdSize;
import com.facebook.ads.AdView;
import com.facebook.ads.AudienceNetworkAds;
import com.mopub.common.DataKeys;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.util.Views;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class FacebookBanner extends CustomEventBanner implements AdListener {
    private static final String PLACEMENT_ID_KEY = "placement_id";
    private static final String ADAPTER_NAME = FacebookBanner.class.getSimpleName();
    private static AtomicBoolean sIsInitialized = new AtomicBoolean(false);

    private AdView mFacebookBanner;
    private CustomEventBannerListener mBannerListener;
    @NonNull
    private FacebookAdapterConfiguration mFacebookAdapterConfiguration;

    /**
     * CustomEventBanner implementation
     */

    public FacebookBanner() {
        mFacebookAdapterConfiguration = new FacebookAdapterConfiguration();
    }

    @Override
    protected void loadBanner(final Context context,
                              final CustomEventBannerListener customEventBannerListener,
                              final Map<String, Object> localExtras,
                              final Map<String, String> serverExtras) {
        if (!sIsInitialized.getAndSet(true)) {
            AudienceNetworkAds.initialize(context);
        }

        setAutomaticImpressionAndClickTracking(false);

        mBannerListener = customEventBannerListener;

        final String placementId;
        if (serverExtrasAreValid(serverExtras)) {
            placementId = serverExtras.get(PLACEMENT_ID_KEY);
            mFacebookAdapterConfiguration.setCachedInitializationParameters(context, serverExtras);
        } else {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            if (mBannerListener != null) {
                mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_NO_FILL);
            }
            return;
        }

        int height;
        if (localExtrasAreValid(localExtras)) {
            height = (Integer) localExtras.get(DataKeys.AD_HEIGHT);
        } else {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            if (mBannerListener != null) {
                mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_NO_FILL);
            }
            return;
        }

        AdSize adSize = calculateAdSize(height);

        mFacebookBanner = new AdView(context, placementId, adSize);
        mFacebookBanner.setAdListener(this);

        final String adm = serverExtras.get(DataKeys.ADM_KEY);
        if (!TextUtils.isEmpty(adm)) {
            mFacebookBanner.loadAdFromBid(adm);
            MoPubLog.log(placementId, LOAD_ATTEMPTED, ADAPTER_NAME);
        } else {
            mFacebookBanner.loadAd();
            MoPubLog.log(placementId, LOAD_ATTEMPTED, ADAPTER_NAME);
        }
    }

    @Override
    protected void onInvalidate() {
        if (mFacebookBanner != null) {
            Views.removeFromParent(mFacebookBanner);
            mFacebookBanner.destroy();
            mFacebookBanner = null;
        }
    }

    /**
     * AdListener implementation
     */

    @Override
    public void onAdLoaded(Ad ad) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Facebook banner ad loaded successfully. Showing ad...");

        if (mBannerListener != null) {
            mBannerListener.onBannerLoaded(mFacebookBanner);
            MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
            MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);
            MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
        }
    }

    @Override
    public void onError(final Ad ad, final AdError error) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Facebook banner ad failed to load.");

        if (mBannerListener != null) {
            if (error.getErrorCode() == AdError.NO_FILL.getErrorCode()) {
                mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_NO_FILL);
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            } else if (error.getErrorCode() == AdError.INTERNAL_ERROR.getErrorCode()) {
                mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_INVALID_STATE);
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_INVALID_STATE.getIntCode(), MoPubErrorCode.NETWORK_INVALID_STATE);
            } else {
                mBannerListener.onBannerFailed(MoPubErrorCode.UNSPECIFIED);
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.UNSPECIFIED.getIntCode(), MoPubErrorCode.UNSPECIFIED);
            }
        }
    }

    @Override
    public void onAdClicked(Ad ad) {
        if (mBannerListener != null) {
            mBannerListener.onBannerClicked();
            MoPubLog.log(CLICKED, ADAPTER_NAME);
        }
    }

    @Override
    public void onLoggingImpression(Ad ad) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Facebook banner ad logged impression.");
        if (mBannerListener != null) {
            mBannerListener.onBannerImpression();
        }
    }

    private boolean serverExtrasAreValid(final Map<String, String> serverExtras) {
        final String placementId = serverExtras.get(PLACEMENT_ID_KEY);
        return (placementId != null && placementId.length() > 0);
    }

    private boolean localExtrasAreValid(@NonNull final Map<String, Object> localExtras) {
        return localExtras.get(DataKeys.AD_WIDTH) instanceof Integer
                && localExtras.get(DataKeys.AD_HEIGHT) instanceof Integer;
    }

    @Nullable
    private AdSize calculateAdSize(int height) {
        if (height >= AdSize.RECTANGLE_HEIGHT_250.getHeight()) {
            return AdSize.RECTANGLE_HEIGHT_250;
        } else if (height >= AdSize.BANNER_HEIGHT_90.getHeight()) {
            return AdSize.BANNER_HEIGHT_90;
        } else {
            // Default to standard banner size
            return AdSize.BANNER_HEIGHT_50;
        }
    }
}
