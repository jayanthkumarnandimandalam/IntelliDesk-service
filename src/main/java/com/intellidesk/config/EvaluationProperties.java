package com.intellidesk.config;

/**
 * Configuration properties for the evaluation runner.
 */
public class EvaluationProperties {

    private String datasetPath = "./data/evaluation/dataset.json";
    private String reportOutputPath = "./data/evaluation/report.json";
    private double threshold = 0.7;
    private int timeoutSeconds = 120;

    public String getDatasetPath() {
        return datasetPath;
    }

    public void setDatasetPath(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    public String getReportOutputPath() {
        return reportOutputPath;
    }

    public void setReportOutputPath(String reportOutputPath) {
        this.reportOutputPath = reportOutputPath;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
