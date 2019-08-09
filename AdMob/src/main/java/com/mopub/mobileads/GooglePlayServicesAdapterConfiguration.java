package com.mopub.mobileads;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.mediation.MediationExtrasReceiver;
import com.google.android.gms.ads.mediation.customevent.CustomEvent;
import com.mopub.common.BaseAdapterConfiguration;
import com.mopub.common.OnNetworkInitializationFinishedListener;
import com.mopub.common.Preconditions;
import com.mopub.common.logging.MoPubLog;
import com.mopub.mobileads.admob.BuildConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM_WITH_THROWABLE;

public class GooglePlayServicesAdapterConfiguration extends BaseAdapterConfiguration {

    // Adapter keys
    private static final String ADAPTER_VERSION = BuildConfig.VERSION_NAME;
    private static final String MOPUB_NETWORK_NAME = BuildConfig.NETWORK_NAME;

    // Ad targeting keys
    private static final String EXTRA_APPLICATION_ID_KEY = "appid";
    private static final String CATEGORY_EXCLUSION_KEY = "excludedCategory";
    private static final String CONTENT_URL_KEY = "contentUrl";
    private static final String CUSTOM_TARGETING_KEY = "customTargeting";
    private static final String LOCATION_KEY = "location";
    private static final String NPA_KEY = "npa";
    private static final String PUBLISHER_PROVIDED_ID_KEY = "pubId";
    private static final String TAG_FOR_CHILD_DIRECTED_KEY = "tagForChildDirectedTreatment";
    private static final String TAG_FOR_UNDER_AGE_OF_CONSENT_KEY = "tagForUnderAgeOfConsent";
    private static final String TEST_DEVICES_KEY = "testDevices";

    private static Bundle npaBundle;

    @NonNull
    @Override
    public String getAdapterVersion() {
        return ADAPTER_VERSION;
    }

    @Nullable
    @Override
    public String getBiddingToken(@NonNull Context context) {
        return null;
    }

    @NonNull
    @Override
    public String getMoPubNetworkName() {
        return MOPUB_NETWORK_NAME;
    }

    @NonNull
    @Override
    public String getNetworkSdkVersion() {
        /* com.google.android.gms:play-services-ads (AdMob) does not have an API to get the compiled
        version */
        final String adapterVersion = getAdapterVersion();

        return (!TextUtils.isEmpty(adapterVersion)) ?
                adapterVersion.substring(0, adapterVersion.lastIndexOf('.')) : "";
    }

    @Override
    public void initializeNetwork(@NonNull Context context, @Nullable Map<String, String>
            configuration, @NonNull OnNetworkInitializationFinishedListener listener) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(listener);

        boolean networkInitializationSucceeded = false;

        synchronized (GooglePlayServicesAdapterConfiguration.class) {
            try {
                if (configuration != null && !configuration.isEmpty()) {
                    String appId = configuration.get(EXTRA_APPLICATION_ID_KEY);

                    if (!TextUtils.isEmpty(appId)) {
                        MobileAds.initialize(context, configuration.get(EXTRA_APPLICATION_ID_KEY));
                    }

                    String npaValue = configuration.get(NPA_KEY);

                    if (!TextUtils.isEmpty(npaValue)) {
                        npaBundle = new Bundle();
                        npaBundle.putString(NPA_KEY, npaValue);
                    }
                } else {
                    MobileAds.initialize(context);
                }

                networkInitializationSucceeded = true;
            } catch (Exception e) {
                MoPubLog.log(CUSTOM_WITH_THROWABLE, "Initializing AdMob has encountered " +
                        "an exception.", e);
            }
        }

