package com.mopub.mobileads;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Keep;

import android.text.TextUtils;

import com.mopub.common.DataKeys;
import com.mopub.common.LifecycleListener;
import com.mopub.common.MediationSettings;
import com.mopub.common.MoPubReward;
import com.mopub.common.logging.MoPubLog;

import com.vungle.warren.AdConfig;

import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOULD_REWARD;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

/**
 * A custom event for showing Vungle rewarded videos.
 */

@Keep
public class VungleRewardedVideo extends CustomEventRewardedVideo {

    /*
     * These constants are intended for MoPub internal use. Do not modify.
     */
    private static final String APP_ID_KEY = "appId";
    private static final String PLACEMENT_ID_KEY = "pid";

    private static final String VUNGLE_NETWORK_ID_DEFAULT = "vngl_id";
    private static final String VUNGLE_DEFAULT_APP_ID = "YOUR_APP_ID_HERE";

    private static final String ADAPTER_NAME = VungleRewardedVideo.class.getSimpleName();
    public static final String USER_ID_KEY = "userId";
    public static final String TITLE_KEY = "title";
    public static final String BODY_KEY = "body";
    public static final String CLOSE_BUTTON_TEXT_KEY = "closeButtonText";
    public static final String KEEP_WATCHING_BUTTON_TEXT_KEY = "keepWatchingButtonText";
    public static final String IS_SOUND_ENABLED_KEY = "isSoundEnabled";
    public static final String FLEX_VIEW_CLOSE_TIME_KEY = "flexViewCloseTimeInSec";
    public static final String ORDINAL_VIEW_COUNT_KEY = "ordinalViewCount";
    public static final String AUTO_ROTATE_ENABLED_KEY = "autoRotateEnabled";

    private static VungleRouter sVungleRouter;
    private VungleRewardedRouterListener mVungleRewardedRouterListener;
    @NonNull
    private VungleAdapterConfiguration mVungleAdapterConfiguration;
    private static boolean sInitialized;
    private String mAppId;
    @NonNull
    private String mPlacementId = VUNGLE_NETWORK_ID_DEFAULT;
    private boolean mIsPlaying;

    private String mAdUnitId;
    private String mCustomerId;
    @Nullable
    private Map<String, Object> localExtrasData;
    private String mUserId;
    private String mTitle;
    private String mBody;
    private String mCloseButtonText;
    private String mKeepWatchingButtonText;
    private boolean mIsSoundEnabled = true;
    private int mFlexViewCloseTimeInSec = 0;
    private int mOrdinalViewCount = 0;
    private boolean mAutoRotateEnabled = false;

    public VungleRewardedVideo() {
        sVungleRouter = VungleRouter.getInstance();

        if (mVungleRewardedRouterListener == null) {
            mVungleRewardedRouterListener = new VungleRewardedRouterListener();
        }
        mVungleAdapterConfiguration = new VungleAdapterConfiguration();
    }

    @Nullable
    @Override
    public LifecycleListener getLifecycleListener() {
        return sVungleRouter.getLifecycleListener();
    }

    @NonNull
    @Override
    protected String getAdNetworkId() {
        return mPlacementId;
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull final Activity launcherActivity,
                                            @NonNull final Map<String, Object> localExtras,
                                            @NonNull final Map<String, String> serverExtras) throws Exception {
        synchronized (VungleRewardedVideo.class) {
            if (sInitialized) {
                return false;
            }

            if (!validateIdsInServerExtras(serverExtras)) {
                mAppId = VUNGLE_DEFAULT_APP_ID;
            }

            if (!sVungleRouter.isVungleInitialized()) {
                // No longer passing the placement IDs (pids) param per Vungle 6.3.17
                sVungleRouter.initVungle(launcherActivity, mAppId);
                mVungleAdapterConfiguration.setCachedInitializationParameters(launcherActivity, serverExtras);
            }

            sInitialized = true;

            return true;
        }
    }

