package io.github.swagger.properties;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResponseMessage;
import springfox.documentation.service.SecurityScheme;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * DocketProperties
 *
 * @author Wilson
 */
@Setter
@ToString
public class DocketProperties {
    public final static String DEFAULT_DOCKET = "docket";
    private final static String LANGUAGE_EN = "en";
    private final static String LANGUAGE_CN = "cn";
    /**
     * 路径映射,默认"/"
     */
    private String pathMapping = "/";
    /**
     * 主机ip,默认localhost
     */
    private String host = "localhost";
    /**
     * 分组名称(swagger-ui页面右上角下拉框栏目)
     */
    private String groupName;
    /**
     * controller package
     */
    private String basePackage;
    /**
     * 协议,默认[http,https]
     */
    private Set<String> protocols = Sets.newHashSet("http", "https");
    /**
     * 返回结构内容类型集合(Content-Type)
     */
    private Set<MediaType> produces = Sets.newHashSet(MediaType.ALL);
    /**
     * 提交参数内容类型集合(Content-Type)
     */
    private Set<MediaType> consumes = Sets.newHashSet(MediaType.ALL);
    /**
     * 全局参数配置
     */
    private List<ParameterProperties> globalParameters = new ArrayList<>();
    /**
     * 全局参数配置,作配置提示作用,若parameters无配置则添加该配置,若parameters已配置则该配置无效
     */
    private ParameterProperties globalParameter;
    private ApiInfoProperties apiInfo = new ApiInfoProperties();
    /**
     * 全局返回默认信息,默认配置了401、403、404、500
     */
    private List<ResponseMessageProperties> responseMessages = new ArrayList<>();
    /**
     * 返回默认信息的国家语言(en-英文,cn-中文)
     */
    private String responseMessageLanguage = LANGUAGE_EN;
    /**
     * url显示配置
     */
    private PathSelectorsProperties pathSelectors;
    /**
     * 安全上下文配置
     */
    private List<SecurityContextProperties> securityContexts;
    /**
     * 安全方案列表(basicAuth、oauth、apiKey)
     */
    private SecuritySchemeProperties securitySchemas;
    /**
     * 忽略的参数类型
     */
    private Class<?>[] ignoredParameterTypes;
    /**
     * 类型转换,基数下标取代其下标-1的偶数下标,如[java.lang.Integer,java.lang.String,java.Timestamp.class, java.lang.Long]
     * Integer将转成String,Timestamp将转成Long
     */
    private List<Class> directModelSubstitutes;
    private Class<?>[] genericModelSubstitutes;

    public Docket toDocket(String beanName, String contextPath, String port) {
        List<ResponseMessage> responseMessages = toResponseMessages();
        List<String> produceTypes = Arrays.asList(MediaType.toString(produces)
                .replaceAll("\\s+", "")
                .split(","));
        List<String> consumeTypes = Arrays.asList(MediaType.toString(consumes)
                .replaceAll("\\s+", "")
                .split(","));
        Docket docket = new Docket(DocumentationType.SWAGGER_2);
        if (directModelSubstitutes != null && directModelSubstitutes.size() % 2 == 0) {
            IntStream.range(0, directModelSubstitutes.size())
                    .filter(i -> i % 2 == 0)
                    .forEach(i -> docket.directModelSubstitute(directModelSubstitutes.get(i), directModelSubstitutes.get(i + 1)));
        }
        return docket.pathMapping(pathMapping)
                .host(host + ":" + port)
                .genericModelSubstitutes(Optional.ofNullable(genericModelSubstitutes).orElse(new Class[0]))
                .pathProvider(Objects.equals(DEFAULT_DOCKET, beanName) ? new DefaultPathProvider(contextPath) : null)
                .groupName(Optional.ofNullable(groupName).orElse(beanName))
                .securityContexts(toSecurityContexts())
                .securitySchemes(toSecuritySchemes())
                .apiInfo(apiInfo.toApiInfo())
                .globalOperationParameters(toGlobalParameters())
                .protocols(protocols)
                .produces(new HashSet<>(produceTypes))
                .consumes(new HashSet<>(consumeTypes))
                .globalResponseMessage(RequestMethod.GET, responseMessages)
                .globalResponseMessage(RequestMethod.PUT, responseMessages)
                .globalResponseMessage(RequestMethod.POST, responseMessages)
                .globalResponseMessage(RequestMethod.DELETE, responseMessages)
                .ignoredParameterTypes(Optional.ofNullable(ignoredParameterTypes).orElse(new Class[0]))
                .select()
                .apis(RequestHandlerSelectors.basePackage(basePackage))
                .paths(toPathSelector())
                .build();
    }

    private List<Parameter> toGlobalParameters() {
        if (globalParameter != null && globalParameters.isEmpty()) {
            globalParameters.add(globalParameter);
        }
        return globalParameters.stream()
                .map(ParameterProperties::build)
                .collect(Collectors.toList());
    }

    private Predicate<String> toPathSelector() {
        if (pathSelectors == null || pathSelectors.isEmpty()) {
            return PathSelectors.any();
        }
        Predicate includePaths = pathSelectors.getIncludePatterns()
                .stream()
                .map(PathSelectors::ant)
                .reduce(Predicates::or)
                .orElse(Predicates.alwaysTrue());
        Predicate excludePaths = pathSelectors.getExcludePatterns()
                .stream()
                .map(e -> Predicates.not(PathSelectors.ant(e)))
                .reduce(Predicates::or)
                .orElse(null);
        return excludePaths == null ? Predicates.and(includePaths) : Predicates.and(includePaths, excludePaths);
    }

    private List<ResponseMessage> toResponseMessages() {
        List<ResponseMessage> defaults = LANGUAGE_EN.equals(responseMessageLanguage)
                ? ResponseMessageProperties.DEFAULT_EN_MESSAGES : ResponseMessageProperties.DEFAULT_CN_MESSAGES;
        responseMessages.forEach(e -> defaults.add(e.toResponseMessage(null)));
        return defaults;
    }

    private List<SecurityContext> toSecurityContexts() {
        return securityContexts == null || securityContexts.isEmpty() ? Collections.emptyList()
                : securityContexts.stream()
                .map(SecurityContextProperties::toSecurityContext)
                .collect(Collectors.toList());
    }

    private List<SecurityScheme> toSecuritySchemes() {
        return securitySchemas == null ? Collections.emptyList() : securitySchemas.toSecuritySchemes();
    }

    public String getHost() {
        return host;
    }
}
