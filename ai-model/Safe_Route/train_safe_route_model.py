import pandas as pd
import joblib
import numpy as np

from sklearn.model_selection import train_test_split
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score


# ============================================================
# 1. Load Dataset
# ============================================================

DATA_PATH = "../Dataset/indian_roads_dataset.csv"

df = pd.read_csv(DATA_PATH)

print("Dataset loaded successfully!")
print("Rows:", len(df))
print("Columns:", len(df.columns))


# ============================================================
# 2. Features and Target
# ============================================================

features = [
    "city",
    "state",
    "latitude",
    "longitude",
    "hour",
    "day_of_week",
    "is_weekend",
    "road_type",
    "lanes",
    "traffic_signal",
    "weather",
    "visibility",
    "temperature",
    "traffic_density",
    "is_peak_hour",
    "festival"
]

target = "risk_score"


# ============================================================
# 3. Prepare Data
# ============================================================

X = df[features].copy()

y = pd.to_numeric(
    df[target],
    errors="coerce"
)

# Remove rows where risk_score is missing
valid_rows = y.notna()

X = X.loc[valid_rows]
y = y.loc[valid_rows]


# ============================================================
# 4. Feature Types
# ============================================================

categorical_features = [
    "city",
    "state",
    "day_of_week",
    "road_type",
    "traffic_signal",
    "weather",
    "visibility",
    "traffic_density",
    "festival"
]

numeric_features = [
    "latitude",
    "longitude",
    "hour",
    "is_weekend",
    "lanes",
    "temperature",
    "is_peak_hour"
]


# ============================================================
# 5. Preprocessing
# ============================================================

preprocessor = ColumnTransformer(
    transformers=[
        (
            "categorical",
            OneHotEncoder(handle_unknown="ignore"),
            categorical_features
        ),
        (
            "numeric",
            "passthrough",
            numeric_features
        )
    ]
)


# ============================================================
# 6. Random Forest Model
# ============================================================

model = RandomForestRegressor(
    n_estimators=300,
    random_state=42,
    n_jobs=-1,
    min_samples_leaf=2
)


# ============================================================
# 7. Complete Pipeline
# ============================================================

pipeline = Pipeline(
    steps=[
        ("preprocessor", preprocessor),
        ("regressor", model)
    ]
)


# ============================================================
# 8. Train/Test Split
# ============================================================

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    random_state=42
)

print("\nTraining model...")
print("Training records:", len(X_train))
print("Testing records:", len(X_test))


# ============================================================
# 9. Train Model
# ============================================================

pipeline.fit(
    X_train,
    y_train
)

print("Model training completed!")


# ============================================================
# 10. Prediction
# ============================================================

predictions = pipeline.predict(X_test)

# Keep risk between 0 and 1
predictions = np.clip(
    predictions,
    0,
    1
)


# ============================================================
# 11. Model Evaluation
# ============================================================

mae = mean_absolute_error(
    y_test,
    predictions
)

rmse = np.sqrt(
    mean_squared_error(
        y_test,
        predictions
    )
)

r2 = r2_score(
    y_test,
    predictions
)


print("\n" + "=" * 50)
print("       SAFE ROUTE AI MODEL RESULTS")
print("=" * 50)

print(f"\nMAE  : {mae:.4f}")
print(f"RMSE : {rmse:.4f}")
print(f"R2   : {r2:.4f}")


# ============================================================
# 12. Save Model
# ============================================================

MODEL_PACKAGE = {
    "pipeline": pipeline,
    "features": features,
    "target": target,
    "model_type": "RandomForestRegressor",
    "risk_range": [0, 1]
}

MODEL_PATH = "safe_route_model.joblib"

joblib.dump(
    MODEL_PACKAGE,
    MODEL_PATH
)

print("\nModel saved successfully!")
print(f"File: {MODEL_PATH}")

print("\n" + "=" * 50)
print("       TRAINING COMPLETED")
print("=" * 50)