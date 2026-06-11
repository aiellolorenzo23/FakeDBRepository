package io.github.aiellolorenzo23.fakedb.repository;

import io.github.aiellolorenzo23.fakedb.annotation.FakeDBDatasource;
import io.github.aiellolorenzo23.fakedb.core.FakeDBDatasources;
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;

public class FakeDBRepositoryFactoryBean implements FactoryBean<Object>, InitializingBean {

    private final FakeDBDatasources fakeDBDatasources;

    private String repositoryInterfaceName;

    private Class<?> repositoryInterface;

    private Object proxy;

    public FakeDBRepositoryFactoryBean(FakeDBDatasources fakeDBDatasources) {
        this.fakeDBDatasources = fakeDBDatasources;
    }

    public void setRepositoryInterfaceName(String repositoryInterfaceName) {
        this.repositoryInterfaceName = repositoryInterfaceName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (repositoryInterfaceName == null || repositoryInterfaceName.isBlank()) {
            throw new FakeDBConfigurationException("FakeDB repository interface name is required");
        }

        repositoryInterface = ClassUtils.forName(repositoryInterfaceName, getClass().getClassLoader());
        if (!repositoryInterface.isInterface()) {
            throw new FakeDBConfigurationException(
                    "FakeDB repository " + repositoryInterface.getName() + " must be an interface"
            );
        }

        RepositoryTypes repositoryTypes = resolveRepositoryTypes(repositoryInterface);
        FakeDBRepository<?, ?> delegate = fakeDBDatasources.template(resolveDatasourceName(repositoryInterface)).repository(
                repositoryTypes.entityClass(),
                repositoryTypes.idClass()
        );

        proxy = Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                new RepositoryInvocationHandler(repositoryInterface, repositoryTypes.entityClass(), delegate)
        );
    }

    private String resolveDatasourceName(Class<?> repositoryInterface) {
        FakeDBDatasource datasource = repositoryInterface.getAnnotation(FakeDBDatasource.class);
        return datasource == null ? null : datasource.value();
    }

    @Override
    public Object getObject() {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private RepositoryTypes resolveRepositoryTypes(Class<?> source) {
        for (Type genericInterface : source.getGenericInterfaces()) {
            RepositoryTypes resolved = resolveRepositoryTypes(genericInterface);
            if (resolved != null) {
                return resolved;
            }
        }

        throw new FakeDBConfigurationException(
                "FakeDB repository " + source.getName() + " must extend FakeDBRepository<T, ID>"
        );
    }

    @Nullable
    private RepositoryTypes resolveRepositoryTypes(Type source) {
        if (source instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType == FakeDBRepository.class) {
                return new RepositoryTypes(
                        resolveClass(parameterizedType.getActualTypeArguments()[0]),
                        resolveClass(parameterizedType.getActualTypeArguments()[1])
                );
            }

            if (rawType instanceof Class<?> rawClass) {
                return resolveRepositoryTypes(rawClass);
            }
        }

        if (source instanceof Class<?> sourceClass) {
            return resolveRepositoryTypes(sourceClass);
        }

        return null;
    }

    private Class<?> resolveClass(Type type) {
        if (type instanceof Class<?> sourceClass) {
            return sourceClass;
        }

        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawClass) {
            return rawClass;
        }

        throw new FakeDBConfigurationException("Unsupported FakeDB repository generic type " + type.getTypeName());
    }

    private record RepositoryTypes(Class<?> entityClass, Class<?> idClass) {
    }

    private static class RepositoryInvocationHandler implements InvocationHandler {

        private final Class<?> repositoryInterface;

        private final FakeDBRepository<?, ?> delegate;

        private final FakeDBQueryMethodInvoker queryMethodInvoker;

        private RepositoryInvocationHandler(
                Class<?> repositoryInterface,
                Class<?> entityClass,
                FakeDBRepository<?, ?> delegate
        ) {
            this.repositoryInterface = repositoryInterface;
            this.delegate = delegate;
            this.queryMethodInvoker = new FakeDBQueryMethodInvoker(entityClass, delegate);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }

            Method delegateMethod = findDelegateMethod(method);
            if (delegateMethod == null) {
                if (queryMethodInvoker.supports(method)) {
                    return queryMethodInvoker.invoke(method, args);
                }

                throw new UnsupportedOperationException("FakeDB repository method is not supported: " + method.getName());
            }

            return delegateMethod.invoke(delegate, args);
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> repositoryInterface.getName() + " proxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unsupported Object method " + method.getName());
            };
        }

        @Nullable
        private Method findDelegateMethod(Method method) {
            return Arrays.stream(FakeDBRepository.class.getMethods())
                    .filter(candidate -> hasSameSignature(candidate, method))
                    .findFirst()
                    .orElse(null);
        }

        private boolean hasSameSignature(Method left, Method right) {
            return left.getName().equals(right.getName())
                    && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
        }
    }
}
