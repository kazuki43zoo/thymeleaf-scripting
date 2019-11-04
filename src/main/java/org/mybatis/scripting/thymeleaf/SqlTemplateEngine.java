package org.mybatis.scripting.thymeleaf;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import org.mybatis.scripting.thymeleaf.expression.Likes;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

public class SqlTemplateEngine {

  private final ITemplateEngine templateEngine;

  public SqlTemplateEngine() {
    this.templateEngine = createDefaultTemplateEngine(ThymeleafLanguageDriverConfig.newInstance());
  }

  public SqlTemplateEngine(ThymeleafLanguageDriverConfig config) {
    this.templateEngine = createDefaultTemplateEngine(config);
  }

  public SqlTemplateEngine(ITemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  private ITemplateEngine createDefaultTemplateEngine(ThymeleafLanguageDriverConfig config) {
    MyBatisDialect dialect = new MyBatisDialect(config.getDialect().getPrefix());
    dialect.setNamedParameterConfig(config.getDialect().getNamedParameter());
    Likes likes = Likes.newBuilder().escapeChar(config.getDialect().getLikeEscapeChar())
        .escapeClauseFormat(config.getDialect().getLikeEscapeClauseFormat())
        .additionalEscapeTargetChars(config.getDialect().getLikeAdditionalEscapeTargetChars()).build();
    dialect.setLikes(likes);

    // Create an ClassLoaderTemplateResolver instance
    ClassLoaderTemplateResolver classLoaderTemplateResolver = new ClassLoaderTemplateResolver();
    TemplateMode mode = config.isUse2way() ? TemplateMode.CSS : TemplateMode.TEXT;
    classLoaderTemplateResolver.setOrder(1);
    classLoaderTemplateResolver.setTemplateMode(mode);
    classLoaderTemplateResolver
        .setResolvablePatterns(Arrays.stream(config.getTemplateFile().getPatterns()).collect(Collectors.toSet()));
    classLoaderTemplateResolver.setCharacterEncoding(config.getTemplateFile().getEncoding().name());
    classLoaderTemplateResolver.setCacheable(config.getTemplateFile().isCacheEnabled());
    classLoaderTemplateResolver.setCacheTTLMs(config.getTemplateFile().getCacheTtl());
    classLoaderTemplateResolver.setPrefix(config.getTemplateFile().getBaseDir());

    // Create an StringTemplateResolver instance
    StringTemplateResolver stringTemplateResolver = new StringTemplateResolver();
    stringTemplateResolver.setOrder(2);
    stringTemplateResolver.setTemplateMode(mode);

    // Create an TemplateEngine instance
    TemplateEngine targetTemplateEngine = new TemplateEngine();
    targetTemplateEngine.addTemplateResolver(classLoaderTemplateResolver);
    targetTemplateEngine.addTemplateResolver(stringTemplateResolver);
    targetTemplateEngine.addDialect(dialect);
    targetTemplateEngine.setEngineContextFactory(
        new MyBatisIntegratingEngineContextFactory(targetTemplateEngine.getEngineContextFactory()));

    // Create an TemplateEngineCustomizer instance and apply
    final TemplateEngineCustomizer customizer = Optional.ofNullable(config.getCustomizer()).map(v -> {
      try {
        return v.getConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new IllegalStateException("Cannot create an instance for class: " + v, e);
      }
    }).map(TemplateEngineCustomizer.class::cast).orElse(TemplateEngineCustomizer.BuiltIn.DO_NOTHING);
    customizer.accept(targetTemplateEngine);

    return targetTemplateEngine;
  }

  public String process(String sqlTemplate, Object parameterObject) {
    return process(sqlTemplate, parameterObject, null);
  }

  public String process(String sqlTemplate, Object parameterObject, Map<String, Object> customBindAttributes) {
    ParameterBasedContext context = new ParameterBasedContext(parameterObject);
    String sql = templateEngine.process(sqlTemplate, context);
    if (customBindAttributes != null) {
      customBindAttributes.putAll(context.bindingContext.getCustomBindVariables());
    }
    return sql;
  }

  private static class ParameterBasedContext implements IContext {
    private final Object parameter;
    private final Map<String, Object> variables = new HashMap<>();
    private final MyBatisBindingContext bindingContext;

    private ParameterBasedContext(Object parameter) {
      this.parameter = parameter;
      if (parameter != null) {
        if (parameter instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> mapParameter = (Map<String, Object>) parameter;
          variables.putAll(mapParameter);
        } else {
          try {
            for (PropertyDescriptor pd : OgnlRuntime.getPropertyDescriptorsArray(parameter.getClass())) {
              if (!pd.getName().equals("class")) {
                variables.put(pd.getName(), null);
              }
            }
          } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
          }
        }
      }
      bindingContext = new MyBatisBindingContext(variables.isEmpty());
      System.out.println(variables);
      variables.put(MyBatisBindingContext.CONTEXT_VARIABLE_NAME, bindingContext);
      variables.put("_parameter", parameter);
    }

    @Override
    public Locale getLocale() {
      return Locale.getDefault();
    }

    @Override
    public boolean containsVariable(String name) {
      return variables.containsKey(name);
    }

    @Override
    public Set<String> getVariableNames() {
      return variables.keySet();
    }

    @Override
    public Object getVariable(String name) {
      return variables.computeIfAbsent(name, k -> {
        try {
          return Ognl.getValue(k, parameter);
        } catch (OgnlException e) {
          throw new IllegalStateException(e);
        }
      });
    }
  }

}
