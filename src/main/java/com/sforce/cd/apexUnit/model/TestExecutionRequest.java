package com.sforce.cd.apexUnit.model;

import java.util.Collection;
import java.util.ArrayList;
import com.google.gson.Gson;

@SuppressWarnings("unused")
public class TestExecutionRequest {
    private Integer maxFailedTests;
    private Collection<TestClass> tests;
    private String classids;
    private String classNames;
    private String suiteids;
    private String suiteNames;
    private Boolean skipCodeCoverage;
    private TestLevel testLevel;

    private TestExecutionRequest(
         Integer maxFailedTests,
         Collection<TestClass> tests,
         String classids,
         String classNames,
         String suiteids,
         String suiteNames,
         Boolean skipCodeCoverage,
         TestLevel testLevel
    ) {
        this.maxFailedTests = maxFailedTests;
        this.tests = tests;
        this.classids = classids;
        this.classNames = classNames;
        this.suiteids = suiteids;
        this.suiteNames = suiteNames;
        this.skipCodeCoverage = skipCodeCoverage;
        this.testLevel = testLevel;
    }

    public String toJson() {
        Gson gson = new Gson();
		return gson.toJson(this);
    }

    public static TestExecutionRequestBuilder builder() {
        return new TestExecutionRequestBuilder();
    }

    public static class TestExecutionRequestBuilder {
        private ArrayList<String> classNames;
        private ArrayList<String> classids;
        private ArrayList<String> suiteNames;
        private ArrayList<String> suiteids;
        private Boolean skipCodeCoverage;
        private Collection<TestClass> tests;
        private Integer maxFailedTests;
        private TestLevel testLevel;

        private TestExecutionRequestBuilder() {}

        public TestExecutionRequestBuilder className(String className) {
            if (tests != null) {
                throw new IllegalStateException("Request may not include both test classes and class names");
            }
            if (classNames == null) { classNames = new ArrayList<String>(); }
            classNames.add(className);
            return this;
        }

        public TestExecutionRequestBuilder classid(String classid) {
            if (tests != null) {
                throw new IllegalStateException("Request may not include both test classes and class ids");
            }
            if (classids == null) { classids = new ArrayList<String>(); }
            classids.add(classid);
            return this;
        }

        public TestExecutionRequestBuilder suiteName(String suiteName) {
            if (tests != null) {
                throw new IllegalStateException("Request may not include both test classes and suite names");
            }
            if (suiteNames == null) { suiteNames = new ArrayList<String>(); }
            suiteNames.add(suiteName);
            return this;
        }

        public TestExecutionRequestBuilder suiteid(String suiteid) {
            if (tests != null) {
                throw new IllegalStateException("Request may not include both test classes and suite ids");
            }
            if (suiteids == null) { suiteids = new ArrayList<String>(); }
            suiteids.add(suiteid);
            return this;
        }

        public TestExecutionRequestBuilder skipCodeCoverage(Boolean skipCodeCoverage) {
            this.skipCodeCoverage = skipCodeCoverage;
            return this;
        }

        public TestExecutionRequestBuilder testClass(TestClass testClass) {
            if (classNames != null || classids != null || suiteNames != null || suiteids != null) {
                throw new IllegalStateException("Request may not include both test classes and (class names, class ids, suite names, or suite ids)");
            }
            if (tests == null) { tests = new ArrayList<TestClass>(); }
            tests.add(testClass);
            return this;
        }

        public TestExecutionRequestBuilder maxFailedTests(Integer maxFailedTests) {
            this.maxFailedTests = maxFailedTests;
            return this;
        }

        public TestExecutionRequestBuilder testLevel(TestLevel testLevel) {
            this.testLevel = testLevel;
            return this;
        }

        public TestExecutionRequest build() {
            return new TestExecutionRequest(
                maxFailedTests,
                tests,
                (classids == null ? null : String.join(",", classids)),
                (classNames == null ? null : String.join(",", classNames)),
                (suiteids == null ? null : String.join(",", suiteids)),
                (suiteNames == null ? null : String.join(",", suiteNames)),
                skipCodeCoverage,
                testLevel
            );
        }

    }
}