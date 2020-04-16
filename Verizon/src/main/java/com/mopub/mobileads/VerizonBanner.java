package com.mopub.mobileads;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import androidx.annotation.NonNull;

import com.mopub.common.MoPub;
import com.mopub.common.Preconditions;
import com.mopub.common.logging.MoPubLog;
import com.verizon.ads.ActivityStateManager;
import com.verizon.ads.Bid;
import com.verizon.ads.BidRequestListener;
import com.verizon.ads.CreativeInfo;
import com.verizon.ads.ErrorInfo;
import com.verizon.ads.RequestMetadata;
import com.verizon.ads.VASAds;
import com.verizon.ads.edition.StandardEdition;
import com.verizon.ads.inlineplacement.AdSize;
import com.verizon.ads.inlineplacement.InlineAdFactory;
import com.verizon.ads.inlineplacement.InlineAdView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM_WITH_THROWABLE;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.WILL_LEAVE_APPLICATION;
import static com.mopub.mobileads.MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR;
import static com.mopub.mobileads.MoPubErrorCode.INTERNAL_ERROR;
import static com.mopub.mobileads.VerizonAdapterConfiguration.convertErrorInfoToMoPub;

public class VerizonBanner extends CustomEventBanner {

    private static final String ADAPTER_NAME = VerizonBanner.class.getSimpleName();

    private static final String PLACEMENT_ID_KEY = "placementId";
    private static final String SITE_ID_KEY = "siteId";
    private static final String HEIGHT_KEY = "com_mopub_ad_height";
    private static final String WIDTH_KEY = "com_mopub_ad_width";
    private static final String HEIGHT_LEGACY_KEY = "adHeight";
    private static final String WIDTH_LEGACY_KEY = "adWidth";

    private InlineAdView verizonInlineAd;
    private CustomEventBannerListener bannerListener;
    private FrameLayout internalView;

    private int adWidth, adHeight;
    private static String mPlacementId;

    @NonNull
    private VerizonAdapterConfiguration verizonAdapterConfiguration;

    public VerizonBanner() {
        verizonAdapterConfiguration = new VerizonAdapterConfiguration();
    }

    @Override
    protected void loadBanner(final Context context,
                              final CustomEventBannerListener customEventBannerListener,
                              final Map<String, Object> localExtras,
                              final Map<String, String> serverExtras) {

        bannerListener = customEventBannerListener;

        if (serverExtras == null || serverExtras.isEmpty()) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Ad request to Verizon " +
                    "failed because serverExtras is null or empty");

            logAndNotifyBannerFailed(LOAD_FAILED, ADAPTER_CONFIGURATION_ERROR);

