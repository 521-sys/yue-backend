package com.yue.sms;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentSmsSenderTest {
    @Test
    void sendsDomesticLoginTemplateAndReturnsProviderRequestId() throws Exception {
        SmsProperties properties = configuredProperties();
        AtomicReference<SendSmsRequest> captured = new AtomicReference<>();
        TencentSmsClient client = request -> {
            captured.set(request);
            SendStatus status = new SendStatus();
            status.setCode("Ok");
            SendSmsResponse response = new SendSmsResponse();
            response.setSendStatusSet(new SendStatus[]{status});
            response.setRequestId("request-123");
            return response;
        };

        SmsSendReceipt receipt = new TencentSmsSender(properties, client)
                .sendLoginCode("13812345678", "654321", 5);

        assertEquals("request-123", receipt.providerRequestId());
        assertArrayEquals(new String[]{"+8613812345678"}, captured.get().getPhoneNumberSet());
        assertArrayEquals(new String[]{"654321", "5"}, captured.get().getTemplateParamSet());
        assertEquals("1400999999", captured.get().getSmsSdkAppId());
        assertEquals("粤语学习", captured.get().getSignName());
        assertEquals("246810", captured.get().getTemplateId());
    }

    @Test
    void mapsProviderRejectionAsKnownFailureWithoutExposingProviderMessage() {
        TencentSmsClient client = request -> {
            SendStatus status = new SendStatus();
            status.setCode("FailedOperation.SignatureIncorrectOrUnapproved");
            status.setMessage("secret provider diagnostic");
            SendSmsResponse response = new SendSmsResponse();
            response.setSendStatusSet(new SendStatus[]{status});
            response.setRequestId("request-rejected");
            return response;
        };

        SmsDeliveryException error = assertThrows(SmsDeliveryException.class,
                () -> new TencentSmsSender(configuredProperties(), client)
                        .sendLoginCode("13812345678", "654321", 5));

        assertFalse(error.unknownOutcome());
        assertFalse(error.getMessage().contains("secret provider diagnostic"));
    }

    @Test
    void mapsTransportFailureAsUnknownOutcome() {
        TencentSmsClient client = request -> {
            throw new TencentCloudSDKException("transport secret");
        };

        SmsDeliveryException error = assertThrows(SmsDeliveryException.class,
                () -> new TencentSmsSender(configuredProperties(), client)
                        .sendLoginCode("13812345678", "654321", 5));

        assertTrue(error.unknownOutcome());
        assertFalse(error.getMessage().contains("transport secret"));
    }

    private SmsProperties configuredProperties() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setCodePepper("test-pepper-0123456789abcdef0123456789abcdef");
        properties.getTencent().setSecretId("id-secret");
        properties.getTencent().setSecretKey("key-secret");
        properties.getTencent().setSdkAppId("1400999999");
        properties.getTencent().setSignName("粤语学习");
        properties.getTencent().setTemplateId("246810");
        return properties;
    }
}