        if (networkInitializationSucceeded) {
            listener.onNetworkInitializationFinished(GooglePlayServicesAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_INITIALIZATION_SUCCESS);
        } else {
            listener.onNetworkInitializationFinished(GooglePlayServicesAdapterConfiguration.class,
                    MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
    }

    static Object setTargeting(Object builder, Map localExtras) {
        try {
            // Publishers may set the following targeting data by passing a Map<String, Object>
            // to MoPubView.setLocalExtras()
            final String contentUrl = (String) localExtras.get(CONTENT_URL_KEY);
            final String excludedCategory = (String) localExtras.get(CATEGORY_EXCLUSION_KEY);
            final Location location = (Location) localExtras.get(LOCATION_KEY);
            final String testDeviceId = (String) localExtras.get(TEST_DEVICES_KEY);
            final Boolean childDirected = (Boolean) localExtras.get(TAG_FOR_CHILD_DIRECTED_KEY);
            final String publisherProvidedId = (String) localExtras.get(PUBLISHER_PROVIDED_ID_KEY);
            final Boolean underAgeOfConsent = (Boolean) localExtras.get(TAG_FOR_UNDER_AGE_OF_CONSENT_KEY);
            final Map customTargeting = (HashMap) localExtras.get(CUSTOM_TARGETING_KEY);

            /* The data type of this Builder depends on if the current ad unit ID mediates AdMob or Ad Manager.
            However, for targeting, the two Builders share the same method names that can be called via reflection. */
            Class<?> clazz = builder.getClass();
            Method setRequestAgent = clazz.getMethod("setRequestAgent", String.class);
            setRequestAgent.invoke(builder, "MoPub");

            if (!TextUtils.isEmpty(contentUrl)) {
                Method setContentUrl = clazz.getMethod("setContentUrl", String.class);
                setContentUrl.invoke(builder, contentUrl);
            }

            if (!TextUtils.isEmpty(excludedCategory)) {
                if (clazz.getName().equals("com.google.android.gms.ads.doubleclick.PublisherAdRequest$Builder")) {
                    Method addCategoryExclusion = clazz.getMethod("addCategoryExclusion", String.class);
                    addCategoryExclusion.invoke(builder, excludedCategory);
                }
            }

            if (location != null) {
                Method setLocation = clazz.getMethod("setLocation", Location.class);
                setLocation.invoke(builder, location);
            }

            if (!TextUtils.isEmpty(testDeviceId)) {
                Method addTestDevice = clazz.getMethod("addTestDevice", String.class);
                addTestDevice.invoke(builder, testDeviceId);
            }

            if (childDirected != null) {
                Method tagForChildDirectedTreatment = clazz.getMethod("tagForChildDirectedTreatment", boolean.class);
                tagForChildDirectedTreatment.invoke(builder, childDirected);
            }

            if (!TextUtils.isEmpty(publisherProvidedId)) {
                if (clazz.getName().equals("com.google.android.gms.ads.doubleclick.PublisherAdRequest$Builder")) {
                    Method setPublisherProvidedId = clazz.getMethod("setPublisherProvidedId", String.class);
                    setPublisherProvidedId.invoke(builder, publisherProvidedId);
                }
            }

            if (underAgeOfConsent != null) {
                Method setTagForUnderAgeOfConsent = clazz.getMethod("setTagForUnderAgeOfConsent", int.class);

                if (underAgeOfConsent) {
                    setTagForUnderAgeOfConsent.invoke(builder, 1);
                } else {
                    setTagForUnderAgeOfConsent.invoke(builder, 0);
                }
            }

            if (npaBundle != null && !npaBundle.isEmpty()) {
                final Object[] args = new Object[2];
                args[0] = AdMobAdapter.class;
                args[1] = Bundle.class;

                MediationExtrasReceiver mediationExtrasReceiver = new MediationExtrasReceiver() {
                    @Override
                    public int hashCode() {
                        return super.hashCode();
                    }
                }

                Method addNetworkExtrasBundle = clazz.getMethod("addNetworkExtrasBundle",
                        AdMobAdapter.class, Bundle.class);
               // addNetworkExtrasBundle.invoke(builder, AdMobAdapter.class, npaBundle);
            }

//            Method addCustomTargeting = clazz.getMethod("addCustomTargeting", );
//            for (Object key : customTargeting.keySet()) {
//                publisherBuilder.addCustomTargeting(key.toString(), customTargeting.get(key).toString());
//            }
        } catch (Exception e) {
            MoPubLog.log(CUSTOM_WITH_THROWABLE, "Failed to set ad targeting data for AdMob or " +
                    "Ad Manager. Either the PublisherAdRequest.Builder or AdRequest.Builder does not " +
                    "exist in the current app.", e);
        }

        return builder;
    }
}
