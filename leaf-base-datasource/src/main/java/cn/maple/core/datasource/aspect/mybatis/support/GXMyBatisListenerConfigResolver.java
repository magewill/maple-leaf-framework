package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.datasource.util.GXMyBatisListenerAnnotationUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Resolves {@link GXMyBatisListener} declarations with deterministic ordering and de-duplication.
 *
 * <p>Results are cached per candidate type set and method signature. A negative result is cached as
 * well, which lets the aspects short-circuit write operations that declare no listener.</p>
 */
@Component
public class GXMyBatisListenerConfigResolver {
    private static final Comparator<Class<?>> TYPE_ORDER = Comparator.comparing(Class::getName);
    /**
     * Delivery order of one write operation: declaring interface fully-qualified name first, listener
     * type name second (contracts/event-payload.md 投递与顺序). The listener name is only the
     * tie-breaker, so two listeners declared on the same interface are still ordered reproducibly.
     *
     * <p>Format dependency: this keys on {@link ResolvedListener#declaredFrom()}, whose value starts
     * with the declaring interface FQN (type-level: {@code FQN}, method-level: {@code FQN#method}).
     * The FQN prefix is therefore part of the ordering contract: changing that format re-orders
     * delivery and with it the {@code propagate-exception} first-failure identity. The attribution
     * is pinned by {@code GXMyBatisListenerDeduplicationTest}.</p>
     */
    private static final Comparator<ResolvedListener> LISTENER_ORDER =
            Comparator.comparing(ResolvedListener::declaredFrom)
                    .thenComparing(listener -> listener.listenerClazz().getName());
    private static final ClassValue<List<Class<?>>> MAPPER_INTERFACE_CACHE = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            Set<Class<?>> types = new LinkedHashSet<>();
            collectInterfaces(type, types);
            return types.stream().sorted(TYPE_ORDER).toList();
        }
    };

    private final GXMyBatisListenerProperties properties;
    private final Cache<String, List<ResolvedListener>> resolutionCache;

    public GXMyBatisListenerConfigResolver(GXMyBatisListenerProperties properties) {
        this.properties = properties;
        this.resolutionCache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getMetadataCacheSize()))
                .expireAfterAccess(Math.max(1L, properties.getMetadataCacheExpireSeconds()), TimeUnit.SECONDS)
                .build();
    }

    /**
     * Collects candidate declaring types for a MyBatis mapper target.
     *
     * <p>Only interfaces are considered, matching the declaration scope of a mapper proxy. The
     * proxy implementation class itself is intentionally excluded so that a declaration is not
     * counted twice.</p>
     */
    public List<Class<?>> mapperCandidateTypes(@Nullable Object target) {
        if (Objects.isNull(target)) {
            return List.of();
        }
        return MAPPER_INTERFACE_CACHE.get(AopUtils.getTargetClass(target));
    }

    /**
     * Collects candidate declaring types for a batch service: the service type itself plus the
     * mapper type and their interfaces.
     */
    public List<Class<?>> serviceCandidateTypes(@Nullable Object target, @Nullable Class<?> extraType) {
        Set<Class<?>> types = new LinkedHashSet<>();
        if (Objects.nonNull(target)) {
            Class<?> targetClass = AopUtils.getTargetClass(target);
            types.add(targetClass);
            types.addAll(MAPPER_INTERFACE_CACHE.get(targetClass));
        }
        if (Objects.nonNull(extraType)) {
            types.add(extraType);
            collectInterfaces(extraType, types);
        }
        return types.stream().sorted(TYPE_ORDER).toList();
    }

    /**
     * Resolves the listener declarations that apply to the invoked method.
     *
     * @param candidateTypes candidate declaring types, order does not matter
     * @param invokedMethod  the method that was invoked on the proxy
     * @return de-duplicated listeners in deterministic order, never {@code null}
     */
    public List<ResolvedListener> resolve(@Nullable List<Class<?>> candidateTypes, @Nullable Method invokedMethod) {
        if (Objects.isNull(invokedMethod) || Objects.isNull(candidateTypes) || candidateTypes.isEmpty()) {
            return List.of();
        }
        List<Class<?>> sortedTypes = isNormalized(candidateTypes)
                ? candidateTypes
                : candidateTypes.stream().filter(Objects::nonNull).distinct().sorted(TYPE_ORDER).toList();
        if (sortedTypes.isEmpty()) {
            return List.of();
        }
        String cacheKey = buildCacheKey(sortedTypes, invokedMethod);
        List<ResolvedListener> resolved = resolutionCache.get(cacheKey, key -> doResolve(sortedTypes, invokedMethod));
        return Objects.isNull(resolved) ? List.of() : resolved;
    }

    private boolean isNormalized(List<Class<?>> types) {
        Class<?> previous = null;
        for (Class<?> type : types) {
            if (Objects.isNull(type)) {
                return false;
            }
            if (Objects.nonNull(previous) && previous.getName().compareTo(type.getName()) >= 0) {
                return false;
            }
            previous = type;
        }
        return true;
    }

    private List<ResolvedListener> doResolve(List<Class<?>> sortedTypes, Method invokedMethod) {
        List<ResolvedListener> methodLevel = new ArrayList<>();
        List<ResolvedListener> typeLevel = new ArrayList<>();
        for (Class<?> candidate : sortedTypes) {
            GXMyBatisListenerAnnotationUtils.ResolvedDeclaration methodDeclaration =
                    GXMyBatisListenerAnnotationUtils.findMethodDeclaration(candidate, invokedMethod);
            if (Objects.nonNull(methodDeclaration)) {
                // The annotation may live on a parent interface method; attribute it to the type that
                // really declares it so the diagnostic origin matches the source (data-model §2).
                addListener(methodLevel, methodDeclaration.annotation(),
                        methodDeclaration.declaringType().getName() + "#" + invokedMethod.getName());
            }
            GXMyBatisListenerAnnotationUtils.ResolvedDeclaration typeDeclaration =
                    GXMyBatisListenerAnnotationUtils.findTypeDeclaration(candidate);
            if (Objects.nonNull(typeDeclaration)) {
                addListener(typeLevel, typeDeclaration.annotation(), typeDeclaration.declaringType().getName());
            }
        }
        List<ResolvedListener> selected = methodLevel.isEmpty() ? typeLevel : methodLevel;
        if (selected.isEmpty()) {
            return List.of();
        }
        List<ResolvedListener> ordered = selected.stream().sorted(LISTENER_ORDER).toList();
        if (!properties.isDeduplicate()) {
            return List.copyOf(ordered);
        }
        Map<String, ResolvedListener> deduplicated = new LinkedHashMap<>();
        for (ResolvedListener listener : ordered) {
            deduplicated.putIfAbsent(listener.listenerClazz().getName() + "|" + listener.async(), listener);
        }
        return List.copyOf(deduplicated.values());
    }

    private void addListener(List<ResolvedListener> target, GXMyBatisListener annotation, String declaredFrom) {
        Class<? extends GXMybatisListenerService<?>> listenerClazz = annotation.listenerClazz();
        if (Objects.isNull(listenerClazz)) {
            return;
        }
        boolean async = CharSequenceUtil.equals(annotation.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT);
        ResolvedListener listener = new ResolvedListener(listenerClazz, async, declaredFrom);
        boolean alreadyPresent = target.stream().anyMatch(existing ->
                existing.listenerClazz().equals(listener.listenerClazz())
                        && existing.async() == listener.async()
                        && CharSequenceUtil.equals(existing.declaredFrom(), listener.declaredFrom()));
        if (!alreadyPresent) {
            target.add(listener);
        }
    }

    private static void collectInterfaces(@Nullable Class<?> root, Set<Class<?>> collected) {
        if (Objects.isNull(root) || root.isPrimitive() || root.isArray()) {
            return;
        }
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(root);
        Set<Class<?>> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            Class<?> current = pending.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (Class<?> interfaceType : current.getInterfaces()) {
                if (collected.add(interfaceType) && !visited.contains(interfaceType)) {
                    pending.add(interfaceType);
                }
            }
        }
    }

    private String buildCacheKey(List<Class<?>> sortedTypes, Method invokedMethod) {
        StringBuilder builder = new StringBuilder(128);
        for (Class<?> type : sortedTypes) {
            builder.append(type.getName()).append(';');
        }
        builder.append('#').append(invokedMethod.getName()).append('(');
        for (Class<?> parameterType : invokedMethod.getParameterTypes()) {
            builder.append(parameterType.getName()).append(',');
        }
        return builder.append(')').toString();
    }

    /**
     * A resolved listener declaration.
     *
     * @param listenerClazz target listener implementation
     * @param async         whether the declaration asked for after-commit delivery
     * @param declaredFrom  diagnostic origin of the declaration
     */
    public record ResolvedListener(Class<? extends GXMybatisListenerService<?>> listenerClazz,
                                   boolean async,
                                   String declaredFrom) {
    }
}
