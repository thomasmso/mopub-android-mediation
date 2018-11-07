package com.mopub.mobileads;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.mopub.common.logging.MoPubLog;
import com.vungle.warren.AdConfig;

import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

/**
 * A custom event for showing Vungle Interstitial.
 */
public class VungleInterstitial extends CustomEventInterstitial {

    /*
     * APP_ID_KEY is intended for MoPub internal use. Do not modify.
     */
    public static final String APP_ID_KEY = "appId";
    public static final String PLACEMENT_ID_KEY = "pid";
    public static final String ADAPTER_NAME = VungleInterstitial.class.getSimpleName();

    /*
     * These keys can be used with MoPubInterstitial.setLocalExtras()
     * to pass additional parameters to the SDK.
     */
    public static final String SOUND_ENABLED_KEY = "vungleSoundEnabled";
    public static final String FLEX_VIEW_CLOSE_TIME_KEY = "vungleFlexViewCloseTimeInSec";
    public static final String ORDINAL_VIEW_COUNT_KEY = "vungleOrdinalViewCount";

    private static VungleRouter sVungleRouter;
    private final Handler mHandler;
    private CustomEventInterstitialListener mCustomEventInterstitialListener;
    private VungleInterstitialRouterListener mVungleRouterListener;
    private String mAppId;
    private String mPlacementId;
    private AdConfig mAdConfig;
    private boolean mIsPlaying;


    public VungleInterstitial() {
        mHandler = new Handler(Looper.getMainLooper());
        sVungleRouter = VungleRouter.getInstance();
    }

