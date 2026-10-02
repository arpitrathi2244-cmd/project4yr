from flask import Flask, request, jsonify
import joblib
import pandas as pd
import os

app = Flask(__name__)

# ============================================================
# Load trained model
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "accident_risk_model.joblib")

if not os.path.exists(MODEL_PATH):
    raise FileNotFoundError(
        f"Model file not found: {MODEL_PATH}"
    )

package = joblib.load(MODEL_PATH)

model = package["pipeline"]
features = package["features"]

print("✅ Accident Risk Model Loaded Successfully")


# ============================================================
# Home / Health Check
# ============================================================

@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "message": "Accident Risk Prediction API is running",
        "status": "success"
    })


# ============================================================
# Prediction API
# ============================================================

@app.route("/predict", methods=["POST"])
def predict():

    try:
        data = request.get_json()

        if not data:
            return jsonify({
                "error": "No JSON data received"
            }), 400

        # Check required features
        missing_features = [
            feature
            for feature in features
            if feature not in data
        ]

        if missing_features:
            return jsonify({
                "error": "Missing required features",
                "missing_features": missing_features
            }), 400

        # Create DataFrame
        input_data = pd.DataFrame(
            [data],
            columns=features
        )

        # Prediction
        prediction = model.predict(input_data)[0]

        # Probabilities
        probabilities = model.predict_proba(input_data)[0]
        classes = model.named_steps["classifier"].classes_

        probability_result = {}

        for cls, prob in zip(classes, probabilities):
            probability_result[cls] = round(
                float(prob) * 100,
                2
            )

        # Response
        return jsonify({
            "status": "success",
            "predicted_risk": prediction,
            "probabilities": probability_result
        })

    except Exception as e:

        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500


# ============================================================
# Start server
# ============================================================

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5000,
        debug=True
    )