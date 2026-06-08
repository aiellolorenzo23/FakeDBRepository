package io.github.aiellolorenzo23.fakedb.repository;

import io.github.aiellolorenzo23.fakedb.annotation.EnableFakeDBRepositories;
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FakeDBRepositoriesRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(
            @NonNull AnnotationMetadata importingClassMetadata,
            @NonNull BeanDefinitionRegistry registry
    ) {
        ClassPathScanningCandidateComponentProvider scanner = repositoryScanner();

        for (String basePackage : resolveBasePackages(importingClassMetadata)) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                registerRepository(candidate.getBeanClassName(), registry);
            }
        }
    }

    private ClassPathScanningCandidateComponentProvider repositoryScanner() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                AnnotationMetadata metadata = beanDefinition.getMetadata();
                return metadata.isInterface() && metadata.isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(FakeDBRepository.class));
        scanner.setResourceLoader(resourceLoader);
        return scanner;
    }

    private List<String> resolveBasePackages(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(
                EnableFakeDBRepositories.class.getName()
        );

        List<String> basePackages = new ArrayList<>();
        if (attributes != null) {
            for (String basePackage : (String[]) attributes.get("basePackages")) {
                if (!basePackage.isBlank()) {
                    basePackages.add(basePackage);
                }
            }

            for (Class<?> basePackageClass : (Class<?>[]) attributes.get("basePackageClasses")) {
                basePackages.add(ClassUtils.getPackageName(basePackageClass));
            }
        }

        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        return basePackages;
    }

    private void registerRepository(String repositoryInterfaceName, BeanDefinitionRegistry registry) {
        if (repositoryInterfaceName == null || repositoryInterfaceName.equals(FakeDBRepository.class.getName())) {
            return;
        }

        String beanName = repositoryBeanName(repositoryInterfaceName);
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FakeDBRepositoryFactoryBean.class);
        builder.addPropertyValue("repositoryInterfaceName", repositoryInterfaceName);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        registry.registerBeanDefinition(beanName, beanDefinition);
    }

    private String repositoryBeanName(String repositoryInterfaceName) {
        String shortName = ClassUtils.getShortName(repositoryInterfaceName);
        return Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1);
    }
}