    @Override
    protected void loadInterstitial(Context context,
                                    CustomEventInterstitialListener customEventInterstitialListener,
                                    Map<String, Object> localExtras,
                                    Map<String, String> serverExtras) {
        mCustomEventInterstitialListener = customEventInterstitialListener;
        mIsPlaying = false;

        if (context == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

                    MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                            MoPubErrorCode.NETWORK_NO_FILL);
                }
            });

            return;
        }

        if (!validateIdsInServerExtras(serverExtras)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

                    MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                            MoPubErrorCode.NETWORK_NO_FILL);
                }
            });

            return;
        }

        if (mVungleRouterListener == null) {
            mVungleRouterListener = new VungleInterstitialRouterListener();
        }

        if (!sVungleRouter.isVungleInitialized()) {
            // No longer passing the placement IDs (pids) param per Vungle 6.3.17
            sVungleRouter.initVungle(context, mAppId);
        }

        if (localExtras != null) {
            mAdConfig = new AdConfig();
            Object isSoundEnabled = localExtras.get(SOUND_ENABLED_KEY);
            if (isSoundEnabled instanceof Boolean)
                mAdConfig.setMuted(!(Boolean) isSoundEnabled);
            Object flexViewCloseTimeInSec = localExtras.get(FLEX_VIEW_CLOSE_TIME_KEY);
            if (flexViewCloseTimeInSec instanceof Integer)
                mAdConfig.setFlexViewCloseTime((Integer) flexViewCloseTimeInSec);
            Object ordinalViewCount = localExtras.get(ORDINAL_VIEW_COUNT_KEY);
            if (ordinalViewCount instanceof Integer)
                mAdConfig.setOrdinal((Integer) ordinalViewCount);
        }

        sVungleRouter.loadAdForPlacement(mPlacementId, mVungleRouterListener);
        MoPubLog.log(mPlacementId, LOAD_ATTEMPTED, ADAPTER_NAME);
    }

    @Override
    protected void showInterstitial() {
        MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);

        if (sVungleRouter.isAdPlayableForPlacement(mPlacementId)) {

            sVungleRouter.playAdForPlacement(mPlacementId, mAdConfig);
            mIsPlaying = true;
        } else {
            MoPubLog.log(CUSTOM, "SDK tried to show a Vungle interstitial ad before it " +
                    "finished loading. Please try again.");
            mCustomEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

            MoPubLog.log(SHOW_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    @Override
    protected void onInvalidate() {
        MoPubLog.log(CUSTOM, "onInvalidate is called for Placement ID:" + mPlacementId);
        sVungleRouter.removeRouterListener(mPlacementId);
        mVungleRouterListener = null;
        mAdConfig = null;
    }

    // private functions
    private boolean validateIdsInServerExtras(Map<String, String> serverExtras) {
        boolean isAllDataValid = true;

        if (serverExtras.containsKey(APP_ID_KEY)) {
            mAppId = serverExtras.get(APP_ID_KEY);
            if (mAppId.isEmpty()) {
                MoPubLog.log(CUSTOM, "App ID is empty.");
                isAllDataValid = false;
            }
        } else {
            MoPubLog.log(CUSTOM, "AppID is not in serverExtras.");
            isAllDataValid = false;
        }

        if (serverExtras.containsKey(PLACEMENT_ID_KEY)) {
            mPlacementId = serverExtras.get(PLACEMENT_ID_KEY);
            if (mPlacementId.isEmpty()) {
                MoPubLog.log(CUSTOM, "Placement ID for this Ad Unit is empty.");
                isAllDataValid = false;
            }
        } else {
            MoPubLog.log(CUSTOM, "Placement ID for this Ad Unit is not in serverExtras.");
            isAllDataValid = false;
        }

        return isAllDataValid;
    }

    /*
     * VungleRouterListener
     */
    private class VungleInterstitialRouterListener implements VungleRouterListener {
        @Override
        public void onAdEnd(@NonNull String placementReferenceId, final boolean wasSuccessfulView, final boolean wasCallToActionClicked) {

            if (mPlacementId.equals(placementReferenceId)) {
                MoPubLog.log(CUSTOM, "onAdEnd - Placement ID: " + placementReferenceId +
                        ", wasSuccessfulView: " + wasSuccessfulView + ", wasCallToActionClicked: "
                        + wasCallToActionClicked);

                mIsPlaying = false;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (wasCallToActionClicked) {
                            mCustomEventInterstitialListener.onInterstitialClicked();

                            MoPubLog.log(CLICKED, ADAPTER_NAME);
                        }
                        mCustomEventInterstitialListener.onInterstitialDismissed();
                    }
                });
                sVungleRouter.removeRouterListener(mPlacementId);
            }
        }

        @Override
        public void onAdStart(@NonNull String placementReferenceId) {
            if (mPlacementId.equals(placementReferenceId)) {
                MoPubLog.log(CUSTOM, "onAdStart - Placement ID: " + placementReferenceId);

                mIsPlaying = true;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCustomEventInterstitialListener.onInterstitialShown();

                        MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
                    }
                });
            }
        }

        @Override
        public void onUnableToPlayAd(@NonNull String placementReferenceId, String reason) {
            if (mPlacementId.equals(placementReferenceId)) {
                MoPubLog.log(CUSTOM, "onUnableToPlayAd - Placement ID: " +
                        placementReferenceId + ", reason: " + reason);
                mIsPlaying = false;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCustomEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

                        MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                                MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                                MoPubErrorCode.NETWORK_NO_FILL);
                    }
                });
            }
        }

        @Override
        public void onAdAvailabilityUpdate(@NonNull String placementReferenceId, boolean isAdAvailable) {
            if (mPlacementId.equals(placementReferenceId)) {
                if (!mIsPlaying) {
                    if (isAdAvailable) {
                        MoPubLog.log(CUSTOM, "interstitial ad successfully loaded - " +
                                "Placement ID: " + placementReferenceId);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mCustomEventInterstitialListener.onInterstitialLoaded();
                                MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
                            }
                        });
                    } else {
                        MoPubLog.log(CUSTOM, "interstitial ad is not loaded - Placement ID: "
                                + placementReferenceId);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mCustomEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

                                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                                        MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                                        MoPubErrorCode.NETWORK_NO_FILL);
                            }
                        });
                    }
                }
            }
        }
    }
}