package com.yue.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;

public class TencentSmsSender implements SmsSender {
    private final SmsProperties properties;
    private final TencentSmsClient client;

    public TencentSmsSender(SmsProperties properties) {
        this(properties, createClient(properties));
    }

    TencentSmsSender(SmsProperties properties, TencentSmsClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public SmsSendReceipt sendLoginCode(String phone, String code, int validMinutes) {
        SendSmsRequest request = new SendSmsRequest();
        request.setPhoneNumberSet(new String[]{"+86" + phone});
        request.setSmsSdkAppId(properties.getTencent().getSdkAppId());
        request.setSignName(properties.getTencent().getSignName());
        request.setTemplateId(properties.getTencent().getTemplateId());
        request.setTemplateParamSet(new String[]{code, Integer.toString(validMinutes)});

        try {
            SendSmsResponse response = client.send(request);
            SendStatus[] statuses = response == null ? null : response.getSendStatusSet();
            if (statuses == null || statuses.length != 1 || statuses[0] == null) {
                throw new SmsDeliveryException("短信服务返回结果不完整", true);
            }
            if (!"Ok".equals(statuses[0].getCode())) {
                throw new SmsDeliveryException("短信发送被服务商拒绝", false);
            }
            return new SmsSendReceipt(response.getRequestId());
        } catch (TencentCloudSDKException e) {
            throw new SmsDeliveryException("短信服务请求结果未知", true, e);
        }
    }

    private static TencentSmsClient createClient(SmsProperties properties) {
        var tencent = properties.getTencent();
        Credential credential = new Credential(tencent.getSecretId(), tencent.getSecretKey());
        SmsClient smsClient = new SmsClient(credential, tencent.getRegion());
        return smsClient::SendSms;
    }
}
