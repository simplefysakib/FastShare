package com.fastshare.transfer;

import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;

public class TransferManager {
    public static TransferActivity activeActivity;
    public static String connectedEndpointId;
    
    public static final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (activeActivity != null) {
                activeActivity.handlePayloadReceived(payload);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            if (activeActivity != null) {
                activeActivity.handlePayloadUpdate(update);
            }
        }
    };
}