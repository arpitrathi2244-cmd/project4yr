import os
import joblib
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    classification_report,
    confusion_matrix
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder


# ============================================================
# 1. FILE PATHS
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

DATASET_PATH = os.path.join(
    BASE_DIR,
    "dataset",
    "accident_ai_cleaned.csv"
)

MODEL_PATH = os.path.join(
    BASE_DIR,
    "accident_risk_model.joblib"
)


# ============================================================
# 2. CHECK DATASET
# ============================================================

if not os.path.exists(DATASET_PATH):
    raise FileNotFoundError(
        f"\nDataset nahi mili!\n"
        f"Expected location:\n{DATASET_PATH}\n"
        f"\nCheck karo ki accident_ai_cleaned.csv "
        f"'dataset' folder ke andar hai."
    )


# ============================================================
# 3. LOAD DATASET
# ============================================================

print("\n" + "=" * 65)
print("        AI ACCIDENT RISK PREDICTION MODEL")
print("=" * 65)

print("\nLoading dataset...")

df = pd.read_csv(DATASET_PATH)

print(f"Dataset loaded successfully.")
print(f"Total rows    : {len(df)}")
print(f"Total columns : {len(df.columns)}")


# ============================================================
# 4. CLEAN COLUMN NAMES
# ============================================================

df.columns = df.columns.astype(str).str.strip()


# ============================================================
# 5. REMOVE COMPLETELY EMPTY ROWS / COLUMNS
# ============================================================

df = df.dropna(axis=0, how="all")
df = df.dropna(axis=1, how="all")


# ============================================================
# 6. REMOVE DUPLICATE ROWS
# ============================================================

before_duplicates = len(df)

df = df.drop_duplicates()

removed_duplicates = before_duplicates - len(df)

print(f"Duplicate rows removed: {removed_duplicates}")


# ============================================================
# 7. TARGET COLUMN
# ============================================================

TARGET_COLUMN = "Accident_severity"

if TARGET_COLUMN not in df.columns:
    raise ValueError(
        f"'{TARGET_COLUMN}' column dataset me nahi mili."
    )


# ============================================================
# 8. MAP ACCIDENT SEVERITY TO RISK LEVEL
# ============================================================

severity_mapping = {
    "Slight Injury": "Low",
    "Serious Injury": "Medium",
    "Fatal injury": "High"
}

df[TARGET_COLUMN] = (
    df[TARGET_COLUMN]
    .astype(str)
    .str.strip()
)

df["Risk_Level"] = df[TARGET_COLUMN].map(
    severity_mapping
)


# Remove unknown target rows
df = df.dropna(
    subset=["Risk_Level"]
).copy()


# ============================================================
# 9. FEATURES
# ============================================================
# These are the features used by the AI model.
# Post-accident casualty information is intentionally avoided.

FEATURES = [
    "Time",
    "Day_of_week",
    "Age_band_of_driver",
    "Sex_of_driver",
    "Driving_experience",
    "Type_of_vehicle",
    "Service_year_of_vehicle",
    "Defect_of_vehicle",
    "Area_accident_occured",
    "Lanes_or_Medians",
    "Road_allignment",
    "Types_of_Junction",
    "Road_surface_type",
    "Road_surface_conditions",
    "Light_conditions",
    "Weather_conditions",
    "Type_of_collision",
    "Number_of_vehicles_involved",
    "Vehicle_movement",
    "Cause_of_accident"
]


# ============================================================
# 10. CHECK REQUIRED FEATURES
# ============================================================

missing_features = [
    column
    for column in FEATURES
    if column not in df.columns
]

if missing_features:
    print("\nMissing columns:")

    for column in missing_features:
        print(" -", column)

    raise ValueError(
        "\nRequired feature columns dataset me missing hain."
    )


# ============================================================
# 11. CREATE X AND Y
# ============================================================

X = df[FEATURES].copy()
y = df["Risk_Level"].copy()


# ============================================================
# 12. HANDLE EMPTY STRINGS
# ============================================================

X = X.replace(
    r"^\s*$",
    pd.NA,
    regex=True
)


# ============================================================
# 13. NUMERIC COLUMN
# ============================================================

NUMERIC_FEATURES = [
    "Number_of_vehicles_involved"
]

X["Number_of_vehicles_involved"] = pd.to_numeric(
    X["Number_of_vehicles_involved"],
    errors="coerce"
)


