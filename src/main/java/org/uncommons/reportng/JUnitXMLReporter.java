package org.uncommons.reportng;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.IClass;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestResult;
import org.testng.xml.XmlSuite;

/**
 * JUnit XML reporter for TestNG that uses Freemarker templates to generate its output.
 */
public class JUnitXMLReporter extends AbstractReporter {

    private static final String RESULTS_KEY = "results";
    private static final String TEMPLATES_PATH = "org/uncommons/reportng/templates/xml/";
    private static final String RESULTS_FILE = "results";
    private static final String REPORT_DIRECTORY = "xml";

    public JUnitXMLReporter() {
        super(TEMPLATES_PATH);
    }

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites,
        String outputDirectoryName) {
        removeEmptyDirectories(new File(outputDirectoryName));
        File outputDirectory = new File(outputDirectoryName, REPORT_DIRECTORY);
        outputDirectory.mkdirs();

        Collection<TestClassResults> flattenedResults = flattenResults(suites);

        for (TestClassResults results : flattenedResults) {
            Map<String, Object> context = createContext();
            context.put(RESULTS_KEY, results);

            try {
                generateFile(
                    new File(outputDirectory,
                        results.getTestClass().getName() + '_' + RESULTS_FILE + ".xml"),
                    RESULTS_FILE + ".ftl",
                    context);
            } catch (Exception e) {
                throw new ReportNGException("Failed generating JUnit XML report.", e);
            }
        }
    }

    /**
     * Flatten a list of test suite results into a collection of results grouped by test class. This
     * method basically strips away the TestNG way of organising tests and arranges the results by
     * test class.
     *
     * @param suites List of test suites {@link ISuite}
     * @return A collection of {@link TestClassResults} grouped by test class.
     */
    private Collection<TestClassResults> flattenResults(List<ISuite> suites) {
        Map<IClass, TestClassResults> flattenedResults = new HashMap<>();
        for (ISuite suite : suites) {
            for (ISuiteResult suiteResult : suite.getResults().values()) {
                // Failed and skipped configuration methods are treated as test failures
                organiseByClass(
                    suiteResult.getTestContext().getFailedConfigurations().getAllResults(),
                    flattenedResults);
                organiseByClass(
                    suiteResult.getTestContext().getSkippedConfigurations().getAllResults(),
                    flattenedResults);

                // Successful configuration methods are not included.

                organiseByClass(suiteResult.getTestContext().getFailedTests().getAllResults(),
                    flattenedResults);
                organiseByClass(suiteResult.getTestContext().getSkippedTests().getAllResults(),
                    flattenedResults);
                organiseByClass(suiteResult.getTestContext().getPassedTests().getAllResults(),
                    flattenedResults);
            }
        }
        return flattenedResults.values();
    }

    private void organiseByClass(Set<ITestResult> testResults,
        Map<IClass, TestClassResults> flattenedResults) {
        for (ITestResult testResult : testResults) {
            getResultsForClass(flattenedResults, testResult).addResult(testResult);
        }
    }

    // Look up the results data for a particular test class.
    private TestClassResults getResultsForClass(Map<IClass, TestClassResults> flattenedResults,
        ITestResult testResult) {
        TestClassResults resultsForClass = flattenedResults.get(testResult.getTestClass());
        if (resultsForClass == null) {
            resultsForClass = new TestClassResults(testResult.getTestClass());
            flattenedResults.put(testResult.getTestClass(), resultsForClass);
        }
        return resultsForClass;
    }

    /**
     * Groups together all of the data about the test results from the methods of a single test
     * class
     */
    public static final class TestClassResults {

        private final IClass testClass;
        private final Collection<ITestResult> failedTests = new LinkedList<>();
        private final Collection<ITestResult> skippedTests = new LinkedList<>();
        private final Collection<ITestResult> passedTests = new LinkedList<>();

        private long duration = 0;

        private TestClassResults(IClass testClass) {
            this.testClass = testClass;
        }

        public IClass getTestClass() {
            return testClass;
        }

        /**
         * Adds a test reult for this class. Organises results by outcome.
         *
         * @param result A {@link ITestResult} object
         */
        void addResult(ITestResult result) {
            switch (result.getStatus()) {
                case ITestResult.SKIP:
                    if (META.allowSkippedTestsInXML()) {
                        skippedTests.add(result);
                        break;
                    }
                    // Intentional fall-through (skipped tests marked as failed if XML doesn't support skips).
                case ITestResult.FAILURE:
                case ITestResult.SUCCESS_PERCENTAGE_FAILURE:
                    failedTests.add(result);
                    break;
                case ITestResult.SUCCESS:
                    passedTests.add(result);
                    break;
                default:
                    throw new IllegalStateException("Unknown result status:" + result.getStatus());
            }
            duration += (result.getEndMillis() - result.getStartMillis());
        }

        public Collection<ITestResult> getFailedTests() {
            return failedTests;
        }

        public Collection<ITestResult> getSkippedTests() {
            return skippedTests;
        }

        public Collection<ITestResult> getPassedTests() {
            return passedTests;
        }

        public long getDuration() {
            return duration;
        }
    }
}
