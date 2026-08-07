package cn.maple.extension.register;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtensionCoordinate;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base executor for invoking extension point methods.
 */
public abstract class GXAbstractComponentExecutor {
    /**
     * Executes an extension method and returns its result.
     *
     * @param targetClz   extension point interface
     * @param bizScenario business scenario
     * @param exeFunction extension invocation
     * @param <R>         result type
     * @param <T>         extension point type
     * @return invocation result
     */
    public <R, T> R execute(Class<T> targetClz, GXBizScenario bizScenario, Function<T, R> exeFunction) {
        if (targetClz == null || bizScenario == null || exeFunction == null) {
            throw new NullPointerException("targetClz, bizScenario and exeFunction cannot be null");
        }
        T component = locateComponent(targetClz, bizScenario);
        return exeFunction.apply(component);
    }

    /**
     * Executes an extension method using an extension coordinate.
     *
     * @param extensionCoordinate extension coordinate
     * @param exeFunction         extension invocation
     * @param <R>                 result type
     * @param <T>                 extension point type
     * @return invocation result
     */
    public <R, T> R execute(GXExtensionCoordinate extensionCoordinate, Function<T, R> exeFunction) {
        if (extensionCoordinate == null || exeFunction == null) {
            throw new NullPointerException("extensionCoordinate and exeFunction cannot be null");
        }
        if (extensionCoordinate.getExtensionPointClass() != null && extensionCoordinate.getBizScenario() != null) {
            return execute(extensionCoordinate.getExtensionPointClass(), extensionCoordinate.getBizScenario(), exeFunction);
        }
        T component = locateComponent(extensionCoordinate.getExtensionPointName(),
                extensionCoordinate.getBizScenarioUniqueIdentity());
        return exeFunction.apply(component);
    }

    /**
     * Executes an extension method without a return value.
     *
     * @param targetClz   extension point interface
     * @param context     business scenario
     * @param exeFunction extension invocation
     * @param <T>         extension point type
     */
    public <T> void executeVoid(Class<T> targetClz, GXBizScenario context, Consumer<T> exeFunction) {
        if (targetClz == null || context == null || exeFunction == null) {
            throw new NullPointerException("targetClz, context and exeFunction cannot be null");
        }
        T component = locateComponent(targetClz, context);
        exeFunction.accept(component);
    }

    /**
     * Executes an extension method without a return value using an extension coordinate.
     *
     * @param extensionCoordinate extension coordinate
     * @param exeFunction         extension invocation
     * @param <T>                 extension point type
     */
    public <T> void executeVoid(GXExtensionCoordinate extensionCoordinate, Consumer<T> exeFunction) {
        if (extensionCoordinate == null || exeFunction == null) {
            throw new NullPointerException("extensionCoordinate and exeFunction cannot be null");
        }
        if (extensionCoordinate.getExtensionPointClass() != null && extensionCoordinate.getBizScenario() != null) {
            executeVoid(extensionCoordinate.getExtensionPointClass(), extensionCoordinate.getBizScenario(), exeFunction);
            return;
        }
        T component = locateComponent(extensionCoordinate.getExtensionPointName(),
                extensionCoordinate.getBizScenarioUniqueIdentity());
        exeFunction.accept(component);
    }

    /**
     * Locates an extension implementation by interface and scenario.
     *
     * @param targetClz extension point interface
     * @param context   business scenario
     * @param <C>       extension point type
     * @return extension implementation
     */
    protected abstract <C> C locateComponent(Class<C> targetClz, GXBizScenario context);

    /**
     * Locates an extension implementation by string coordinate.
     *
     * @param extensionPointName        extension point interface name
     * @param bizScenarioUniqueIdentity business scenario identity
     * @param <C>                       extension point type
     * @return extension implementation
     */
    protected <C> C locateComponent(String extensionPointName, String bizScenarioUniqueIdentity) {
        throw new UnsupportedOperationException("String coordinate locate is not supported");
    }
}
