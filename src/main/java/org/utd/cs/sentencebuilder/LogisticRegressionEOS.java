/**
 * LogisticRegressionEOS.java
 * CS4485 - Fall 2025 - Sentence Builder Project
 *
 * Author: Vincent Phan
 * Date: November 15, 2025
 *
 * Description: Implements a Logistic Regression classifier
 * specifically tuned for End-Of-Sentence (EOS) detection.
 *
 * This class serves as the decision-maker of the predictor. It takes
 * three distinct features (log-odds derived from statistical probabilities)
 * and learns optimal weights (w1, w2, w3) and a bias (b) to combine them
 * into a final probability.
 */

package org.utd.cs.sentencebuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogisticRegressionEOS {
    private static final Logger logger = LoggerFactory.getLogger(LogisticRegressionEOS.class);

    // Parameters: bias + 3 weights
    private double b = 0.0;
    private double w1 = 0.0;
    private double w2 = 0.0;
    private double w3 = 0.0;

    // Hyperparameters
    private double learningRate = 0.01;
    private int maxEpochs = 10000;
    private double lambdaL2 = 0.0;

    // Early stopping configuration
    private boolean useEarlyStopping = true;
    private int patience = 2000;     // stop if not improved for this many epochs
    private double minDelta = 1e-6;  // minimum required improvement

    public LogisticRegressionEOS() {}

    public LogisticRegressionEOS(double learningRate, int maxEpochs, double lambdaL2) {
        this.learningRate = learningRate;
        this.maxEpochs = maxEpochs;
        this.lambdaL2 = lambdaL2;
    }

    private double sigmoid(double z) {
        if (z < -40) return 0;
        if (z > 40) return 1;
        return 1.0 / (1.0 + Math.exp(-z));
    }

    public double predict(SentenceFeature f) {
        double z = b + w1*f.getX1() + w2*f.getX2() + w3*f.getX3();
        return sigmoid(z);
    }

    public void fit(List<SentenceFeature> data) {
        double bestLoss = Double.MAX_VALUE;
        int epochsSinceImprove = 0;
        // classic binary cross entropy. L = -[ylog(p) + (1-y)log(1[p)]
        for (int epoch = 0; epoch < maxEpochs; epoch++) {

            double db = 0, dw1 = 0, dw2 = 0, dw3 = 0;

            for (SentenceFeature f : data) {
                double y = f.getLabel();
                double p = predict(f); //sigmoid(f)

                // derivative of loss w.r.t. bias.
                double error = p - y;

                // gradient
                db  += error;
                dw1 += error * f.getX1();
                dw2 += error * f.getX2();
                dw3 += error * f.getX3();
            }
            // for datasets, we average
            int n = data.size();
            db  /= n;
            dw1 /= n;
            dw2 /= n;
            dw3 /= n;

            // L2 penalty
            dw1 += lambdaL2 * w1;
            dw2 += lambdaL2 * w2;
            dw3 += lambdaL2 * w3;

            // Update
            b  -= learningRate * db;
            w1 -= learningRate * dw1;
            w2 -= learningRate * dw2;
            w3 -= learningRate * dw3;

            // Compute loss
            double loss = totalLoss(data);

            if (epoch % 500 == 0) {
                //System.out.printf("Epoch %d – loss=%.6f%n", epoch, loss);
                logger.info("Epoch {} - loss= {}", epoch, loss);
            }

            // ---- EARLY STOPPING ----
            if (useEarlyStopping) {
                if (bestLoss - loss > minDelta) {
                    bestLoss = loss;
                    epochsSinceImprove = 0;
                } else {
                    epochsSinceImprove++;
                    if (epochsSinceImprove > patience) {
                        logger.info("Early stopping triggered at epoch {}", epoch);
                        break;
                    }
                }
            }
        }
    }

    public double totalLoss(List<SentenceFeature> data) {
        double loss = 0;

        for (SentenceFeature f : data) {
            double p = predict(f);
            double y = f.getLabel();
            p = Math.max(1e-12, Math.min(1 - 1e-12, p));
            loss += -(y * Math.log(p) + (1 - y) * Math.log(1 - p));
        }

        return loss / data.size();
    }

    public void saveModel(File file) throws IOException {
        logger.info("Saving model at {}", file);
        File parentDir = file.getParentFile();
        // Check if it exists and create it if it doesn't
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directories: " + parentDir.getAbsolutePath());
            }
        }

        JSONObject obj = new JSONObject();
        obj.put("b", b);
        obj.put("w1", w1);
        obj.put("w2", w2);
        obj.put("w3", w3);

        // optional hyperparams
        obj.put("learningRate", learningRate);
        obj.put("lambdaL2", lambdaL2);

        Files.writeString(file.toPath(), obj.toString(2), StandardCharsets.UTF_8);
    }

    public static LogisticRegressionEOS loadModel(File file) throws IOException {
        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JSONObject obj = new JSONObject(json);

        LogisticRegressionEOS model = new LogisticRegressionEOS();
        model.b = obj.getDouble("b");
        model.w1 = obj.getDouble("w1");
        model.w2 = obj.getDouble("w2");
        model.w3 = obj.getDouble("w3");

        if (obj.has("learningRate"))
            model.learningRate = obj.getDouble("learningRate");

        if (obj.has("lambdaL2"))
            model.lambdaL2 = obj.getDouble("lambdaL2");

        return model;
    }

    /**
     * Evaluates the model given x1, x2, x3
     * @param x1 feature 1
     * @param x2 feature 2
     * @param x3 feature 3
     * @return predicted probability of EOS
     */
    public double eval(double x1, double x2, double x3) {
        double z = b + w1 * x1 + w2 * x2 + w3 * x3;
        return sigmoid(z);
    }

    public double getB() { return b; }
    public double getW1() { return w1; }
    public double getW2() { return w2; }
    public double getW3() { return w3; }
}
