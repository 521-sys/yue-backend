package com.yue.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {
    private boolean enabled;
    private String codePepper = "";
    private int expirySeconds = 600;
    private int cooldownSeconds = 60;
    private int emailDailyLimit = 10;
    private int ipDailyLimit = 30;
    private int maxFailures = 5;
    private String cleanupCron = "0 25 3 * * *";
    private Smtp smtp = new Smtp();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCodePepper() { return codePepper; }
    public void setCodePepper(String codePepper) { this.codePepper = codePepper; }
    public int getExpirySeconds() { return expirySeconds; }
    public void setExpirySeconds(int expirySeconds) { this.expirySeconds = expirySeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public int getEmailDailyLimit() { return emailDailyLimit; }
    public void setEmailDailyLimit(int emailDailyLimit) { this.emailDailyLimit = emailDailyLimit; }
    public int getIpDailyLimit() { return ipDailyLimit; }
    public void setIpDailyLimit(int ipDailyLimit) { this.ipDailyLimit = ipDailyLimit; }
    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    public String getCleanupCron() { return cleanupCron; }
    public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
    public Smtp getSmtp() { return smtp; }
    public void setSmtp(Smtp smtp) { this.smtp = smtp; }

    public boolean isReady() {
        return enabled && codePepper != null && codePepper.length() >= 32 && smtp.hasRequiredValues();
    }

    public static class Smtp {
        private String host = "smtpdm.aliyun.com";
        private int port = 465;
        private String username = "";
        private String password = "";
        private String from = "";
        private String fromName = "粤语学习";
        private boolean ssl = true;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }

        boolean hasRequiredValues() {
            return notBlank(host) && port > 0 && notBlank(username) && notBlank(password)
                    && notBlank(from) && notBlank(fromName);
        }

        private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    }
}