# ============================================================
# 14. CATEGORICAL COLUMNS
# ============================================================

CATEGORICAL_FEATURES = [
    column
    for column in FEATURES
    if column not in NUMERIC_FEATURES
]


# ============================================================
# 15. PREPROCESSING
# ============================================================

numeric_pipeline = Pipeline(
    steps=[
        (
            "imputer",
            SimpleImputer(
                strategy="median"
            )
        )
    ]
)


categorical_pipeline = Pipeline(
    steps=[
        (
            "imputer",
            SimpleImputer(
                strategy="most_frequent"
            )
        ),
        (
            "onehot",
            OneHotEncoder(
                handle_unknown="ignore"
            )
        )
    ]
)


preprocessor = ColumnTransformer(
    transformers=[
        (
            "numeric",
            numeric_pipeline,
            NUMERIC_FEATURES
        ),
        (
            "categorical",
            categorical_pipeline,
            CATEGORICAL_FEATURES
        )
    ]
)


# ============================================================
# 16. SHOW CLASS DISTRIBUTION
# ============================================================

print("\n" + "-" * 65)
print("Risk Level Distribution")
print("-" * 65)

print(
    y.value_counts()
)


# ============================================================
# 17. TRAIN / TEST SPLIT
# ============================================================

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    random_state=42,
    stratify=y
)

print("\nTraining rows :", len(X_train))
print("Testing rows  :", len(X_test))


# ============================================================
# 18. RANDOM FOREST MODEL
# ============================================================
# Class weights are used because High-risk accident records
# are much fewer than Low-risk records.

random_forest = RandomForestClassifier(
    n_estimators=600,
    class_weight={
        "Low": 1,
        "Medium": 3,
        "High": 12
    },
    random_state=42,
    n_jobs=-1,
    min_samples_leaf=2
)


# ============================================================
# 19. COMPLETE ML PIPELINE
# ============================================================

model = Pipeline(
    steps=[
        (
            "preprocessor",
            preprocessor
        ),
        (
            "classifier",
            random_forest
        )
    ]
)


# ============================================================
# 20. TRAIN MODEL
# ============================================================

print("\n" + "=" * 65)
print("Training model...")
print("=" * 65)

model.fit(
    X_train,
    y_train
)

print("\nModel training completed successfully!")


# ============================================================
# 21. PREDICTIONS
# ============================================================

y_pred = model.predict(
    X_test
)


# ============================================================
# 22. MODEL EVALUATION
# ============================================================

accuracy = accuracy_score(
    y_test,
    y_pred
)

balanced_accuracy = balanced_accuracy_score(
    y_test,
    y_pred
)


print("\n" + "=" * 65)
print("MODEL EVALUATION")
print("=" * 65)

print(
    f"\nAccuracy           : {accuracy * 100:.2f}%"
)

print(
    f"Balanced Accuracy  : {balanced_accuracy * 100:.2f}%"
)


# ============================================================
# 23. CLASSIFICATION REPORT
# ============================================================

print("\nClassification Report:")
print("-" * 65)

print(
    classification_report(
        y_test,
        y_pred,
        labels=[
            "Low",
            "Medium",
            "High"
        ],
        zero_division=0
    )
)


# ============================================================
# 24. CONFUSION MATRIX
# ============================================================

print("Confusion Matrix:")
print("-" * 65)

matrix = confusion_matrix(
    y_test,
    y_pred,
    labels=[
        "Low",
        "Medium",
        "High"
    ]
)

print(matrix)


# ============================================================
# 25. SAVE TRAINED MODEL
# ============================================================

model_package = {
    "pipeline": model,
    "features": FEATURES,
    "target_mapping": severity_mapping,
    "classes": [
        "Low",
        "Medium",
        "High"
    ],
    "model_name": "Random Forest Accident Risk Classifier",
    "random_state": 42
}


joblib.dump(
    model_package,
    MODEL_PATH
)


# ============================================================
# 26. SUCCESS MESSAGE
# ============================================================

print("\n" + "=" * 65)
print("MODEL SAVED SUCCESSFULLY!")
print("=" * 65)

print(
    f"\nModel file:\n{MODEL_PATH}"
)

print(
    "\nAb accident_risk_model.joblib generate ho gaya hai."
)

print(
    "\nNext step: python test_model.py"
)

print("\n" + "=" * 65)