package com.saferoad.controller.dto;

public class AiPredictionResponse {

    private String status;
    private String predicted_risk;
    private Object probabilities;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPredicted_risk() {
        return predicted_risk;
    }

    public void setPredicted_risk(String predicted_risk) {
        this.predicted_risk = predicted_risk;
    }

    public Object getProbabilities() {
        return probabilities;
    }

    public void setProbabilities(Object probabilities) {
        this.probabilities = probabilities;
    }
}