package cn.maple.extension;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Unique coordinate for an extension point implementation.
 */
public class GXExtensionCoordinate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final String extensionPointName;
    @Getter
    private final String bizScenarioUniqueIdentity;
    private final Class<?> extensionPointClass;
    @Getter
    private final GXBizScenario bizScenario;
    private final int hashCode;

    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario) {
        Objects.requireNonNull(extPtClass, "Extension point class cannot be null");
        Objects.requireNonNull(bizScenario, "Biz scenario cannot be null");
        validateExtensionPointClass(extPtClass);

        String uniqueIdentity = bizScenario.getUniqueIdentity();
        validateText(uniqueIdentity, "Biz scenario unique identity");

        this.extensionPointClass = extPtClass;
        this.extensionPointName = extPtClass.getName();
        this.bizScenario = bizScenario;
        this.bizScenarioUniqueIdentity = uniqueIdentity;
        this.hashCode = calculateHashCode(this.extensionPointName, this.bizScenarioUniqueIdentity);
    }

    public GXExtensionCoordinate(String extensionPoint, String bizScenario) {
        validateText(extensionPoint, "Extension point name");
        validateText(bizScenario, "Biz scenario unique identity");

        this.extensionPointName = extensionPoint;
        this.bizScenarioUniqueIdentity = bizScenario;
        this.extensionPointClass = null;
        this.bizScenario = null;
        this.hashCode = calculateHashCode(this.extensionPointName, this.bizScenarioUniqueIdentity);
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

    private static void validateExtensionPointClass(Class<?> extPtClass) {
        if (!extPtClass.isInterface() || !GXExtensionPoint.class.isAssignableFrom(extPtClass)
                || extPtClass == GXExtensionPoint.class) {
            throw new IllegalArgumentException("Extension point class must be a GXExtensionPoint sub-interface");
        }
    }

    private static int calculateHashCode(String extensionPointName, String bizScenarioUniqueIdentity) {
        int result = extensionPointName.hashCode();
        result = 31 * result + bizScenarioUniqueIdentity.hashCode();
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getExtensionPointClass() {
        return (Class<T>) extensionPointClass;
    }

    @Override
    public int hashCode() {
        return hashCode;
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
