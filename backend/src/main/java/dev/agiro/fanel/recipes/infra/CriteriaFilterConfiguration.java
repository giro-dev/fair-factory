package dev.agiro.fanel.recipes.infra;

import dev.agiro.criteriafilter.autoconfigure.CriteriaFilterProperties;
import dev.agiro.criteriafilter.metamodel.CriteriaFilterBeanInitializer;
import dev.agiro.criteriafilter.metamodel.DateFieldResolver;
import dev.agiro.criteriafilter.metamodel.DatePatternResolver;
import dev.agiro.criteriafilter.metamodel.EntityFilterMetadataBuilder;
import dev.agiro.criteriafilter.metamodel.FilterMetadataRegistry;
import dev.agiro.criteriafilter.repository.CriteriaRepositoryRegistry;
import dev.agiro.criteriafilter.repository.jpa.JpaSpecificationTranslator;
import dev.agiro.criteriafilter.validation.FilterValidator;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires only the criteria-filter pieces Fanel uses (metamodel, validator and the JPA
 * specification translator). The library's {@code CriteriaFilterAutoConfiguration} is
 * excluded in {@code application.yml} because its web layer (extra handler mappings and a
 * non-RFC-9457 exception advice) does not fit this API.
 */
@Configuration
@EnableConfigurationProperties(CriteriaFilterProperties.class)
public class CriteriaFilterConfiguration {

    @Bean
    JpaSpecificationTranslator jpaSpecificationTranslator() {
        return new JpaSpecificationTranslator();
    }

    @Bean
    DatePatternResolver datePatternResolver(ObjectProvider<DateFieldResolver> resolvers,
                                            CriteriaFilterProperties properties) {
        return new DatePatternResolver(resolvers.orderedStream().toList(),
                properties.getDefaultDateTimePattern());
    }

    @Bean
    EntityFilterMetadataBuilder entityFilterMetadataBuilder(DatePatternResolver datePatternResolver) {
        return new EntityFilterMetadataBuilder(datePatternResolver);
    }

    @Bean
    FilterMetadataRegistry filterMetadataRegistry() {
        return new FilterMetadataRegistry();
    }

    @Bean
    CriteriaRepositoryRegistry criteriaRepositoryRegistry() {
        return new CriteriaRepositoryRegistry();
    }

    @Bean
    FilterValidator filterValidator(FilterMetadataRegistry registry) {
        return new FilterValidator(registry);
    }

    @Bean
    CriteriaFilterBeanInitializer criteriaFilterBeanInitializer(
            CriteriaFilterProperties properties,
            FilterMetadataRegistry metadataRegistry,
            CriteriaRepositoryRegistry repositoryRegistry,
            EntityFilterMetadataBuilder metadataBuilder,
            JpaSpecificationTranslator jpaTranslator,
            ObjectProvider<EntityManager> entityManagerProvider) {
        return new CriteriaFilterBeanInitializer(properties, metadataRegistry, repositoryRegistry,
                metadataBuilder, jpaTranslator, entityManagerProvider);
    }
}
