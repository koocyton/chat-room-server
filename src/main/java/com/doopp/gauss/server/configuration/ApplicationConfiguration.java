package com.doopp.gauss.server.configuration;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableCaching

@Import({
    EhcacheConfiguration.class,
    RedisConfiguration.class,
    MyBatisConfiguration.class,
    TaskExecutorConfiguration.class
})

@ComponentScan(basePackages={"com.doopp.gauss"}, excludeFilters={
    @ComponentScan.Filter(type= FilterType.ANNOTATION, value=EnableWebMvc.class)
})

public class ApplicationConfiguration {

//    @Bean
//    public PropertyPlaceholderConfigurer propertyConfigurer() {
//        PropertyPlaceholderConfigurer propertyConfigurer = new PropertyPlaceholderConfigurer();
//        propertyConfigurer.setLocation(new ClassPathResource("config/application.properties"));
//        return propertyConfigurer;
//    }

}
