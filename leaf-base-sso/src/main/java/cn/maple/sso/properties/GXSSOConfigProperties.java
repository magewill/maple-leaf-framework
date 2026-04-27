package cn.maple.sso.properties;

public class GXSSOConfigProperties {
    private GXSSOProperties config = new GXSSOProperties();

    public GXSSOProperties getConfig() {
        return config;
    }

    public void setConfig(GXSSOProperties config) {
        this.config = config == null ? new GXSSOProperties() : config;
    }
}
