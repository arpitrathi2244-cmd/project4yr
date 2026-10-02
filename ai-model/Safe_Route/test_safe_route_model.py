import os
import joblib
import pandas as pd
import numpy as np

# =========================================================
# LOAD MODEL
# =========================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "safe_route_model.joblib")

if not os.path.exists(MODEL_PATH):
    print("ERROR: safe_route_model.joblib nahi mili!")
    exit()

package = joblib.load(MODEL_PATH)

model = package["pipeline"]
features = package["features"]

print("AI Safe Route Model Loaded Successfully")


# =========================================================
# ROUTE DATA
# =========================================================

# 3 possible routes
# Har route me multiple road segments hain.

routes = {

    "Route A": [
        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.61,
            "longitude": 77.21,
            "hour": 18,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 4,
            "traffic_signal": 1,
            "weather": "clear",
            "visibility": "high",
            "temperature": 30,
            "traffic_density": "high",
            "is_peak_hour": 1,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.62,
            "longitude": 77.22,
            "hour": 18,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 3,
            "traffic_signal": 1,
            "weather": "clear",
            "visibility": "high",
            "temperature": 31,
            "traffic_density": "high",
            "is_peak_hour": 1,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.63,
            "longitude": 77.23,
            "hour": 19,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "highway",
            "lanes": 4,
            "traffic_signal": 0,
            "weather": "clear",
            "visibility": "high",
            "temperature": 30,
            "traffic_density": "medium",
            "is_peak_hour": 1,
            "festival": "None"
        }
    ],

    "Route B": [
        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.60,
            "longitude": 77.20,
            "hour": 16,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 4,
            "traffic_signal": 1,
            "weather": "clear",
            "visibility": "high",
            "temperature": 29,
            "traffic_density": "low",
            "is_peak_hour": 0,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.61,
            "longitude": 77.19,
            "hour": 17,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 4,
            "traffic_signal": 1,
            "weather": "clear",
            "visibility": "high",
            "temperature": 30,
            "traffic_density": "medium",
            "is_peak_hour": 1,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.62,
            "longitude": 77.18,
            "hour": 17,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "highway",
            "lanes": 4,
            "traffic_signal": 0,
            "weather": "clear",
            "visibility": "high",
            "temperature": 29,
            "traffic_density": "low",
            "is_peak_hour": 0,
            "festival": "None"
        }
    ],

    "Route C": [
        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.64,
            "longitude": 77.24,
            "hour": 21,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 2,
            "traffic_signal": 0,
            "weather": "fog",
            "visibility": "low",
            "temperature": 20,
            "traffic_density": "high",
            "is_peak_hour": 0,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.65,
            "longitude": 77.25,
            "hour": 22,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 2,
            "traffic_signal": 0,
            "weather": "fog",
            "visibility": "low",
            "temperature": 19,
            "traffic_density": "high",
            "is_peak_hour": 0,
            "festival": "None"
        },

        {
            "city": "Delhi",
            "state": "Delhi",
            "latitude": 28.66,
            "longitude": 77.26,
            "hour": 20,
            "day_of_week": "Monday",
            "is_weekend": 0,
            "road_type": "urban",
            "lanes": 3,
            "traffic_signal": 0,
            "weather": "fog",
            "visibility": "low",
            "temperature": 20,
            "traffic_density": "high",
            "is_peak_hour": 0,
            "festival": "None"
        }
    ]
}


# =========================================================
# PREDICT ROUTE RISK
# =========================================================

route_results = {}

for route_name, segments in routes.items():

    segment_df = pd.DataFrame(segments)

    # Model ke required features hi use karna
    segment_df = segment_df[features]

    predictions = model.predict(segment_df)

    # Route ka average risk
    average_risk = float(np.mean(predictions))

    # Highest-risk segment
    maximum_risk = float(np.max(predictions))

    route_results[route_name] = {
        "average_risk": average_risk,
        "maximum_risk": maximum_risk,
        "segment_risks": predictions
    }


# =========================================================
# DISPLAY RESULTS
# =========================================================

print("\n")
print("=" * 60)
print("              AI SAFE ROUTE RECOMMENDATION")
print("=" * 60)

print("\nPossible Routes:\n")

for route_name, result in route_results.items():

    print("-" * 60)

    print(f"{route_name}")

    print(
        f"Segment Risks    : "
        + ", ".join(
            f"{risk * 100:.2f}%"
            for risk in result["segment_risks"]
        )
    )

    print(
        f"Average Risk     : "
        f"{result['average_risk'] * 100:.2f}%"
    )

    print(
        f"Highest Segment  : "
        f"{result['maximum_risk'] * 100:.2f}%"
    )


# =========================================================
# FIND SAFEST ROUTE
# =========================================================

safest_route = min(
    route_results,
    key=lambda route: route_results[route]["average_risk"]
)

safest_risk = route_results[safest_route]["average_risk"]


# =========================================================
# FINAL RESULT
# =========================================================

print("\n")
print("=" * 60)
print("                 FINAL RECOMMENDATION")
print("=" * 60)

print(f"\nSafest Route     : {safest_route}")
print(f"AI Risk Score    : {safest_risk:.2f}")
print(f"Risk Percentage  : {safest_risk * 100:.2f}%")

if safest_risk < 0.30:
    risk_level = "LOW"
elif safest_risk < 0.60:
    risk_level = "MEDIUM"
else:
    risk_level = "HIGH"

print(f"Risk Level       : {risk_level}")

print("\nRecommendation:")
print(
    f"AI recommends {safest_route} because it has "
    f"the lowest predicted accident risk among the available routes."
)

print("\n" + "=" * 60)