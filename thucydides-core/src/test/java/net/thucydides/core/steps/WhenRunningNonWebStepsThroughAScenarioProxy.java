package net.thucydides.core.steps;

import net.thucydides.core.annotations.Pending;
import net.thucydides.core.annotations.Step;
import net.thucydides.core.annotations.StepGroup;
import net.thucydides.core.annotations.Steps;
import net.thucydides.core.annotations.Story;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.pages.Pages;
import net.thucydides.core.webdriver.WebdriverAssertionError;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WhenRunningNonWebStepsThroughAScenarioProxy {

    StepListener listener;

    @Mock
    StepListener mockListener;

    @Mock
    TestOutcome testOutcome;
    
    private StepFactory factory;

    private ArgumentCaptor<ExecutedStepDescription> argument;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);

        factory = new StepFactory();
        ArgumentCaptor<ExecutedStepDescription> argument = ArgumentCaptor.forClass(ExecutedStepDescription.class);

        listener = new ConsoleStepListener();
        StepEventBus.getEventBus().clear();
        StepEventBus.getEventBus().registerListener(listener);
        StepEventBus.getEventBus().registerListener(mockListener);

    }

    @After
    public void deregisterListener() {
        StepEventBus.getEventBus().dropListener(listener);
        StepEventBus.getEventBus().dropListener(mockListener);
    }

    static class SimpleSteps {

        public SimpleSteps() {
        }

        @Steps
        NestedSteps nestedSteps;

        @Step
        public void step1() { }

        @Step
        public void step2() {}

        @Step
        public void step3() {}

        @Step
        public void step_with_parameter(String name) {}

        @Step
        public void step_with_parameters(String name, int age) {}

        @Step
        public void failing_step() {
            throw new AssertionError("Oh bother");
        }

        @Step
        public void nested_steps() {
            nestedSteps.step1_1();
            nestedSteps.step1_2();
            nestedSteps.step1_3();
        }
    }

    static class NestedSteps {

        public NestedSteps() {
        }

        @Step
        public void step1_1() {}

        @Step
        public void step1_2() {}

        @Step
        public void step1_3() {}

    }

    @Test
    public void the_proxy_should_execute_steps_transparently() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();
        steps.step2();
        steps.step3();

        assertThat(listener.toString(), allOf(containsString("step1"), containsString("step2"), containsString("step3")));
    }

    @Test
    public void the_proxy_should_execute_nested_steps_transparently() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.nested_steps();
        assertThat(listener.toString(), allOf(containsString("nested_steps"),
                                              containsString("step1_1"),
                                              containsString("step1_2"),
                                              containsString("step1_3")));
    }


    @Test
    public void the_proxy_should_store_step_method_parameters() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step_with_parameter("Joe");

        assertThat(listener.toString(), allOf(containsString("step_with_parameter"),
                                              containsString("Joe")));
    }

    @Test
    public void the_proxy_should_store_multiple_step_method_parameters() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step_with_parameters("Joe", 10);

        assertThat(listener.toString(), allOf(containsString("step_with_parameter"),
                                              containsString("Joe"),
                                              containsString("10")));
    }


    @Test
    public void the_proxy_should_record_execution_structure() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();
        steps.step2();
        steps.nested_steps();
        steps.step3();

        String executedSteps = listener.toString();
        String expectedSteps =
        "step1\n" +
        "--> STEP DONE\n" +
        "step2\n" +
        "--> STEP DONE\n" +
        "nested_steps\n" +
        "-step1_1\n" +
        "---> STEP DONE\n" +
        "-step1_2\n" +
        "---> STEP DONE\n" +
        "-step1_3\n" +
        "---> STEP DONE\n" +
        "--> STEP DONE\n" +
        "step3\n" +
        "--> STEP DONE\n";

        assertThat(executedSteps, is(expectedSteps));
    }

    @Test
    public void the_proxy_should_notify_listeners_when_tests_are_starting() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();
        steps.step2();
        steps.step3();

        verify(mockListener, times(3)).stepStarted(any(ExecutedStepDescription.class));
    }

    class AStory {}

    @Story(AStory.class)
    class ATestCase {
        public void app_should_work() {}
    }

    @Test
    public void the_proxy_should_notify_listeners_when_tests_are_starting_with_details_about_step_name_and_class() {
        ArgumentCaptor<ExecutedStepDescription> argument = ArgumentCaptor.forClass(ExecutedStepDescription.class);

        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();

        verify(mockListener).stepStarted(argument.capture());
        assertThat(argument.getValue().getStepClass().getName(), is(SimpleSteps.class.getName()));
        assertThat(argument.getValue().getName(), is("step1"));
    }

    @Test
    public void the_proxy_should_notify_listeners_when_tests_have_finished() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();
        steps.step2();
        steps.step3();

        verify(mockListener, times(3)).stepFinished();
    }



    @Test
    public void the_proxy_should_skip_tests_after_a_failure() {
        SimpleSteps steps =  factory.getStepLibraryFor(SimpleSteps.class);

        steps.step1();
        steps.failing_step();
        steps.step3();

        String expectedExecution =
                "step1\n" +
                "--> STEP DONE\n" +
                "failing_step\n" +
                "--> STEP FAILED\n" +
                "step3\n" +
                "--> STEP IGNORED\n";
        assertThat(listener.toString(), is(expectedExecution));

    }

}
