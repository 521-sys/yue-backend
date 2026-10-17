package com.yue.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {
    private boolean enabled;
    private String codePepper = "";
    private int expirySeconds = 300;
    private int cooldownSeconds = 60;
    private int phoneDailyLimit = 10;
    private int ipDailyLimit = 30;
    private int maxFailures = 5;
    private String cleanupCron = "0 15 3 * * *";
    private Set<String> trustedProxyAddresses = new LinkedHashSet<>(Set.of("127.0.0.1", "::1"));
    private Tencent tencent = new Tencent();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCodePepper() { return codePepper; }
    public void setCodePepper(String codePepper) { this.codePepper = codePepper; }
    public int getExpirySeconds() { return expirySeconds; }
    public void setExpirySeconds(int expirySeconds) { this.expirySeconds = expirySeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public int getPhoneDailyLimit() { return phoneDailyLimit; }
    public void setPhoneDailyLimit(int phoneDailyLimit) { this.phoneDailyLimit = phoneDailyLimit; }
    public int getIpDailyLimit() { return ipDailyLimit; }
    public void setIpDailyLimit(int ipDailyLimit) { this.ipDailyLimit = ipDailyLimit; }
    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    public String getCleanupCron() { return cleanupCron; }
    public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
    public Set<String> getTrustedProxyAddresses() { return trustedProxyAddresses; }
    public void setTrustedProxyAddresses(Set<String> trustedProxyAddresses) {
        this.trustedProxyAddresses = new LinkedHashSet<>(trustedProxyAddresses);
    }
    public Tencent getTencent() { return tencent; }
    public void setTencent(Tencent tencent) { this.tencent = tencent; }

    public boolean isReady() {
        return enabled
                && codePepper != null && codePepper.length() >= 32
                && tencent.hasRequiredValues();
    }

    public static class Tencent {
        private String secretId = "";
        private String secretKey = "";
        private String sdkAppId = "";
        private String signName = "";
        private String templateId = "";
        private String region = "ap-guangzhou";

        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getSdkAppId() { return sdkAppId; }
        public void setSdkAppId(String sdkAppId) { this.sdkAppId = sdkAppId; }
        public String getSignName() { return signName; }
        public void setSignName(String signName) { this.signName = signName; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        boolean hasRequiredValues() {
            return notBlank(secretId) && notBlank(secretKey) && notBlank(sdkAppId)
                    && notBlank(signName) && notBlank(templateId) && notBlank(region);
        }

        private boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
