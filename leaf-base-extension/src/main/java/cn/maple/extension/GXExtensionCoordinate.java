package cn.maple.extension;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Unique coordinate for an extension point implementation.
 */
public class GXExtensionCoordinate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String extensionPointName;
    private final String bizScenarioUniqueIdentity;
    private final Class<?> extensionPointClass;
    private final GXBizScenario bizScenario;

    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario) {
        Objects.requireNonNull(extPtClass, "Extension point class cannot be null");
        Objects.requireNonNull(bizScenario, "Biz scenario cannot be null");

        String uniqueIdentity = bizScenario.getUniqueIdentity();
        validateText(uniqueIdentity, "Biz scenario unique identity");

        this.extensionPointClass = extPtClass;
        this.extensionPointName = extPtClass.getName();
        this.bizScenario = bizScenario;
        this.bizScenarioUniqueIdentity = uniqueIdentity;
    }

    public GXExtensionCoordinate(String extensionPoint, String bizScenario) {
        validateText(extensionPoint, "Extension point name");
        validateText(bizScenario, "Biz scenario unique identity");

        this.extensionPointName = extensionPoint;
        this.bizScenarioUniqueIdentity = bizScenario;
        this.extensionPointClass = null;
        this.bizScenario = null;
    }

    public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario) {
        return new GXExtensionCoordinate(extPtClass, bizScenario);
    }

    public static GXExtensionCoordinate valueOf(String extensionPoint, String bizScenario) {
        return new GXExtensionCoordinate(extensionPoint, bizScenario);
    }

    private static void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getExtensionPointClass() {
        return (Class<T>) extensionPointClass;
    }

    @SuppressWarnings("unchecked")
    public GXBizScenario getBizScenario() {
        return bizScenario;
    }

    @SuppressWarnings("unchecked")
    public String getExtensionPointName() {
        return extensionPointName;
    }

    @SuppressWarnings("unchecked")
    public String getBizScenarioUniqueIdentity() {
        return bizScenarioUniqueIdentity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(extensionPointName, bizScenarioUniqueIdentity);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GXExtensionCoordinate other)) {
            return false;
        }
        return Objects.equals(extensionPointName, other.extensionPointName)
                && Objects.equals(bizScenarioUniqueIdentity, other.bizScenarioUniqueIdentity);
    }

    @Override
    public String toString() {
        return "GXExtensionCoordinate [extensionPointName=" + extensionPointName
                + ", bizScenarioUniqueIdentity=" + bizScenarioUniqueIdentity + "]";
    }
}
