package com.recipebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Recipe {
    private final String title;
    private final int cookingTimeMinutes;
    private final RecipeCategory category;
    private final List<Ingredient> ingredients = new ArrayList<>();

    public Recipe(String title, int cookingTimeMinutes, RecipeCategory category) {
        this.title = title;
        this.cookingTimeMinutes = cookingTimeMinutes;
        this.category = category;
    }

    public void addIngredient(String name, double amountGrams, double caloriesPer100g) {
        System.out.println("[STUB - Recipe.addIngredient] Створення інгредієнта: " + name);
        Ingredient ingredient = new Ingredient(name, amountGrams, caloriesPer100g);
        this.ingredients.add(ingredient);
    }

    public double calculateTotalCalories() {
        double total = 0.0;
        for (Ingredient ing : ingredients) {
            total += ing.calculateTotalCalories();
        }
        return total;
    }

    public String getTitle() {
        return title;
    }

    public int getCookingTimeMinutes() {
        return cookingTimeMinutes;
    }

    public RecipeCategory getCategory() {
        return category;
    }

    public List<Ingredient> getIngredients() {
        return Collections.unmodifiableList(ingredients);
    }
}