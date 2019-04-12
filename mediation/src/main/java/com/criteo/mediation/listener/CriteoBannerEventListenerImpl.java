package com.criteo.mediation.listener;


import android.view.View;
import com.criteo.publisher.mediation.listeners.CriteoBannerAdListener;
import com.criteo.publisher.mediation.utils.CriteoErrorCode;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;

public class CriteoBannerEventListenerImpl implements CriteoBannerAdListener {

    private CustomEventBannerListener customEventBannerListener;

    public CriteoBannerEventListenerImpl(CustomEventBannerListener listener) {
        customEventBannerListener = listener;
    }

    @Override
    public void onAdFetchSucceededForBanner(View view) {
        customEventBannerListener.onAdLoaded(view);
    }

    @Override
    public void onAdFetchFailed(CriteoErrorCode code) {
        switch (code) {
            case ERROR_CODE_INTERNAL_ERROR:
                customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INTERNAL_ERROR);
                break;
            case ERROR_CODE_NETWORK_ERROR:
                customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_NETWORK_ERROR);
                break;
            case ERROR_CODE_INVALID_REQUEST:
                customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_INVALID_REQUEST);
                break;
            case ERROR_CODE_NO_FILL:
                customEventBannerListener.onAdFailedToLoad(AdRequest.ERROR_CODE_NO_FILL);
                break;
        }
    }

    @Override
    public void onAdFullScreen() {

    }

    @Override
    public void onAdClosed() {
        customEventBannerListener.onAdClosed();
    }

}
