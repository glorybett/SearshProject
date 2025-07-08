package searchengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jsoup")
public class Config {
    private String userAgent;
    private String referrer;

    private int delay;

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public int getDelay() {  // Add this getter
        return delay;
    }

    public void setDelay(int delay) {  // Add this setter
        this.delay = delay;
    }
}