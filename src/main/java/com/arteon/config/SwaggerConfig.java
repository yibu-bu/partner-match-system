package com.arteon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;
import springfox.documentation.service.Contact;

/**
 * 自定义Swagger接口文档的配置
 */
@Configuration
@EnableSwagger2WebMvc
@Profile({"dev", "test"})  // 仅在特定环境下该Bean生效，千万不要在线上环境暴露接口文档地址
public class SwaggerConfig {

    @Bean(value = "defaultApi2")
    public Docket defaultApi2() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.arteon.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    /**
     * api 信息
     */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("伙伴组队系统")
                .description("项目接口文档")
                .termsOfServiceUrl("https://github.com/liyupi")
                .contact(new Contact("yibu", "https://github.com/yibu-bu", "xxx@qq.com"))
                .version("1.0")
                .build();
    }

}
