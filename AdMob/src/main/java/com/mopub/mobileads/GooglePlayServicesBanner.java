package com.mopub.mobileads;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.util.Views;

import java.util.Map;

import static com.google.android.gms.ads.AdSize.BANNER;
import static com.google.android.gms.ads.AdSize.FULL_BANNER;
import static com.google.android.gms.ads.AdSize.LEADERBOARD;
import static com.google.android.gms.ads.AdSize.MEDIUM_RECTANGLE;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class GooglePlayServicesBanner extends CustomEventBanner {
    /*
     * These keys are intended for MoPub internal use. Do not modify.
     */
    private static final String ADMOB_ID_PREFIX = "ca-app-pub";

    private static final String AD_UNIT_ID_KEY = "adUnitID";
    private static final String AD_WIDTH_KEY = "adWidth";
    private static final String AD_HEIGHT_KEY = "adHeight";
    private static final String ADAPTER_NAME = GooglePlayServicesBanner.class.getSimpleName();

    private CustomEventBannerListener mBannerListener;
    private AdView mGoogleAdView;
    private PublisherAdView mPublisherAdView;
    private boolean useAdManager = false;

    @Override
    protected void loadBanner(
            final Context context,
            final CustomEventBannerListener customEventBannerListener,
            final Map<String, Object> localExtras,
            final Map<String, String> serverExtras) {
        mBannerListener = customEventBannerListener;

        final int adWidth;
        final int adHeight;

        String adUnitId = "";
        if (extrasAreValid(serverExtras)) {
            adUnitId = serverExtras.get(AD_UNIT_ID_KEY);
            adWidth = Integer.parseInt(serverExtras.get(AD_WIDTH_KEY));
            adHeight = Integer.parseInt(serverExtras.get(AD_HEIGHT_KEY));
        } else {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);

            mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_NO_FILL);
            return;
        }

        // Determine if the Google ad unit ID is an AdMob or an Ad Manager one, so we know which
        // the publisher is mediating in this session and prepare API calls appropriately
        if (!TextUtils.isEmpty(adUnitId)) {
            if (!adUnitId.contains(ADMOB_ID_PREFIX)) {
                useAdManager = true;
            }
        }

        final AdSize adSize = calculateAdSize(adWidth, adHeight);
        if (adSize == null) {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);

            mBannerListener.onBannerFailed(MoPubErrorCode.NETWORK_NO_FILL);
            return;
        }

        if (useAdManager) {
            mPublisherAdView = new PublisherAdView(context);
            mPublisherAdView.setAdListener(new AdListener());
            mPublisherAdView.setAdUnitId(adUnitId);
            mPublisherAdView.setAdSizes(adSize);

            final PublisherAdRequest.Builder publisherBuilder = (PublisherAdRequest.Builder)
                    GooglePlayServicesAdapterConfiguration.setTargeting(new PublisherAdRequest.Builder(), localExtras);

            final PublisherAdRequest publisherAdRequest = publisherBuilder.build();
            mPublisherAdView.loadAd(publisherAdRequest);

            MoPubLog.log(adUnitId, LOAD_ATTEMPTED, ADAPTER_NAME);
        } else {
            mGoogleAdView = new AdView(context);
            mGoogleAdView.setAdListener(new AdViewListener());
            mGoogleAdView.setAdUnitId(adUnitId);
            mGoogleAdView.setAdSize(adSize);

            final AdRequest.Builder builder = (AdRequest.Builder) GooglePlayServicesAdapterConfiguration
                    .setTargeting(new AdRequest.Builder(), localExtras);

            final AdRequest adRequest = builder.build();
            mGoogleAdView.loadAd(adRequest);

            MoPubLog.log(adUnitId, LOAD_ATTEMPTED, ADAPTER_NAME);
        }
    }

    @Override
    protected void onInvalidate() {
        Views.removeFromParent(mGoogleAdView);

        if (useAdManager) {
            if (mGoogleAdView != null) {
                mGoogleAdView.setAdListener(null);
                mGoogleAdView.destroy();
            }
        } else {
            if (mPublisherAdView != null) {
                mPublisherAdView.setAdListener(null);
                mPublisherAdView.destroy();
            }
        }
    }

    private boolean extrasAreValid(Map<String, String> serverExtras) {
        try {
            Integer.parseInt(serverExtras.get(AD_WIDTH_KEY));
            Integer.parseInt(serverExtras.get(AD_HEIGHT_KEY));
        } catch (NumberFormatException e) {
            return false;
        }

        return serverExtras.containsKey(AD_UNIT_ID_KEY);
    }

    private AdSize calculateAdSize(int width, int height) {
        // Use the smallest AdSize that will properly contain the adView
        if (width <= BANNER.getWidth() && height <= BANNER.getHeight()) {
            return BANNER;
        } else if (width <= MEDIUM_RECTANGLE.getWidth() && height <= MEDIUM_RECTANGLE.getHeight()) {
            return MEDIUM_RECTANGLE;
        } else if (width <= FULL_BANNER.getWidth() && height <= FULL_BANNER.getHeight()) {
            return FULL_BANNER;
        } else if (width <= LEADERBOARD.getWidth() && height <= LEADERBOARD.getHeight()) {
            return LEADERBOARD;
        } else {
            return null;
        }
    }

    private class AdViewListener extends AdListener {
        /*
         * Google Play Services AdListener implementation
         */

        @Override
        public void onAdClosed() {

        }

        @Override
        public void onAdFailedToLoad(int errorCode) {
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    getMoPubErrorCode(errorCode).getIntCode(),
                    getMoPubErrorCode(errorCode));

            if (mBannerListener != null) {
                mBannerListener.onBannerFailed(getMoPubErrorCode(errorCode));
            }
        }

        @Override
        public void onAdLeftApplication() {
        }

        @Override
        public void onAdLoaded() {
            MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
            MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);
            MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);

            if (mBannerListener != null) {
                mBannerListener.onBannerLoaded(mGoogleAdView);
            }
        }

        @Override
        public void onAdOpened() {
            MoPubLog.log(CLICKED, ADAPTER_NAME);

            if (mBannerListener != null) {
                mBannerListener.onBannerClicked();
            }
        }

        /**
         * Converts a given Google Mobile Ads SDK error code into {@link MoPubErrorCode}.
         *
         * @param error Google Mobile Ads SDK error code.
         * @return an equivalent MoPub SDK error code for the given Google Mobile Ads SDK error
         * code.
         */
        private MoPubErrorCode getMoPubErrorCode(int error) {
            MoPubErrorCode errorCode;
            switch (error) {
                case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    errorCode = MoPubErrorCode.INTERNAL_ERROR;
                    break;
                case AdRequest.ERROR_CODE_INVALID_REQUEST:
                    errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR;
                    break;
                case AdRequest.ERROR_CODE_NETWORK_ERROR:
                    errorCode = MoPubErrorCode.NO_CONNECTION;
                    break;
                case AdRequest.ERROR_CODE_NO_FILL:
                    errorCode = MoPubErrorCode.NO_FILL;
                    break;
                default:
                    errorCode = MoPubErrorCode.UNSPECIFIED;
            }
            return errorCode;
        }
    }
}
