/**
 *    Copyright 2018-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.scripting.thymeleaf.processor;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mybatis.scripting.thymeleaf.MyBatisBindingContext;
import org.mybatis.scripting.thymeleaf.NamedParameterConfig;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.engine.EngineEventUtils;
import org.thymeleaf.engine.IterationStatusVar;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * The processor class for handling the {@code mybatis:p} tag. <br>
 * This processor render bind variable({@code #{…​}}) expression that can parsed MyBatis and register an iteration
 * object to the MyBatis’s bind variables.
 *
 * @author Kazuki Shimizu
 * @version 1.0.0
 */
public class MyBatisParamTagProcessor extends AbstractAttributeTagProcessor {
  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{(.*)}");
  private static final int PRECEDENCE = 1400;
  private static final String ATTR_NAME = "p";

  private final StandardExpressionExecutionContext expressionExecutionContext;

  private NamedParameterConfig namedParameterConfig = new NamedParameterConfig();

  /**
   * Constructor that can be specified the template mode and dialect prefix.
   *
   * @param templateMode
   *          A target template mode
   * @param prefix
   *          A target dialect prefix
   */
  public MyBatisParamTagProcessor(final TemplateMode templateMode, final String prefix) {
    super(templateMode, prefix, null, false, ATTR_NAME, true, PRECEDENCE, true);
    expressionExecutionContext = templateMode == TemplateMode.TEXT ? StandardExpressionExecutionContext.RESTRICTED
        : StandardExpressionExecutionContext.NORMAL;
  }

  public void setNamedParameterConfig(NamedParameterConfig namedParameterConfig) {
    this.namedParameterConfig = namedParameterConfig;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
      String attributeValue, IElementTagStructureHandler structureHandler) {

    Matcher expressionMatcher = EXPRESSION_PATTERN.matcher(attributeValue);
    if (expressionMatcher.find()) {
      for (int i = 0; i < expressionMatcher.groupCount(); i++) {
        attributeValue = EXPRESSION_PATTERN.matcher(attributeValue).replaceFirst(
            getExpressionEvaluatedValue(context, tag, attributeName, expressionMatcher.group(i + 1)).toString());
      }
    }

    Pair parameterAndOptionPair = Pair.parse(attributeValue, ',');
    String parameterPath = parameterAndOptionPair.left;
    String options = parameterAndOptionPair.right;

    Pair objectNameAndPropertyPathPair = Pair.parse(parameterPath, '.');
    String objectName = objectNameAndPropertyPathPair.left;
    String nestedPropertyPath = objectNameAndPropertyPathPair.right;

    String body;
    String iterationObjectName = objectName + "Stat";
    if (context.containsVariable(iterationObjectName)) {
      MyBatisBindingContext bindingContext = MyBatisBindingContext.load(context);
      IterationStatusVar iterationStatus = (IterationStatusVar) context.getVariable(iterationObjectName);
      String iterationObjectVariableName = bindingContext.generateUniqueName(objectName, iterationStatus);
      if (!bindingContext.containsCustomBindVariable(iterationObjectVariableName)) {
        bindingContext.setCustomBindVariable(iterationObjectVariableName, iterationStatus.getCurrent());
      }
      if (nestedPropertyPath.isEmpty()) {
        body = namedParameterConfig.getPrefix() + iterationObjectVariableName + options
            + namedParameterConfig.getSuffix();
      } else {
        Object value = getExpressionEvaluatedValue(context, tag, attributeName, parameterPath);
        if (isCollectionOrArray(value)) {
          body = generateCollectionBindVariables(value, iterationObjectVariableName + nestedPropertyPath, options);
        } else {
          body = namedParameterConfig.getPrefix() + iterationObjectVariableName + nestedPropertyPath + options
              + namedParameterConfig.getSuffix();
        }
      }
    } else {
      Object value = nestedPropertyPath.isEmpty() ? context.getVariable(objectName)
          : getExpressionEvaluatedValue(context, tag, attributeName, parameterPath);
      if (isCollectionOrArray(value)) {
        body = generateCollectionBindVariables(value, parameterPath, options);
      } else {
        body = namedParameterConfig.getPrefix() + attributeValue + namedParameterConfig.getSuffix();
      }
    }
    structureHandler.setBody(body, false);
  }

  private Object getExpressionEvaluatedValue(ITemplateContext context, IProcessableElementTag tag,
      AttributeName attributeName, String parameterValue) {
    IStandardExpression expression = EngineEventUtils.computeAttributeExpression(context, tag, attributeName,
        "${" + parameterValue + "}");
    return expression.execute(context, this.expressionExecutionContext);
  }

  private boolean isCollectionOrArray(Object value) {
    return value != null && (Collection.class.isAssignableFrom(value.getClass()) || value.getClass().isArray());
  }

  private String generateCollectionBindVariables(Object value, String parameterPath, String options) {
    int size = value.getClass().isArray() ? Array.getLength(value) : ((Collection) value).size();
    if (size == 0) {
      return "null";
    } else {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size; i++) {
        if (i != 0) {
          sb.append(", ");
        }
        sb.append(namedParameterConfig.getPrefix()).append(parameterPath).append("[").append(i).append("]")
            .append(options).append(namedParameterConfig.getSuffix());
      }
      return sb.toString();
    }
  }

  private static class Pair {

    private final String left;
    private final String right;

    private Pair(String left, String right) {
      this.left = left;
      this.right = right;
    }

    private static Pair parse(String value, char separator) {
      int separatorIndex = value.indexOf(separator);
      String left;
      String right;
      if (separatorIndex == -1) {
        left = value;
        right = "";
      } else {
        left = value.substring(0, separatorIndex);
        right = value.substring(separatorIndex);
      }
      return new Pair(left, right);
    }

  }

}