    @Override
    protected void loadWithSdkInitialized(@NonNull final Activity activity, @NonNull final Map<String, Object> localExtras, @NonNull final Map<String, String> serverExtras) throws Exception {
        mIsPlaying = false;

        if (!validateIdsInServerExtras(serverExtras)) {
            MoPubRewardedVideoManager.onRewardedVideoLoadFailure(VungleRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
            return;
        }
        // save localExtras here
        localExtrasData = localExtras;

        Object adUnitObject = localExtras.get(DataKeys.AD_UNIT_ID_KEY);
        if (adUnitObject instanceof String) {
            mAdUnitId = (String) adUnitObject;
        }

        Object customerIdObject = localExtras.get(DataKeys.REWARDED_AD_CUSTOMER_ID_KEY);
        if (customerIdObject instanceof String && !TextUtils.isEmpty((String) customerIdObject)) {
            mCustomerId = (String) customerIdObject;
        }

        if (sVungleRouter.isVungleInitialized()) {
            if (sVungleRouter.isValidPlacement(mPlacementId)) {
                sVungleRouter.loadAdForPlacement(mPlacementId, mVungleRewardedRouterListener);
            } else {
                MoPubLog.log(CUSTOM, "Invalid or Inactive Placement ID: " + mPlacementId);
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Invalid or Inactive Placement ID: " + mPlacementId);
                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(VungleRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                        MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                        MoPubErrorCode.NETWORK_NO_FILL);
            }
            MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
        } else {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "There should not be this case. loadWithSdkInitialized is " +
                    "called before the SDK starts initialization for Placement ID: " + mPlacementId);
            MoPubRewardedVideoManager.onRewardedVideoLoadFailure(VungleRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    @Override
    protected boolean hasVideoAvailable() {
        return sVungleRouter.isAdPlayableForPlacement(mPlacementId);
    }

    @Override
    protected void showVideo() {
        MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);

        final AdConfig adConfig = new AdConfig();
        setUpMediationSettingsForRequest(adConfig);

        sVungleRouter.playAdForPlacement(mPlacementId, adConfig);
        mIsPlaying = true;
    }

    @Override
    protected void onInvalidate() {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "onInvalidate is called for Placement ID:" + mPlacementId);

        sVungleRouter.removeRouterListener(mPlacementId);
        mVungleRewardedRouterListener = null;
    }

    //private functions
    private boolean validateIdsInServerExtras(Map<String, String> serverExtras) {
        boolean isAllDataValid = true;

        if (serverExtras.containsKey(APP_ID_KEY)) {
            mAppId = serverExtras.get(APP_ID_KEY);
            if (mAppId != null && mAppId.isEmpty()) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "App ID is empty.");
                isAllDataValid = false;
            }
        } else {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "AppID is not in serverExtras.");
            isAllDataValid = false;
        }

        if (serverExtras.containsKey(PLACEMENT_ID_KEY)) {
            mPlacementId = serverExtras.get(PLACEMENT_ID_KEY);
            if (mPlacementId != null && mPlacementId.isEmpty()) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Placement ID for this Ad Unit is empty.");
                isAllDataValid = false;
            }
        } else {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Placement ID for this Ad Unit is not in serverExtras.");
            isAllDataValid = false;
        }

        return isAllDataValid;
    }

    private void setWithMediationSettings(@NonNull VungleMediationSettings mediationSettings) {

        mUserId = mediationSettings.userId;
        mTitle = mediationSettings.title;
        mBody = mediationSettings.body;
        mCloseButtonText = mediationSettings.closeButtonText;
        mKeepWatchingButtonText = mediationSettings.keepWatchingButtonText;
        mIsSoundEnabled = mediationSettings.isSoundEnabled;
        mFlexViewCloseTimeInSec = mediationSettings.flexViewCloseTimeInSec;
        mOrdinalViewCount = mediationSettings.ordinalViewCount;
        mAutoRotateEnabled = mediationSettings.autoRotateEnabled;
    }

    private void setUpMediationSettingsForRequest(AdConfig adConfig) {

        final VungleMediationSettings globalMediationSettings =
                MoPubRewardedVideoManager.getGlobalMediationSettings(VungleMediationSettings.class);
        final VungleMediationSettings instanceMediationSettings =
                MoPubRewardedVideoManager.getInstanceMediationSettings(VungleMediationSettings.class, mAdUnitId);

        // Local options override global options.
        // local extras that are set will override fields from mediation settings
        if (instanceMediationSettings != null) {
            setWithMediationSettings(instanceMediationSettings);
        } else if (globalMediationSettings != null) {
            setWithMediationSettings(globalMediationSettings);
        }

        if (localExtrasData != null) {
            if (!TextUtils.isEmpty(mCustomerId)) {
                mUserId = mCustomerId;
            }

            final Object titleObject = localExtrasData.get(TITLE_KEY);
            if (titleObject instanceof String) {
                mTitle = (String) titleObject;
            }

            final Object bodyObject = localExtrasData.get(BODY_KEY);
            if (bodyObject instanceof String) {
                mBody = (String) bodyObject;
            }

            final Object closeButtonTextObject = localExtrasData.get(CLOSE_BUTTON_TEXT_KEY);
            if (closeButtonTextObject instanceof String) {
                mCloseButtonText = (String) closeButtonTextObject;
            }

            final Object keepWatchingButtonTextObject = localExtrasData.get(KEEP_WATCHING_BUTTON_TEXT_KEY);
            if (keepWatchingButtonTextObject instanceof String) {
                mKeepWatchingButtonText = (String) keepWatchingButtonTextObject;
            }

            final Object isSoundEnabledObject = localExtrasData.get(IS_SOUND_ENABLED_KEY);
            if (isSoundEnabledObject instanceof Boolean) {
                mIsSoundEnabled = (Boolean) isSoundEnabledObject;
            }

            final Object flexViewCloseTimeInSecObject = localExtrasData.get(FLEX_VIEW_CLOSE_TIME_KEY);
            if (flexViewCloseTimeInSecObject instanceof Integer) {
                mFlexViewCloseTimeInSec = (Integer) flexViewCloseTimeInSecObject;
            }

            final Object ordinalViewCountObject = localExtrasData.get(ORDINAL_VIEW_COUNT_KEY);
            if (ordinalViewCountObject instanceof Integer) {
                mOrdinalViewCount = (Integer) ordinalViewCountObject;
            }

            final Object autoRotateEnabledObject = localExtrasData.get(AUTO_ROTATE_ENABLED_KEY);
            if (autoRotateEnabledObject instanceof Boolean) {
                mAutoRotateEnabled = (Boolean) autoRotateEnabledObject;
            }
        }
        sVungleRouter.setIncentivizedFields(mUserId, mTitle, mBody,
                mKeepWatchingButtonText, mCloseButtonText);

        adConfig.setMuted(!mIsSoundEnabled);
        adConfig.setFlexViewCloseTime(mFlexViewCloseTimeInSec);
        adConfig.setOrdinal(mOrdinalViewCount);
        adConfig.setAutoRotate(mAutoRotateEnabled);
    }


    /*
     * VungleRewardedRouterListener
     */
    private class VungleRewardedRouterListener implements VungleRouterListener {
        @Override
        public void onAdEnd(@NonNull String placementReferenceId, final boolean wasSuccessfulView, final boolean wasCallToActionClicked) {
            if (mPlacementId.equals(placementReferenceId)) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "onAdEnd - Placement ID: " + placementReferenceId +
                        ", wasSuccessfulView: " + wasSuccessfulView + ", wasCallToActionClicked: "
                        + wasCallToActionClicked);

                mIsPlaying = false;

                if (wasSuccessfulView) {
                    MoPubLog.log(SHOULD_REWARD, ADAPTER_NAME, MoPubReward.NO_REWARD_AMOUNT, MoPubReward.NO_REWARD_LABEL);

                    // Vungle does not provide a callback when a user should be rewarded.
                    // You will need to provide your own reward logic if you receive a reward with
                    // "NO_REWARD_LABEL" && "NO_REWARD_AMOUNT"
                    MoPubRewardedVideoManager.onRewardedVideoCompleted(VungleRewardedVideo.class,
                            mPlacementId,
                            MoPubReward.success(MoPubReward.NO_REWARD_LABEL,
                                    MoPubReward.NO_REWARD_AMOUNT));
                }

                if (wasCallToActionClicked) {
                    MoPubRewardedVideoManager.onRewardedVideoClicked(VungleRewardedVideo.class,
                            mPlacementId);

                    MoPubLog.log(CLICKED, ADAPTER_NAME);
                }

                MoPubRewardedVideoManager.onRewardedVideoClosed(VungleRewardedVideo.class,
                        mPlacementId);

                sVungleRouter.removeRouterListener(mPlacementId);
            }
        }

        @Override
        public void onAdStart(@NonNull String placementReferenceId) {
            if (mPlacementId.equals(placementReferenceId)) {

                MoPubLog.log(CUSTOM, ADAPTER_NAME, "onAdStart - Placement ID: " + placementReferenceId);

                mIsPlaying = true;

                MoPubRewardedVideoManager.onRewardedVideoStarted(VungleRewardedVideo.class,
                        mPlacementId);

                MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
            }
        }

        @Override
        public void onUnableToPlayAd(@NonNull String placementReferenceId, String reason) {
            if (mPlacementId.equals(placementReferenceId)) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "onUnableToPlayAd - Placement ID: " +
                        placementReferenceId + ", reason: " + reason);

                mIsPlaying = false;
                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(VungleRewardedVideo.class,
                        mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

                MoPubLog.log(LOAD_FAILED, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                        MoPubErrorCode.NETWORK_NO_FILL);
            }
        }

        @Override
        public void onAdAvailabilityUpdate(@NonNull String placementReferenceId, boolean isAdAvailable) {
            if (mPlacementId.equals(placementReferenceId)) {
                if (!mIsPlaying) {
                    if (isAdAvailable) {
                        MoPubLog.log(CUSTOM, ADAPTER_NAME, "rewarded video ad successfully loaded - " +
                                "Placement ID: " + placementReferenceId);
                        MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(VungleRewardedVideo.class,
                                mPlacementId);

                        MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
                    } else {
                        MoPubLog.log(CUSTOM, ADAPTER_NAME, "rewarded video ad is not loaded - " +
                                "Placement ID: " + placementReferenceId);
                        MoPubRewardedVideoManager.onRewardedVideoLoadFailure(VungleRewardedVideo.class,
                                mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

                        MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                                MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                                MoPubErrorCode.NETWORK_NO_FILL);
                    }
                }
            }
        }
    }

    public static class VungleMediationSettings implements MediationSettings {
        @Nullable
        private final String userId;
        @Nullable
        private final String title;
        @Nullable
        private final String body;
        @Nullable
        private final String closeButtonText;
        @Nullable
        private final String keepWatchingButtonText;
        private final boolean isSoundEnabled;
        private final int flexViewCloseTimeInSec;
        private final int ordinalViewCount;
        private final boolean autoRotateEnabled;

        public static class Builder {
            @Nullable
            private String userId;
            @Nullable
            private String title;
            @Nullable
            private String body;
            @Nullable
            private String closeButtonText;
            @Nullable
            private String keepWatchingButtonText;
            private boolean isSoundEnabled = true;
            private int flexViewCloseTimeInSec = 0;
            private int ordinalViewCount = 0;
            private boolean autoRotateEnabled = false;

            public Builder withUserId(@NonNull final String userId) {
                this.userId = userId;
                return this;
            }

            public Builder withCancelDialogTitle(@NonNull final String title) {
                this.title = title;
                return this;
            }

            public Builder withCancelDialogBody(@NonNull final String body) {
                this.body = body;
                return this;
            }

            public Builder withCancelDialogCloseButton(@NonNull final String buttonText) {
                this.closeButtonText = buttonText;
                return this;
            }

            public Builder withCancelDialogKeepWatchingButton(@NonNull final String buttonText) {
                this.keepWatchingButtonText = buttonText;
                return this;
            }

            public Builder withSoundEnabled(boolean isSoundEnabled) {
                this.isSoundEnabled = isSoundEnabled;
                return this;
            }

            public Builder withFlexViewCloseTimeInSec(int flexViewCloseTimeInSec) {
                this.flexViewCloseTimeInSec = flexViewCloseTimeInSec;
                return this;
            }

            public Builder withOrdinalViewCount(int ordinalViewCount) {
                this.ordinalViewCount = ordinalViewCount;
                return this;
            }

            public Builder withAutoRotate(boolean autoRotateEnabled) {
                this.autoRotateEnabled = autoRotateEnabled;
                return this;
            }

            public VungleMediationSettings build() {
                return new VungleMediationSettings(this);
            }
        }

        private VungleMediationSettings(@NonNull final Builder builder) {
            this.userId = builder.userId;
            this.title = builder.title;
            this.body = builder.body;
            this.closeButtonText = builder.closeButtonText;
            this.keepWatchingButtonText = builder.keepWatchingButtonText;
            this.isSoundEnabled = builder.isSoundEnabled;
            this.flexViewCloseTimeInSec = builder.flexViewCloseTimeInSec;
            this.ordinalViewCount = builder.ordinalViewCount;
            this.autoRotateEnabled = builder.autoRotateEnabled;
        }
    }
}