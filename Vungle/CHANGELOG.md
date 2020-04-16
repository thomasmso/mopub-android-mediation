## Changelog
  *  6.5.3.0
    * This version of the adapters has been certified with Vungle 6.5.3 and MoPub SDK 5.11.1.
    * Code format following [MoPub mediation coding style guideline](https://developers.mopub.com/networks/integrate/mopub-network-mediation-guidelines/).

  *  6.5.2.0
    * This version of the adapters has been certified with Vungle 6.5.2.
    * Add support for Vungle's newly-introduced [banner format](https://support.vungle.com/hc/en-us/articles/360032641251-Early-Access-Get-Started-with-Banner-Ads-Android-or-Amazon-SDK-v-6-5-1).
    * Deprecate `VungleMediationSettings` in the favor of `VungleMediationConfiguration`, through which custom ad configurations can be passed that apply to all ad formats. Refer to [Vungle doc](https://support.vungle.com/hc/en-us/articles/360033932751#interstitial-ads-0-5) for details.

  * 6.4.11.5
    * Log the Vungle placement ID in ad lifecycle events.

  * 6.4.11.4
    * Fire `onInterstitialShown` callback when interstitial records an impression.

  * 6.4.11.3
    * Prioritize reading data from `localExtras` and fall back to `MediationSettings` if necessary for `VungleRewardedVideo.java`

  * 6.4.11.2
    * Add support for AndroidX. This is the minimum version compatible with MoPub 5.9.0.

  * 6.4.11.1
    * Added manual click tracking to `VungleBanner` based of Vungle's definition of a click.
    * Add manual impression tracking for `VungleInterstitial`.

  * 6.4.11.0
    * This version of adapters has been certified with Vungle 6.4.11.
    * Added support for banner ad.

  * 6.3.24.4
    * Allow supported mediated networks and publishers to opt-in to process a user’s personal data based on legitimate interest basis. More details [here](https://developers.mopub.com/docs/publisher/gdpr-guide/#legitimate-interest-support).

  * 6.3.24.3
    * Enable passing config option for Vungle auto rotate setting.

  * 6.3.24.2
    * Vungle Adapter will now be released as an Android Archive (AAR) file that includes manifest file for Vungle.

  * 6.3.24.1
    * **Note**: This version is only compatible with the 5.5.0+ release of the MoPub SDK.
    * Add the `VungleAdapterConfiguration` class to: 
         * pre-initialize the Vungle SDK during MoPub SDK initialization process
         * store adapter and SDK versions for logging purpose
    * Streamline adapter logs via `MoPubLog` to make debugging more efficient. For more details, check the [Android Initialization guide](https://developers.mopub.com/docs/android/initialization/) and [Writing Custom Events guide](https://developers.mopub.com/docs/android/custom-events/).

  * 6.3.24.0
    * This version of the adapters has been certified with Vungle 6.3.24.
    * Check if the passed Placement ID is a valid placement for the given App ID.

  * 6.3.17.1
    * Update the placement ID returned in the `getAdNetworkId` API (used to generate server-side rewarded video callback URL) to be non-null, and avoid potential NullPointerExceptions.

  * 6.3.17.0
    * This version of the adapters has been certified with Vungle 6.13.17.
    * Remove the placement IDS (pids) parameter from Vungle's init call per Vungle's new SDK requirements.
    * Update the implementation of Vungle's `updateConsentStatus()` to now also pass a `consentMessageVersion`.

  * 6.2.5.1
    * Fixed an NPE when passing consent before the Vungle SDK is initialized.

  * 6.2.5.0
    * This version of the adapters has been certified with Vungle 6.2.5.
    * General Data Protection Regulation (GDPR) update to support a way for publishers to determine GDPR applicability and to obtain/manage consent from users in European Economic Area, the United Kingdom, or Switzerland to serve personalize ads. Only applicable when integrated with MoPub version 5.0.0 and above.

  * 5.3.2.0
    * This version of the adapters has been certified with Vungle 5.3.2.

  * Initial Commit
  	* Adapters moved from [mopub-android-sdk](https://github.com/mopub/mopub-android-sdk) to [mopub-android-mediation](https://github.com/mopub/mopub-android-mediation/)