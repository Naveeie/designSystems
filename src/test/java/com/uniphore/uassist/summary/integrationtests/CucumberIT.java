package com.uniphore.uassist.summary.integrationtests;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = {"src/test/resources/features"},
    plugin = {"pretty","html:test-reports/cucumber-reports.html"},
    glue = {"com.uniphore.uassist.summary.integrationtests.cucumberglue"})
public class CucumberIT {

}
