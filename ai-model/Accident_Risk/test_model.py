import os
import joblib
import pandas as pd

# Model file location
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "accident_risk_model.joblib")

# Check model exists
if not os.path.exists(MODEL_PATH):
    print("ERROR: accident_risk_model.joblib nahi mili!")
    exit()

# Load trained model
package = joblib.load(MODEL_PATH)

model = package["pipeline"]
features = package["features"]

# Sample accident situation
sample = {
    "Time": "22.30.00",
    "Day_of_week": "Friday",
    "Age_band_of_driver": "18-30",
    "Sex_of_driver": "Male",
    "Driving_experience": "Below 1yr",
    "Type_of_vehicle": "Automobile",
    "Service_year_of_vehicle": "5-10yrs",
    "Defect_of_vehicle": "No defect",
    "Area_accident_occured": "Residential areas",
    "Lanes_or_Medians": "Undivided Two way",
    "Road_allignment": "Tangent road with flat terrain",
    "Types_of_Junction": "Y Shape",
    "Road_surface_type": "Asphalt roads",
    "Road_surface_conditions": "Wet or damp",
    "Light_conditions": "Darkness - lights lit",
    "Weather_conditions": "Raining",
    "Type_of_collision": "Vehicle with vehicle collision",
    "Number_of_vehicles_involved": 2,
    "Vehicle_movement": "Going straight",
    "Cause_of_accident": "Overspeed"
}

# Create input DataFrame
input_data = pd.DataFrame([sample], columns=features)

# Predict
prediction = model.predict(input_data)[0]

print("\n" + "=" * 50)
print("      ACCIDENT RISK PREDICTION")
print("=" * 50)

print(f"\nPredicted Risk Level: {prediction}")

# Probability
probabilities = model.predict_proba(input_data)[0]
classes = model.named_steps["classifier"].classes_

print("\nRisk Probabilities:")

for cls, prob in zip(classes, probabilities):
    print(f"{cls}: {prob * 100:.2f}%")

print("\n" + "=" * 50)