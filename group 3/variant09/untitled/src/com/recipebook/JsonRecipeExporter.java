package com.recipebook;
public class JsonRecipeExporter implements RecipeExporter {
    @Override
    public String export(Recipe recipe) {
        System.out.println("[STUB - JsonRecipeExporter.export] Форматування рецепта в JSON: " + recipe.getTitle());
        return "{\n" +
                "  \"title\": \"" + recipe.getTitle() + "\",\n" +
                "  \"category\": \"" + recipe.getCategory().getCategoryName() + "\",\n" +
                "  \"cookingTime\": " + recipe.getCookingTimeMinutes() + ",\n" +
                "  \"totalCalories\": " + recipe.calculateTotalCalories() + "\n" +
                "}";
    }
}