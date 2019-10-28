package com.mopub.mobileads;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.BaseLifecycleListener;
import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPubReward;
import com.mopub.common.logging.MoPubLog;
import com.unity3d.ads.mediation.IUnityAdsExtendedListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.metadata.MediationMetaData;

import java.util.Map;
import java.util.UUID;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOULD_REWARD;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class UnityRewardedVideo extends CustomEventRewardedVideo implements IUnityAdsExtendedListener {
    private static final LifecycleListener sLifecycleListener = new UnityLifecycleListener();
    private static final String ADAPTER_NAME = UnityRewardedVideo.class.getSimpleName();

    @NonNull
    private String mPlacementId = "";

    @NonNull
    private UnityAdsAdapterConfiguration mUnityAdsAdapterConfiguration;

    @Nullable
    private Activity mLauncherActivity;

    private int impressionOrdinal;
    private int missedImpressionOrdinal;

    @Override
    @NonNull
    public LifecycleListener getLifecycleListener() {
        return sLifecycleListener;
    }

    @Override
    @NonNull
    public String getAdNetworkId() {
        return mPlacementId;
    }

    public UnityRewardedVideo() {
        mUnityAdsAdapterConfiguration = new UnityAdsAdapterConfiguration();
    }

    @Override
    public boolean checkAndInitializeSdk(@NonNull final Activity launcherActivity,
                                         @NonNull final Map<String, Object> localExtras,
                                         @NonNull final Map<String, String> serverExtras) throws Exception {
        synchronized (UnityRewardedVideo.class) {
            mPlacementId = UnityRouter.placementIdForServerExtras(serverExtras, mPlacementId);
            if (UnityAds.isInitialized()) {
                return false;
            }

            mUnityAdsAdapterConfiguration.setCachedInitializationParameters(launcherActivity, serverExtras);

            UnityRouter.getInterstitialRouter().setCurrentPlacementId(mPlacementId);
            if (UnityRouter.initUnityAds(serverExtras, launcherActivity)) {
                UnityRouter.getInterstitialRouter().addListener(mPlacementId, this);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    protected void loadWithSdkInitialized(@NonNull Activity activity,
                                          @NonNull Map<String, Object> localExtras,
                                          @NonNull Map<String, String> serverExtras) throws Exception {

        mPlacementId = UnityRouter.placementIdForServerExtras(serverExtras, mPlacementId);
        mLauncherActivity = activity;

        UnityRouter.getInterstitialRouter().addListener(mPlacementId, this);

        UnityAds.load(mPlacementId);

        if (hasVideoAvailable()) {
            MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(UnityRewardedVideo.class, mPlacementId);

            MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
        } else if (UnityAds.getPlacementState(mPlacementId) == UnityAds.PlacementState.NO_FILL) {
            MoPubRewardedVideoManager.onRewardedVideoLoadFailure(UnityRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);
            UnityRouter.getInterstitialRouter().removeListener(mPlacementId);
            MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    @Override
    public boolean hasVideoAvailable() {
        return UnityAds.isReady(mPlacementId);
    }

    @Override
    public void showVideo() {
        MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);

        if (UnityAds.isReady(mPlacementId) && mLauncherActivity != null) {
            MediationMetaData metadata = new MediationMetaData(mLauncherActivity);
            metadata.setOrdinal(++impressionOrdinal);
            metadata.commit();

            UnityAds.show((Activity) mLauncherActivity, mPlacementId);
        } else {
            // lets Unity Ads know when ads fail to show
            MediationMetaData metadata = new MediationMetaData(mLauncherActivity);
            metadata.setMissedImpressionOrdinal(++missedImpressionOrdinal);
            metadata.commit();
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Attempted to show Unity rewarded video before it was " +
                    "available.");

            MoPubRewardedVideoManager.onRewardedVideoPlaybackError(UnityRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);
            MoPubLog.log(SHOW_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    @Override
    protected void onInvalidate() {
        UnityRouter.getInterstitialRouter().removeListener(mPlacementId);
    }

    @Override
    public void onUnityAdsClick(String placementId) {
        MoPubRewardedVideoManager.onRewardedVideoClicked(UnityRewardedVideo.class, placementId);

        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video clicked for placement " +
                placementId + ".");

        MoPubLog.log(CLICKED, ADAPTER_NAME);

    }

    @Override
    public void onUnityAdsPlacementStateChanged(String placementId, UnityAds.PlacementState oldState, UnityAds.PlacementState newState) {
        if (placementId.equals(mPlacementId)) {
            if (newState == UnityAds.PlacementState.NO_FILL) {
                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(UnityRewardedVideo.class, mPlacementId, MoPubErrorCode.NETWORK_NO_FILL);

                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                        MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                        MoPubErrorCode.NETWORK_NO_FILL);

                UnityRouter.getInterstitialRouter().removeListener(mPlacementId);
            }
        }

    }

    @Override
    public void onUnityAdsReady(String placementId) {
        if (placementId.equals(mPlacementId)) {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video cached for placement " +
                    placementId + ".");
            MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(UnityRewardedVideo.class, placementId);

            MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
        }

    }

    @Override
    public void onUnityAdsStart(String placementId) {
        MoPubRewardedVideoManager.onRewardedVideoStarted(UnityRewardedVideo.class, mPlacementId);
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video started for placement " +
                mPlacementId + ".");

        MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
    }

    @Override
    public void onUnityAdsFinish(String placementId, UnityAds.FinishState finishState) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity Ad finished with finish state = " + finishState);

        if (finishState == UnityAds.FinishState.ERROR) {
            MoPubRewardedVideoManager.onRewardedVideoPlaybackError(
                    UnityRewardedVideo.class,
                    mPlacementId,
                    MoPubErrorCode.NETWORK_NO_FILL);

            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video encountered a playback error for " +
                    "placement " + placementId);

            MoPubLog.log(SHOW_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);
        } else if (finishState == UnityAds.FinishState.COMPLETED) {
            MoPubLog.log(SHOULD_REWARD, ADAPTER_NAME, MoPubReward.NO_REWARD_AMOUNT, MoPubReward.NO_REWARD_LABEL);

            MoPubRewardedVideoManager.onRewardedVideoCompleted(
                    UnityRewardedVideo.class,
                    mPlacementId,
                    MoPubReward.success(MoPubReward.NO_REWARD_LABEL, MoPubReward.NO_REWARD_AMOUNT));

            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video completed for placement " +
                    placementId);
        } else if (finishState == UnityAds.FinishState.SKIPPED) {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity ad was skipped, no reward will be given.");
        }
        MoPubRewardedVideoManager.onRewardedVideoClosed(UnityRewardedVideo.class, mPlacementId);
        UnityRouter.getInterstitialRouter().removeListener(placementId);
    }

    @Override
    public void onUnityAdsError(UnityAds.UnityAdsError unityAdsError, String message) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unity rewarded video cache failed for placement " +
                mPlacementId + ".");
        MoPubErrorCode errorCode = UnityRouter.UnityAdsUtils.getMoPubErrorCode(unityAdsError);
        MoPubRewardedVideoManager.onRewardedVideoLoadFailure(UnityRewardedVideo.class, mPlacementId, errorCode);

        MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                errorCode.getIntCode(),
                errorCode);

    }

    private static final class UnityLifecycleListener extends BaseLifecycleListener {
        @Override
        public void onCreate(@NonNull final Activity activity) {
            super.onCreate(activity);
        }

        @Override
        public void onResume(@NonNull final Activity activity) {
            super.onResume(activity);
        }
    }
}