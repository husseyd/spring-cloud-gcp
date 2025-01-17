/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.autoconfigure.core.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.core.GcpEnvironment;
import com.google.cloud.spring.core.GcpEnvironmentProvider;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Tests for on-GCP environment conditions.
 *
 * @since 1.1
 */
@RunWith(MockitoJUnitRunner.class)
public class OnGcpEnvironmentConditionTests {

  OnGcpEnvironmentCondition onGcpEnvironmentCondition = new OnGcpEnvironmentCondition();

  @Mock AnnotatedTypeMetadata mockMetadata;

  @Mock ConditionContext mockContext;

  @Mock ConfigurableListableBeanFactory mockBeanFactory;

  @Mock GcpEnvironmentProvider mockGcpEnvironmentProvider;

  @Before
  public void setUp() {
    when(this.mockContext.getBeanFactory()).thenReturn(this.mockBeanFactory);
    when(this.mockBeanFactory.getBean(GcpEnvironmentProvider.class))
        .thenReturn(this.mockGcpEnvironmentProvider);
  }

  @Test
  public void nullArgumentsTriggerAssertErrors() {
    assertThatThrownBy(() -> onGcpEnvironmentCondition.getMatchOutcome(null, mockMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application context cannot be null.");

    assertThatThrownBy(() -> onGcpEnvironmentCondition.getMatchOutcome(mockContext, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AnnotationTypeMetadata cannot be null.");
  }

  @Test
  public void nullBeanContextTriggerAssertErrors() {
    when(mockContext.getBeanFactory()).thenReturn(null);

    assertThatThrownBy(() -> onGcpEnvironmentCondition.getMatchOutcome(mockContext, mockMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Bean factory cannot be null.");
  }

  @Test
  public void testNoEnvironmentsMatchWhenMissingEnvironmentProvider() {

    setUpAnnotationValue(new GcpEnvironment[] {GcpEnvironment.UNKNOWN});
    when(this.mockBeanFactory.getBean(GcpEnvironmentProvider.class))
        .thenThrow(new NoSuchBeanDefinitionException("no environment"));

    assertThatThrownBy(
            () -> onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata))
        .isInstanceOf(NoSuchBeanDefinitionException.class)
        .hasMessage("No bean named 'no environment' available");
  }

  @Test
  public void testExceptionThrownWhenWrongAttributeType() {

    setUpAnnotationValue("invalid type");
    assertThatThrownBy(
            () -> onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata))
        .isInstanceOf(ClassCastException.class)
        .hasMessageContaining("java.lang.String cannot be cast");
  }

  @Test
  public void testExceptionThrownWhenMissingAttributeType() {

    // Should never happen in real life, as annotation value is not optional.
    setUpAnnotationValue(null);
    assertThatThrownBy(
            () -> onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value attribute of ConditionalOnGcpEnvironment cannot be null.");
  }

  @Test
  public void testExceptionThrownWhenAnnotationNotDeclared() {

    when(mockMetadata.getAnnotationAttributes(ConditionalOnGcpEnvironment.class.getName()))
        .thenReturn(null);
    assertThatThrownBy(
            () -> onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("@ConditionalOnGcpEnvironment annotation not declared on type.");
  }

  @Test
  public void testExceptionThrownWhenEnvironmentProviderBeanMissing() {

    setUpAnnotationValue(new GcpEnvironment[] {GcpEnvironment.COMPUTE_ENGINE});
    when(mockBeanFactory.getBean(GcpEnvironmentProvider.class)).thenReturn(null);

    assertThatThrownBy(
            () -> onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GcpEnvironmentProvider not found in context.");
  }

  @Test
  public void testNegativeOutcome() {
    setUpAnnotationValue(new GcpEnvironment[] {GcpEnvironment.COMPUTE_ENGINE});
    when(this.mockGcpEnvironmentProvider.getCurrentEnvironment())
        .thenReturn(GcpEnvironment.UNKNOWN);

    ConditionOutcome outcome =
        onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata);

    assertThat(outcome.isMatch()).isFalse();
    assertThat(outcome.getMessage())
        .isEqualTo("Application is not running on any of COMPUTE_ENGINE");
  }

  @Test
  public void testNegativeOutcomeForMultipleEnvironments() {
    setUpAnnotationValue(
        new GcpEnvironment[] {GcpEnvironment.COMPUTE_ENGINE, GcpEnvironment.KUBERNETES_ENGINE});
    when(this.mockGcpEnvironmentProvider.getCurrentEnvironment())
        .thenReturn(GcpEnvironment.UNKNOWN);

    ConditionOutcome outcome =
        onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata);

    assertThat(outcome.isMatch()).isFalse();
    assertThat(outcome.getMessage())
        .isEqualTo("Application is not running on any of COMPUTE_ENGINE, KUBERNETES_ENGINE");
  }

  @Test
  public void testPositiveOutcomeForMultipleEnvironments() {
    setUpAnnotationValue(
        new GcpEnvironment[] {GcpEnvironment.COMPUTE_ENGINE, GcpEnvironment.KUBERNETES_ENGINE});
    when(this.mockGcpEnvironmentProvider.getCurrentEnvironment())
        .thenReturn(GcpEnvironment.KUBERNETES_ENGINE);

    ConditionOutcome outcome =
        onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata);

    assertThat(outcome.isMatch()).isTrue();
    assertThat(outcome.getMessage()).isEqualTo("Application is running on KUBERNETES_ENGINE");
  }

  @Test
  public void testPositiveOutcome() {
    setUpAnnotationValue(new GcpEnvironment[] {GcpEnvironment.COMPUTE_ENGINE});
    when(this.mockGcpEnvironmentProvider.getCurrentEnvironment())
        .thenReturn(GcpEnvironment.COMPUTE_ENGINE);

    ConditionOutcome outcome =
        onGcpEnvironmentCondition.getMatchOutcome(this.mockContext, this.mockMetadata);

    assertThat(outcome.isMatch()).isTrue();
    assertThat(outcome.getMessage()).isEqualTo("Application is running on COMPUTE_ENGINE");
  }

  private void setUpAnnotationValue(Object environments) {
    when(this.mockMetadata.getAnnotationAttributes(ConditionalOnGcpEnvironment.class.getName()))
        .thenReturn(Collections.singletonMap("value", environments));
  }
}
