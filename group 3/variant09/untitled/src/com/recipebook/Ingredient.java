package com.recipebook;

public class Ingredient {
    private final String name;
    private final double amountGrams;
    private final double caloriesPer100g;


    public Ingredient(String name, double amountGrams, double caloriesPer100g) {
        this.name = name;
        this.amountGrams = amountGrams;
        this.caloriesPer100g = caloriesPer100g;
    }


    public double calculateTotalCalories() {
        return (amountGrams / 100.0) * caloriesPer100g;
    }

    public String getName() {
        return name;
    }

    public double getAmountGrams() {
        return amountGrams;
    }

    @Override
    public String toString() {
        return name + " (" + amountGrams + " г, " + calculateTotalCalories() + " ккал)";
    }
}