            return;
        }

        // Cache serverExtras so siteId can be used to initalizate VAS early at next launch
        verizonAdapterConfiguration.setCachedInitializationParameters(context, serverExtras);

        String siteId = serverExtras.get(getSiteIdKey());
        mPlacementId = serverExtras.get(getPlacementIdKey());

        if (!VASAds.isInitialized()) {
            Application application = null;

            if (context instanceof Application) {
                application = (Application) context;
            } else if (context instanceof Activity) {
                application = ((Activity) context).getApplication();
            }

            if (!StandardEdition.initialize(application, siteId)) {

                logAndNotifyBannerFailed(LOAD_FAILED, ADAPTER_CONFIGURATION_ERROR);
            }
        }

        // The current activity must be set as resumed so VAS can track ad visibility
        ActivityStateManager activityStateManager = VASAds.getActivityStateManager();
        if (activityStateManager != null && context instanceof Activity) {
            activityStateManager.setState((Activity) context, ActivityStateManager.ActivityState.RESUMED);
        }

        if (localExtras == null || localExtras.isEmpty()) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "localExtras is null. " +
                    "Unable to extract banner sizes from localExtras.  Will attempt to extract from " +
                    "serverExtras");
        } else {
            if (localExtras.get(getWidthKey()) != null) {
                adWidth = (int) localExtras.get(getWidthKey());
            }
            if (localExtras.get(getHeightKey()) != null) {
                adHeight = (int) localExtras.get(getHeightKey());
            }
        }

        if (adHeight <= 0 || adWidth <= 0) {
            // Fall back to serverExtras for legacy custom event integrations
            final String widthString = serverExtras.get(WIDTH_LEGACY_KEY);
            final String heightString = serverExtras.get(HEIGHT_LEGACY_KEY);

            try {
                if (widthString != null) {
                    adWidth = Integer.parseInt(widthString);
                }
                if (heightString != null) {
                    adHeight = Integer.parseInt(heightString);
                }
            } catch (NumberFormatException e) {
                MoPubLog.log(getAdNetworkId(), CUSTOM_WITH_THROWABLE, "Unable to parse banner " +
                        "sizes from serverExtras.", e);

                logAndNotifyBannerFailed(LOAD_FAILED, ADAPTER_CONFIGURATION_ERROR);

                return;
            }
        }

        if (TextUtils.isEmpty(mPlacementId) || adWidth <= 0 || adHeight <= 0) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME,
                    "Ad request to Verizon failed because either the placement ID is empty, or width " +
                            "and/or height is <= 0");

            logAndNotifyBannerFailed(LOAD_FAILED, INTERNAL_ERROR);

            return;
        }

        internalView = new FrameLayout(context);

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        internalView.setLayoutParams(lp);

        VASAds.setLocationEnabled(MoPub.getLocationAwareness() != MoPub.LocationAwareness.DISABLED);

        final Bid bid = BidCache.get(mPlacementId);
        final InlineAdFactory inlineAdFactory = new InlineAdFactory(context, mPlacementId,
                Collections.singletonList(new AdSize(adWidth, adHeight)),
                new VerizonInlineAdFactoryListener());

        if (bid == null) {
            final RequestMetadata.Builder requestMetadataBuilder = new RequestMetadata.Builder(VASAds.getRequestMetadata());
            requestMetadataBuilder.setMediator(VerizonAdapterConfiguration.MEDIATOR_ID);

            final String adContent = serverExtras.get(VerizonAdapterConfiguration.SERVER_EXTRAS_AD_CONTENT_KEY);

            if (!TextUtils.isEmpty(adContent)) {
                final Map<String, Object> placementData = new HashMap<>();

                placementData.put(VerizonAdapterConfiguration.REQUEST_METADATA_AD_CONTENT_KEY, adContent);
                placementData.put("overrideWaterfallProvider", "waterfallprovider/sideloading");

                requestMetadataBuilder.setPlacementData(placementData);
            }

            inlineAdFactory.setRequestMetaData(requestMetadataBuilder.build());
            inlineAdFactory.load(new VerizonInlineAdListener());
        } else {
            inlineAdFactory.load(bid, new VerizonInlineAdListener());
        }
    }

    /**
     * Call this method to cache a super auction bid for the specified placement ID
     *
     * @param context            a non-null Context
     * @param placementId        a valid placement ID. Cannot be null or empty.
     * @param adSizes            a list of acceptable {@link AdSize}s. Cannot be null or empty.
     * @param requestMetadata    a {@link RequestMetadata} instance for the request or null
     * @param bidRequestListener an instance of {@link BidRequestListener}. Cannot be null.
     */
    public static void requestBid(final Context context, final String placementId,
                                  final List<AdSize> adSizes,
                                  final RequestMetadata requestMetadata,
                                  final BidRequestListener bidRequestListener) {

        Preconditions.checkNotNull(context, "Super auction bid skipped because the " +
                "context is null");
        Preconditions.checkNotNull(placementId, "Super auction bid skipped because the " +
                "placement ID is null");
        Preconditions.checkNotNull(adSizes, "Super auction bid skipped because the " +
                "adSizes list is null");
        Preconditions.checkNotNull(bidRequestListener, "Super auction bid skipped " +
                "because the bidRequestListener is null");

        if (TextUtils.isEmpty(placementId)) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Super auction bid skipped " +
                    "because the placement ID is empty");

            return;
        }

        if (adSizes.isEmpty()) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Super auction bid skipped " +
                    "because the adSizes list is empty");

            return;
        }

        final RequestMetadata.Builder builder = new RequestMetadata.Builder(requestMetadata);
        final RequestMetadata actualRequestMetadata = builder
                .setMediator(VerizonAdapterConfiguration.MEDIATOR_ID)
                .build();

        InlineAdFactory.requestBid(context, placementId, adSizes, actualRequestMetadata,
                new BidRequestListener() {

                    @Override
                    public void onComplete(Bid bid, ErrorInfo errorInfo) {

                        if (errorInfo == null) {
                            BidCache.put(placementId, bid);
                        }

                        bidRequestListener.onComplete(bid, errorInfo);
                    }
                });
    }

    @Override
    protected void onInvalidate() {
        VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

            @Override
            public void run() {
                bannerListener = null;

                // Destroy any hanging references
                if (verizonInlineAd != null) {
                    verizonInlineAd.destroy();
                    verizonInlineAd = null;
                }
            }
        });
    }

    protected String getPlacementIdKey() {
        return PLACEMENT_ID_KEY;
    }

    protected String getSiteIdKey() {
        return SITE_ID_KEY;
    }

    protected String getWidthKey() {
        return WIDTH_KEY;
    }

    protected String getHeightKey() {
        return HEIGHT_KEY;
    }

    private void logAndNotifyBannerFailed(final MoPubLog.AdapterLogEvent event,
                                          final MoPubErrorCode errorCode) {

        MoPubLog.log(getAdNetworkId(), event, ADAPTER_NAME, errorCode.getIntCode(), errorCode);

        if (bannerListener != null) {
            bannerListener.onBannerFailed(errorCode);
        }
    }

    private static String getAdNetworkId() {
        return mPlacementId;
    }

    private class VerizonInlineAdFactoryListener implements InlineAdFactory.InlineAdFactoryListener {
        final CustomEventBannerListener listener = bannerListener;

        @Override
        public void onLoaded(final InlineAdFactory inlineAdFactory, final InlineAdView inlineAdView) {
            MoPubLog.log(getAdNetworkId(), LOAD_SUCCESS, ADAPTER_NAME);

            verizonInlineAd = inlineAdView;

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    final CreativeInfo creativeInfo = verizonInlineAd == null ? null : verizonInlineAd.getCreativeInfo();
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Verizon creative " +
                            "info: " + creativeInfo);

                    if (internalView != null && verizonInlineAd != null) {
                        internalView.addView(verizonInlineAd);
                    }

                    if (listener != null) {
                        listener.onBannerLoaded(internalView);
                    }
                }
            });
        }

        @Override
        public void onCacheLoaded(final InlineAdFactory inlineAdFactory, final int numRequested,
                                  final int numReceived) {
        }

        @Override
        public void onCacheUpdated(final InlineAdFactory inlineAdFactory, final int cacheSize) {
        }

        @Override
        public void onError(final InlineAdFactory inlineAdFactory, final ErrorInfo errorInfo) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Unable to load Verizon " +
                    "banner due to error: " + errorInfo.toString());

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    logAndNotifyBannerFailed(LOAD_FAILED, convertErrorInfoToMoPub(errorInfo));
                }
            });
        }
    }

    private class VerizonInlineAdListener implements InlineAdView.InlineAdListener {
        final CustomEventBannerListener listener = bannerListener;

        @Override
        public void onError(final InlineAdView inlineAdView, final ErrorInfo errorInfo) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Unable to show Verizon " +
                    "banner due to error: " + errorInfo.toString());

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    logAndNotifyBannerFailed(SHOW_FAILED, convertErrorInfoToMoPub(errorInfo));
                }
            });
        }

        @Override
        public void onResized(final InlineAdView inlineAdView) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Verizon banner resized to: " +
                    inlineAdView.getAdSize().getWidth() + " by " +
                    inlineAdView.getAdSize().getHeight());
        }

        @Override
        public void onExpanded(final InlineAdView inlineAdView) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Verizon banner expanded");

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onBannerExpanded();
                    }
                }
            });
        }

        @Override
        public void onCollapsed(final InlineAdView inlineAdView) {
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Verizon banner collapsed");

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onBannerCollapsed();
                    }
                }
            });
        }

        @Override
        public void onClicked(final InlineAdView inlineAdView) {
            MoPubLog.log(getAdNetworkId(), CLICKED, ADAPTER_NAME);

            VerizonAdapterConfiguration.postOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (listener != null) {
                        listener.onBannerClicked();
                    }
                }
            });
        }

        @Override
        public void onAdLeftApplication(final InlineAdView inlineAdView) {
            // Only logging this event. No need to call bannerListener.onLeaveApplication()
            // because it's an alias for bannerListener.onBannerClicked()
            MoPubLog.log(getAdNetworkId(), WILL_LEAVE_APPLICATION, ADAPTER_NAME);
        }

        @Override
        public void onAdRefreshed(final InlineAdView inlineAdView) {
        }

        @Override
        public void onEvent(final InlineAdView inlineAdView, final String source, final String eventId,
                            final Map<String, Object> arguments) {
        }
    }
}
