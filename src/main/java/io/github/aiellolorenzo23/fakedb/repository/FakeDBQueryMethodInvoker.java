package io.github.aiellolorenzo23.fakedb.repository;

import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unchecked"})
class FakeDBQueryMethodInvoker {

    private final Class<?> entityClass;

    private final FakeDBRepository delegate;

    FakeDBQueryMethodInvoker(Class<?> entityClass, FakeDBRepository delegate) {
        this.entityClass = entityClass;
        this.delegate = delegate;
    }

    boolean supports(Method method) {
        String name = method.getName();
        return name.startsWith("findBy") || name.startsWith("existsBy") || name.startsWith("deleteBy");
    }

    Object invoke(Method method, Object[] args) {
        QueryOperation operation = QueryOperation.from(method.getName());
        List<QueryPart> parts = parseQueryParts(method.getName(), operation);
        Object[] safeArgs = args == null ? new Object[0] : args;
        validateArgumentCount(method, parts, safeArgs);

        Predicate<Object> predicate = entity -> matches(entity, parts, safeArgs);
        List<?> matches = delegate.findAll(predicate);

        return switch (operation) {
            case FIND -> findResult(method, matches);
            case EXISTS -> !matches.isEmpty();
            case DELETE -> deleteResult(method, matches);
        };
    }

    private List<QueryPart> parseQueryParts(String methodName, QueryOperation operation) {
        String criteria = methodName.substring(operation.prefix().length());
        if (criteria.isBlank()) {
            throw new FakeDBConfigurationException("FakeDB query method has no criteria: " + methodName);
        }

        return List.of(criteria.split("And")).stream()
                .map(this::parseQueryPart)
                .toList();
    }

    private QueryPart parseQueryPart(String source) {
        for (QueryOperator operator : QueryOperator.suffixedOperators()) {
            if (source.endsWith(operator.suffix())) {
                String property = source.substring(0, source.length() - operator.suffix().length());
                return new QueryPart(toPropertyName(property), operator);
            }
        }

        return new QueryPart(toPropertyName(source), QueryOperator.EQUALS);
    }

    private String toPropertyName(String source) {
        if (source.isBlank()) {
            throw new FakeDBConfigurationException("FakeDB query method contains an empty property");
        }
        return Character.toLowerCase(source.charAt(0)) + source.substring(1);
    }

    private void validateArgumentCount(Method method, List<QueryPart> parts, Object[] args) {
        long requiredArguments = parts.stream()
                .filter(part -> part.operator().requiresArgument())
                .count();

        if (requiredArguments != args.length) {
            throw new FakeDBConfigurationException(
                    "FakeDB query method " + method.getName() + " expects " + requiredArguments
                            + " arguments but received " + args.length
            );
        }
    }

    private boolean matches(Object entity, List<QueryPart> parts, Object[] args) {
        int argIndex = 0;

        for (QueryPart part : parts) {
            Object expected = part.operator().requiresArgument() ? args[argIndex++] : null;
            Object actual = readProperty(entity, part.property());

            if (!part.operator().matches(actual, expected)) {
                return false;
            }
        }

        return true;
    }

    private Object readProperty(Object entity, String property) {
        Field field = resolveField(property);
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new FakeDBConfigurationException("Cannot read FakeDB query field " + property, ex);
        }
    }

    private Field resolveField(String property) {
        Class<?> current = entityClass;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(property)) {
                    return field;
                }

                FakeDBColumn column = field.getAnnotation(FakeDBColumn.class);
                if (column != null && column.value().equals(property)) {
                    return field;
                }
            }

            current = current.getSuperclass();
        }

        throw new FakeDBConfigurationException(
                "FakeDB entity " + entityClass.getName() + " has no query field named " + property
        );
    }

    private Object findResult(Method method, List<?> matches) {
        Class<?> returnType = method.getReturnType();

        if (returnType == Optional.class) {
            return matches.stream().findFirst();
        }

        if (Collection.class.isAssignableFrom(returnType)) {
            return matches;
        }

        if (returnType == void.class) {
            throw new FakeDBConfigurationException("FakeDB find query method cannot return void: " + method.getName());
        }

        return matches.stream().findFirst().orElse(null);
    }

    private Object deleteResult(Method method, List<?> matches) {
        for (Object match : matches) {
            delegate.delete(match);
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return null;
        }
        if (returnType == long.class || returnType == Long.class) {
            return (long) matches.size();
        }
        if (returnType == int.class || returnType == Integer.class) {
            return matches.size();
        }
        if (Collection.class.isAssignableFrom(returnType)) {
            return matches;
        }

        throw new FakeDBConfigurationException(
                "FakeDB delete query method has unsupported return type: " + method.getName()
        );
    }

    private enum QueryOperation {
        FIND("findBy"),
        EXISTS("existsBy"),
        DELETE("deleteBy");

        private final String prefix;

        QueryOperation(String prefix) {
            this.prefix = prefix;
        }

        private String prefix() {
            return prefix;
        }

        private static QueryOperation from(String methodName) {
            for (QueryOperation operation : values()) {
                if (methodName.startsWith(operation.prefix)) {
                    return operation;
                }
            }

            throw new FakeDBConfigurationException("Unsupported FakeDB query method " + methodName);
        }
    }

    private enum QueryOperator {
        GREATER_THAN("GreaterThan", true),
        LESS_THAN("LessThan", true),
        CONTAINING("Containing", true),
        IN("In", true),
        TRUE("True", false),
        FALSE("False", false),
        EQUALS("", true);

        private final String suffix;

        private final boolean requiresArgument;

        QueryOperator(String suffix, boolean requiresArgument) {
            this.suffix = suffix;
            this.requiresArgument = requiresArgument;
        }

        private String suffix() {
            return suffix;
        }

        private boolean requiresArgument() {
            return requiresArgument;
        }

        private boolean matches(Object actual, Object expected) {
            return switch (this) {
                case GREATER_THAN -> compare(actual, expected) > 0;
                case LESS_THAN -> compare(actual, expected) < 0;
                case CONTAINING -> contains(actual, expected);
                case IN -> in(actual, expected);
                case TRUE -> Objects.equals(actual, true);
                case FALSE -> Objects.equals(actual, false);
                case EQUALS -> Objects.equals(actual, expected);
            };
        }

        private static List<QueryOperator> suffixedOperators() {
            return List.of(GREATER_THAN, LESS_THAN, CONTAINING, IN, TRUE, FALSE);
        }

        private static int compare(Object actual, Object expected) {
            if (actual == null || expected == null) {
                return 0;
            }
            if (!(actual instanceof Comparable comparable)) {
                throw new FakeDBConfigurationException("FakeDB query field is not comparable: " + actual);
            }
            return comparable.compareTo(expected);
        }

        private static boolean contains(Object actual, Object expected) {
            if (actual instanceof String string) {
                return expected != null && string.contains(expected.toString());
            }
            if (actual instanceof Collection<?> collection) {
                return collection.contains(expected);
            }
            return false;
        }

        private static boolean in(Object actual, Object expected) {
            if (expected instanceof Collection<?> collection) {
                return collection.contains(actual);
            }
            return false;
        }
    }

    private record QueryPart(String property, QueryOperator operator) {
    }
}
