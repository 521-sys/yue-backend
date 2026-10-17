package com.yue.sms;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;

@FunctionalInterface
interface TencentSmsClient {
    SendSmsResponse send(SendSmsRequest request) throws TencentCloudSDKException;
}
